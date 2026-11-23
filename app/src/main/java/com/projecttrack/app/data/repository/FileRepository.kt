package com.projecttrack.app.data.repository

import com.projecttrack.app.data.file.FileImporter
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.file.TextPreviewResult
import com.projecttrack.app.data.local.dao.FileDao
import com.projecttrack.app.data.local.dao.FileWithNameRow
import com.projecttrack.app.data.local.dao.ProjectDao
import com.projecttrack.app.data.local.toModel
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.model.StoredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.InputStream

sealed interface FileDeleteResult {
    data object Deleted : FileDeleteResult
    data object NotFound : FileDeleteResult
    data object FileDeleteFailed : FileDeleteResult
}

/** 文件仓库：元数据读写、流式导入、删除、文本预览读取与孤立文件清理。 */
class FileRepository(
    private val fileDao: FileDao,
    private val projectDao: ProjectDao,
    private val storage: FileStorage,
    private val transactions: TransactionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val importer = FileImporter(storage, fileDao, projectDao, transactions, clock)

    fun observeAllFiles(): Flow<List<FileRow>> =
        fileDao.observeAll().map { rows -> rows.toFileRows() }.flowOn(Dispatchers.IO)

    fun observeProjectFiles(projectId: Long): Flow<List<FileRow>> =
        fileDao.observeByProject(projectId).map { rows -> rows.toFileRows() }.flowOn(Dispatchers.IO)

    fun observeNodeFiles(nodeId: Long): Flow<List<FileRow>> =
        fileDao.observeByNode(nodeId).map { rows -> rows.toFileRows() }.flowOn(Dispatchers.IO)

    fun observeFile(fileId: Long): Flow<FileRow?> =
        fileDao.observeById(fileId).map { row -> row?.toFileRow() }.flowOn(Dispatchers.IO)

    suspend fun getFile(fileId: Long): StoredFile? = fileDao.getById(fileId)?.toModel()

    suspend fun importFile(
        projectId: Long,
        nodeId: Long?,
        meta: ImportFileMeta,
        openStream: () -> InputStream?,
    ): FileImporter.Result = importer.import(projectId, nodeId, meta, openStream)

    /**
     * 删除文件：先删除磁盘副本，成功后再删除数据库记录；文件已经不存在时直接清理记录。
     */
    suspend fun deleteFile(fileId: Long): FileDeleteResult {
        val entity = fileDao.getById(fileId) ?: return FileDeleteResult.NotFound
        val fileDeleted = if (storage.exists(entity.relativePath)) {
            storage.delete(entity.relativePath)
        } else {
            true
        }
        if (!fileDeleted) return FileDeleteResult.FileDeleteFailed
        transactions.run {
            fileDao.deleteById(fileId)
            projectDao.touch(entity.projectId, clock())
        }
        return FileDeleteResult.Deleted
    }

    suspend fun readTextPreview(fileId: Long, maxBytes: Long? = null): TextPreviewResult? {
        val entity = fileDao.getById(fileId) ?: return null
        return if (maxBytes == null) {
            storage.readTextPreview(entity.relativePath)
        } else {
            storage.readTextPreview(entity.relativePath, maxBytes)
        }
    }

    fun absoluteFile(file: StoredFile) = storage.storedFile(file.relativePath)

    fun supportsPreview(category: FileCategory) = category == FileCategory.AUDIO ||
        category == FileCategory.VIDEO ||
        category == FileCategory.TEXT

    /** 清理超过 24 小时仍未被数据库引用的孤立文件，返回清理数量。 */
    suspend fun cleanupOrphanFiles(now: Long = clock()): Int =
        storage.deleteOrphanFiles(referencedPaths = fileDao.allPaths().toSet(), now = now)

    private fun List<FileWithNameRow>.toFileRows(): List<FileRow> = map { it.toFileRow() }

    private fun FileWithNameRow.toFileRow(): FileRow = FileRow(
        file = file.toModel(),
        projectName = projectName,
        nodeTitle = nodeTitle,
        exists = storage.exists(file.relativePath),
    )
}
