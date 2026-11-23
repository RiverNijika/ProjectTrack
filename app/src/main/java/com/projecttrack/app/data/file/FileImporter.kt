package com.projecttrack.app.data.file

import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.NodeDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.toModel
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.model.StoredFile
import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * 把系统文件选择器返回的文件复制到应用私有目录，并在复制成功后写入数据库。
 *
 * 关键顺序：先校验归属、再复制文件、提交前检查取消、最后插入数据库。
 * 数据库提交是短暂 NonCancellable 临界区，提交完整返回后按成功处理；
 * 提交前失败或取消会删除未入库的附件，不会留下“数据库有记录但文件不存在”或
 * “文件存在但没有记录”的假成功状态。
 */
class FileImporter(
    private val storage: FileStorage,
    private val fileDao: FileDao,
    private val projectDao: ProjectDao,
    private val nodeDao: NodeDao,
    private val transactions: TransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface Result {
        data class Success(val file: StoredFile) : Result
        data class Failure(val displayName: String, val message: String) : Result
    }

    suspend fun import(
        projectId: Long,
        nodeId: Long?,
        meta: ImportFileMeta,
        openStream: () -> InputStream?,
    ): Result {
        // 归属校验必须在复制文件之前完成，验证失败不得留下任何副本或记录。
        if (projectDao.getById(projectId) == null) {
            return Result.Failure(meta.displayName, "目标项目不存在或已被删除")
        }
        if (nodeId != null) {
            val node = nodeDao.getById(nodeId)
            if (node == null) {
                return Result.Failure(meta.displayName, "目标节点不存在或已被删除")
            }
            if (node.projectId != projectId) {
                return Result.Failure(meta.displayName, "目标节点不属于所选项目")
            }
        }

        val target = storage.createStoredTarget(meta.displayName, now = clock())
        val sizeBytes = try {
            storage.copyFromStream(target.relativePath, openStream)
        } catch (cancellation: CancellationException) {
            // FileStorage 已删除不完整文件；取消必须继续向上传播。
            throw cancellation
        } catch (error: Throwable) {
            return Result.Failure(meta.displayName, copyErrorMessage(error))
        }

        // 提交前检查取消：如果此时已取消，不进入数据库事务，清理刚复制的附件。
        try {
            currentCoroutineContext().ensureActive()
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { storage.delete(target.relativePath) }
            throw cancellation
        }

        val stored = try {
            // 数据库提交是短暂临界区。使用 NonCancellable 只保护这一小段，
            // 避免外部取消在“事务已提交、调用方尚未收到返回”窗口进入 catch 并误删已入库附件。
            // 附件复制等长 I/O 仍在可取消区间内。
            withContext(NonCancellable) {
                transactions.run {
                    val now = clock()
                    val entity = FileEntity(
                        projectId = projectId,
                        nodeId = nodeId,
                        displayName = meta.displayName,
                        mimeType = meta.mimeType,
                        extension = FileTypeClassifier.extensionOf(meta.displayName),
                        sizeBytes = sizeBytes,
                        category = FileTypeClassifier.classify(meta.displayName, meta.mimeType),
                        relativePath = target.relativePath,
                        importedAt = now,
                    )
                    val id = fileDao.insert(entity)
                    projectDao.touch(projectId, now)
                    entity.copy(id = id).toModel()
                }
            }
        } catch (cancellation: CancellationException) {
            // 外部取消不应进入这里：提交临界区被 NonCancellable 保护。
            // 若事务实现自身抛出取消异常，说明提交未正常返回，按未提交失败处理并清理附件。
            withContext(NonCancellable) { storage.delete(target.relativePath) }
            throw cancellation
        } catch (error: Throwable) {
            // 数据库写入失败：回滚已复制的文件，避免产生孤立文件。
            withContext(NonCancellable) { storage.delete(target.relativePath) }
            return Result.Failure(meta.displayName, copyErrorMessage(error))
        }
        return Result.Success(stored)
    }

    private fun copyErrorMessage(error: Throwable): String = when (error) {
        is SecurityException -> "没有读取该文件的权限"
        is IOException -> "读取或写入文件失败"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "导入失败"
    }
}
