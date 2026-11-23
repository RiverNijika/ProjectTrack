package com.projecttrack.app.data.backup

import com.projecttrack.app.data.DataMutationGuard
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.NoteDao
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 非破坏性备份恢复。
 *
 * 流程：
 * 1. 在应用私有临时目录中完整解压并校验 ZIP 结构、版本、JSON 字段、实体关系和附件大小/SHA-256；
 * 2. 校验全部通过后，才在数据变更锁内为附件分配新的唯一文件名并复制到正式附件目录；
 * 3. 在最终数据库提交前检查取消；提交本身是短暂 NonCancellable 临界区，提交完整返回后按成功处理，
 *    不删除已入库附件；
 * 4. 提交前失败或取消会清理临时文件与本次已复制的附件；数据库事务失败由 Room 回滚，
 *    不删除、不覆盖、不修改任何已有项目或附件。
 */
class BackupImporter(
    private val projectDao: ProjectDao,
    private val nodeDao: NodeDao,
    private val noteDao: NoteDao,
    private val fileDao: FileDao,
    private val storage: FileStorage,
    private val transactions: TransactionRunner,
    private val mutationGuard: DataMutationGuard,
    private val cacheRoot: File,
    private val databaseVersion: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun restore(
        input: InputStream,
        onProgress: suspend (RestoreProgress) -> Unit = {},
    ): BackupRestoreResult {
        val temporaryDirectory = File(cacheRoot, "projecttrack-restore-${UUID.randomUUID()}")
        return try {
            onProgress(RestoreProgress(stage = RestoreStage.VALIDATING))
            val extraction = withContext(Dispatchers.IO) {
                BackupZipReader.extract(input, temporaryDirectory)
            }
            val manifest = BackupJson.decodeManifest(
                text = extraction.manifestJson,
                expectedDatabaseVersion = databaseVersion,
            )
            val data = BackupJson.decodeData(extraction.dataJson)
            BackupValidator.validate(manifest, data, extraction)

            mutationGuard.withMutation {
                applyRestore(data, extraction, onProgress)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (validation: BackupValidationException) {
            BackupRestoreResult.Failure(validation.message ?: "备份校验失败")
        } catch (error: IOException) {
            BackupRestoreResult.Failure("读取备份失败：${error.message ?: "文件读取错误"}")
        } catch (error: SecurityException) {
            BackupRestoreResult.Failure("没有读取所选备份文件的权限")
        } catch (error: Exception) {
            BackupRestoreResult.Failure("恢复失败：${error.message ?: "未知错误"}")
        } finally {
            withContext(NonCancellable) {
                temporaryDirectory.deleteRecursively()
            }
        }
    }

    private suspend fun applyRestore(
        data: BackupData,
        extraction: BackupExtraction,
        onProgress: suspend (RestoreProgress) -> Unit,
    ): BackupRestoreResult {
        val copiedPaths = mutableListOf<String>()
        val targetsByFileId = mutableMapOf<Long, com.projecttrack.app.data.file.StoredTarget>()
        val totalBytes = data.files.sumOf { it.sizeBytes }
        try {
            var processedBytes = 0L
            data.files.forEachIndexed { index, file ->
                currentCoroutineContext().ensureActive()
                onProgress(
                    RestoreProgress(
                        stage = RestoreStage.COPYING_FILES,
                        processedFiles = index,
                        totalFiles = data.files.size,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                    ),
                )
                val attachment = extraction.attachments[file.archivePath]
                    ?: throw BackupValidationException("恢复时缺少附件：${file.displayName}")
                val target = allocateUniqueTarget(file.displayName)
                storage.copyFromStream(target.relativePath) {
                    FileInputStream(attachment.file)
                }
                copiedPaths += target.relativePath
                targetsByFileId[file.id] = target
                processedBytes += file.sizeBytes
            }

            currentCoroutineContext().ensureActive()
            onProgress(
                RestoreProgress(
                    stage = RestoreStage.WRITING_DATABASE,
                    processedFiles = data.files.size,
                    totalFiles = data.files.size,
                    processedBytes = totalBytes,
                    totalBytes = totalBytes,
                ),
            )
            currentCoroutineContext().ensureActive()
            // 数据库提交是短暂临界区。只对这一段使用 NonCancellable，保证外部取消不会在
            // “事务已提交、调用方尚未收到返回”窗口触发 catch 并误删已入库附件。
            // ZIP 解压、SHA-256 计算和附件复制仍在可取消区间内。
            try {
                withContext(NonCancellable) {
                    transactions.run {
                        insertRestoredData(data, targetsByFileId)
                    }
                }
            } catch (cancellation: CancellationException) {
                // 外部取消不应进入这里（提交临界区为 NonCancellable）；
                // 若事务自身抛取消，说明提交未正常返回，按未提交处理并清理本次附件。
                withContext(NonCancellable) { cleanupCopiedFiles(copiedPaths) }
                throw cancellation
            } catch (error: Exception) {
                val failedCleanup = withContext(NonCancellable) { cleanupCopiedFiles(copiedPaths) }
                return BackupRestoreResult.Failure(
                    message = "恢复失败，未修改现有数据：${error.message ?: "未知错误"}",
                    cleanupWarning = if (failedCleanup > 0) {
                        "有 $failedCleanup 个已复制的附件未能自动清理，会在应用启动维护时按孤立文件处理"
                    } else {
                        null
                    },
                )
            }
            // 提交已完整返回：直接形成成功结果，不再执行任何挂起调用或取消检查。
            return BackupRestoreResult.Success(
                projectCount = data.projects.size,
                nodeCount = data.nodes.size,
                noteCount = data.notes.size,
                fileCount = data.files.size,
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { cleanupCopiedFiles(copiedPaths) }
            throw cancellation
        } catch (error: Exception) {
            val failedCleanup = withContext(NonCancellable) { cleanupCopiedFiles(copiedPaths) }
            return BackupRestoreResult.Failure(
                message = "恢复失败，未修改现有数据：${error.message ?: "未知错误"}",
                cleanupWarning = if (failedCleanup > 0) {
                    "有 $failedCleanup 个已复制的附件未能自动清理，会在应用启动维护时按孤立文件处理"
                } else {
                    null
                },
            )
        }
    }

    private suspend fun insertRestoredData(
        data: BackupData,
        targetsByFileId: Map<Long, com.projecttrack.app.data.file.StoredTarget>,
    ) {
        val projectIdMap = mutableMapOf<Long, Long>()
        data.projects.forEach { project ->
            val newId = projectDao.insert(
                ProjectEntity(
                    name = project.name,
                    description = project.description,
                    createdAt = project.createdAt,
                    updatedAt = project.updatedAt,
                ),
            )
            projectIdMap[project.id] = newId
        }

        val nodeIdMap = mutableMapOf<Long, Long>()
        data.nodes.forEach { node ->
            val newProjectId = projectIdMap[node.projectId]
                ?: throw BackupValidationException("节点引用的项目不存在")
            val newId = nodeDao.insert(
                NodeEntity(
                    projectId = newProjectId,
                    title = node.title,
                    description = node.description,
                    occurredAt = node.occurredAt,
                    createdAt = node.createdAt,
                ),
            )
            nodeIdMap[node.id] = newId
        }

        data.notes.forEach { note ->
            val newProjectId = projectIdMap[note.projectId]
                ?: throw BackupValidationException("记录引用的项目不存在")
            val newNodeId = note.nodeId?.let { oldNodeId ->
                nodeIdMap[oldNodeId]
                    ?: throw BackupValidationException("记录引用的节点不存在")
            }
            noteDao.insert(
                NoteEntity(
                    projectId = newProjectId,
                    nodeId = newNodeId,
                    content = note.content,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                ),
            )
        }

        data.files.forEach { file ->
            val newProjectId = projectIdMap[file.projectId]
                ?: throw BackupValidationException("文件引用的项目不存在")
            val newNodeId = file.nodeId?.let { oldNodeId ->
                nodeIdMap[oldNodeId]
                    ?: throw BackupValidationException("文件引用的节点不存在")
            }
            val target = targetsByFileId[file.id]
                ?: throw BackupValidationException("文件缺少已复制的附件")
            fileDao.insert(
                FileEntity(
                    projectId = newProjectId,
                    nodeId = newNodeId,
                    displayName = file.displayName,
                    mimeType = file.mimeType,
                    extension = file.extension,
                    sizeBytes = file.sizeBytes,
                    category = file.category,
                    relativePath = target.relativePath,
                    importedAt = file.importedAt,
                ),
            )
        }
    }

    private fun allocateUniqueTarget(displayName: String): com.projecttrack.app.data.file.StoredTarget {
        repeat(5) {
            val target = storage.createStoredTarget(displayName, now = clock())
            if (!storage.exists(target.relativePath)) return target
        }
        throw IOException("无法为恢复文件分配唯一的内部文件名")
    }

    private suspend fun cleanupCopiedFiles(paths: List<String>): Int {
        var failed = 0
        paths.forEach { path ->
            if (!storage.delete(path)) failed++
        }
        return failed
    }
}
