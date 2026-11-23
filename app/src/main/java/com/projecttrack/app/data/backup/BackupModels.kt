package com.projecttrack.app.data.backup

import com.projecttrack.app.data.model.FileCategory
import java.io.File

/** 备份容器与安全限制。格式版本从 1 开始，未知版本一律拒绝。 */
object BackupFormat {
    const val FORMAT_NAME = "ProjectTrackBackup"
    const val FORMAT_VERSION = 1

    const val FILE_EXTENSION = ".ptbackup"
    const val MIME_TYPE = "application/zip"

    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"
    const val ATTACHMENTS_DIRECTORY = "attachments"
    const val ATTACHMENTS_PREFIX = "$ATTACHMENTS_DIRECTORY/"

    /** JSON 条目上限，防止异常备份占用过多内存。 */
    const val MAX_JSON_ENTRY_BYTES = 32L * 1024L * 1024L

    /** 单个附件与附件总大小上限，用于防御异常压缩包与磁盘耗尽。 */
    const val MAX_ATTACHMENT_BYTES = 4L * 1024L * 1024L * 1024L
    const val MAX_TOTAL_ATTACHMENT_BYTES = 16L * 1024L * 1024L * 1024L

    /** ZIP 条目数量上限。 */
    const val MAX_ZIP_ENTRIES = 100_000
}

/**
 * 导出端使用的限制。默认值直接引用 [BackupFormat]，避免导出/导入出现两套魔法数字；
 * 测试可注入更小的预算来验证边界，而不需要创建上万个文件或超大数据。
 */
data class BackupExportLimits(
    val maxJsonEntryBytes: Long = BackupFormat.MAX_JSON_ENTRY_BYTES,
    val maxZipEntries: Int = BackupFormat.MAX_ZIP_ENTRIES,
)

/** 独立于 Room 实体的版本化备份 DTO。 */
data class BackupManifest(
    val formatName: String,
    val formatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val createdAt: Long,
    val projectCount: Int,
    val nodeCount: Int,
    val noteCount: Int,
    val fileCount: Int,
    val totalFileBytes: Long,
)

data class BackupProject(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupNode(
    val id: Long,
    val projectId: Long,
    val title: String,
    val description: String,
    val occurredAt: Long,
    val createdAt: Long,
)

data class BackupNote(
    val id: Long,
    val projectId: Long,
    val nodeId: Long?,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupFileRecord(
    val id: Long,
    val projectId: Long,
    val nodeId: Long?,
    val displayName: String,
    val mimeType: String?,
    val extension: String,
    val sizeBytes: Long,
    val category: FileCategory,
    val importedAt: Long,
    val archivePath: String,
    val sha256: String,
)

data class BackupData(
    val projects: List<BackupProject>,
    val nodes: List<BackupNode>,
    val notes: List<BackupNote>,
    val files: List<BackupFileRecord>,
)

data class BackupAttachmentFile(
    val archivePath: String,
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupExtraction(
    val manifestJson: String,
    val dataJson: String,
    val attachments: Map<String, BackupAttachmentFile>,
)

enum class RestoreStage {
    VALIDATING,
    COPYING_FILES,
    WRITING_DATABASE,
    COMPLETED,
}

data class BackupProgress(
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

data class RestoreProgress(
    val stage: RestoreStage,
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

sealed interface BackupExportResult {
    data class Success(
        val projectCount: Int,
        val nodeCount: Int,
        val noteCount: Int,
        val fileCount: Int,
        val totalFileBytes: Long,
    ) : BackupExportResult

    data class Failure(val message: String) : BackupExportResult
}

sealed interface BackupRestoreResult {
    data class Success(
        val projectCount: Int,
        val nodeCount: Int,
        val noteCount: Int,
        val fileCount: Int,
    ) : BackupRestoreResult

    data class Failure(
        val message: String,
        val cleanupWarning: String? = null,
    ) : BackupRestoreResult
}

/** 备份结构、版本、关系或完整性不合法时抛出的可读异常。 */
class BackupValidationException(message: String) : Exception(message)
