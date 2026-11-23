package com.projecttrack.app.data.file

import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.toModel
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.model.StoredFile
import com.projecttrack.app.data.repository.TransactionRunner
import java.io.IOException
import java.io.InputStream

/**
 * 把系统文件选择器返回的文件复制到应用私有目录，并在复制成功后写入数据库。
 *
 * 关键顺序：先复制文件，成功后再插入数据库记录；任何一个环节失败都会删除不完整副本，
 * 不会留下“数据库有记录但文件不存在”或“文件存在但没有记录”的假成功状态。
 */
class FileImporter(
    private val storage: FileStorage,
    private val fileDao: FileDao,
    private val projectDao: ProjectDao,
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
        val target = storage.createStoredTarget(meta.displayName, now = clock())
        val sizeBytes = try {
            storage.copyFromStream(target.relativePath, openStream)
        } catch (error: Throwable) {
            return Result.Failure(meta.displayName, copyErrorMessage(error))
        }

        return try {
            val stored = transactions.run {
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
            Result.Success(stored)
        } catch (error: Throwable) {
            // 数据库写入失败：回滚已复制的文件，避免产生孤立文件。
            storage.delete(target.relativePath)
            Result.Failure(meta.displayName, copyErrorMessage(error))
        }
    }

    private fun copyErrorMessage(error: Throwable): String = when (error) {
        is SecurityException -> "没有读取该文件的权限"
        is IOException -> "读取或写入文件失败"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "导入失败"
    }
}
