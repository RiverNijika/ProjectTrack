package com.projecttrack.app.data.backup

import com.projecttrack.app.data.DataMutationGuard
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.NoteDao
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.ProjectDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导出完整本地备份。
 *
 * 一致性策略：整个导出过程持有 [DataMutationGuard]，期间项目、节点、记录、文件导入/删除与恢复
 * 都会被阻塞，因此写入备份的元数据与附件来自同一稳定状态。导出先写 manifest.json，
 * 再逐个流式写入附件并计算 SHA-256，最后写包含校验值的 data.json。
 */
class BackupExporter(
    private val projectDao: ProjectDao,
    private val nodeDao: NodeDao,
    private val noteDao: NoteDao,
    private val fileDao: FileDao,
    private val storage: FileStorage,
    private val mutationGuard: DataMutationGuard,
    private val appVersionName: String,
    private val appVersionCode: Long,
    private val databaseVersion: Int,
    private val limits: BackupExportLimits = BackupExportLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun export(
        output: OutputStream,
        onProgress: suspend (BackupProgress) -> Unit = {},
    ): BackupExportResult {
        return try {
            mutationGuard.withMutation {
                try {
                    exportLocked(output, onProgress)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: ExportException) {
                    BackupExportResult.Failure(error.message ?: "导出失败")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            BackupExportResult.Failure("导出失败：${error.message ?: "写入备份时出错"}")
        }
    }

    private suspend fun exportLocked(
        output: OutputStream,
        onProgress: suspend (BackupProgress) -> Unit,
    ): BackupExportResult {
        val projects = projectDao.getAll().sortedBy { it.id }
        val nodes = nodeDao.getAll().sortedBy { it.id }
        val notes = noteDao.getAll().sortedBy { it.id }
        val files = fileDao.getAll().sortedBy { it.id }

        var totalFileBytes = 0L
        files.forEach { file ->
            val source = storage.storedFile(file.relativePath)
            if (!source.isFile) {
                throw ExportException("文件副本缺失：${file.displayName}。请先在文件列表中处理缺失文件后再导出。")
            }
            if (source.length() != file.sizeBytes) {
                throw ExportException("文件大小与数据库记录不一致：${file.displayName}。请先处理该文件后再导出。")
            }
            if (file.sizeBytes > BackupFormat.MAX_ATTACHMENT_BYTES) {
                throw ExportException("文件超过备份大小上限：${file.displayName}")
            }
            totalFileBytes = Math.addExact(totalFileBytes, file.sizeBytes)
        }
        if (totalFileBytes > BackupFormat.MAX_TOTAL_ATTACHMENT_BYTES) {
            throw ExportException("备份附件总大小超过上限，请减少附件后再导出")
        }

        val manifest = BackupManifest(
            formatName = BackupFormat.FORMAT_NAME,
            formatVersion = BackupFormat.FORMAT_VERSION,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            databaseVersion = databaseVersion,
            createdAt = clock(),
            projectCount = projects.size,
            nodeCount = nodes.size,
            noteCount = notes.size,
            fileCount = files.size,
            totalFileBytes = totalFileBytes,
        )

        // 导出必须与导入端使用相同的 ZIP 条目预算，避免生成自己无法恢复的备份。
        // 使用 Long 计算，避免大文件数时 Int 溢出。
        val requiredZipEntries = files.size.toLong() + 2L // manifest.json + data.json
        if (requiredZipEntries > limits.maxZipEntries.toLong()) {
            throw ExportException(
                "备份 ZIP 条目数超过上限（需要 $requiredZipEntries 条，最多 ${limits.maxZipEntries} 条）",
            )
        }
        val manifestBytes = BackupJson.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
        if (manifestBytes.size.toLong() > limits.maxJsonEntryBytes) {
            throw ExportException("manifest.json 超过 JSON 条目大小上限，无法生成可恢复的备份")
        }

        onProgress(
            BackupProgress(
                processedFiles = 0,
                totalFiles = files.size,
                processedBytes = 0L,
                totalBytes = totalFileBytes,
            ),
        )

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            writeEntry(zip, BackupFormat.MANIFEST_ENTRY, manifestBytes)

            val fileRecords = ArrayList<BackupFileRecord>(files.size)
            var processedFiles = 0
            var processedBytes = 0L
            files.forEachIndexed { index, file ->
                currentCoroutineContext().ensureActive()
                val archivePath = BackupFormat.ATTACHMENTS_PREFIX +
                    (index + 1).toString().padStart(6, '0')
                val source = storage.storedFile(file.relativePath)
                val digest = BackupHashing.newSha256()
                var written = 0L
                zip.putNextEntry(ZipEntry(archivePath))
                try {
                    source.inputStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > BackupFormat.MAX_ATTACHMENT_BYTES) {
                                throw ExportException("文件超过备份大小上限：${file.displayName}")
                            }
                            zip.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                } catch (error: Exception) {
                    throw error
                }

                if (written != file.sizeBytes) {
                    throw ExportException("导出附件时大小发生变化：${file.displayName}")
                }
                fileRecords += BackupFileRecord(
                    id = file.id,
                    projectId = file.projectId,
                    nodeId = file.nodeId,
                    displayName = file.displayName,
                    mimeType = file.mimeType,
                    extension = file.extension,
                    sizeBytes = file.sizeBytes,
                    category = file.category,
                    importedAt = file.importedAt,
                    archivePath = archivePath,
                    sha256 = BackupHashing.hex(digest),
                )
                processedFiles++
                processedBytes += written
                onProgress(
                    BackupProgress(
                        processedFiles = processedFiles,
                        totalFiles = files.size,
                        processedBytes = processedBytes,
                        totalBytes = totalFileBytes,
                    ),
                )
            }

            currentCoroutineContext().ensureActive()
            val data = BackupData(
                projects = projects.map { project ->
                    BackupProject(
                        id = project.id,
                        name = project.name,
                        description = project.description,
                        createdAt = project.createdAt,
                        updatedAt = project.updatedAt,
                    )
                },
                nodes = nodes.map { node ->
                    BackupNode(
                        id = node.id,
                        projectId = node.projectId,
                        title = node.title,
                        description = node.description,
                        occurredAt = node.occurredAt,
                        createdAt = node.createdAt,
                    )
                },
                notes = notes.map { note ->
                    BackupNote(
                        id = note.id,
                        projectId = note.projectId,
                        nodeId = note.nodeId,
                        content = note.content,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt,
                    )
                },
                files = fileRecords,
            )
            val dataBytes = BackupJson.encodeData(data).toByteArray(Charsets.UTF_8)
            if (dataBytes.size.toLong() > limits.maxJsonEntryBytes) {
                throw ExportException("data.json 超过 JSON 条目大小上限，无法生成可恢复的备份")
            }
            writeEntry(
                zip = zip,
                entryName = BackupFormat.DATA_ENTRY,
                content = dataBytes,
            )
            zip.finish()
        }

        return BackupExportResult.Success(
            projectCount = projects.size,
            nodeCount = nodes.size,
            noteCount = notes.size,
            fileCount = files.size,
            totalFileBytes = totalFileBytes,
        )
    }

    private fun writeEntry(zip: ZipOutputStream, entryName: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(content)
        zip.closeEntry()
    }

    private class ExportException(message: String) : Exception(message)
}
