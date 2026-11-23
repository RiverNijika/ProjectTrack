package com.projecttrack.app.data.model

/** 文件的展示分类。预览能力只覆盖音频、视频和纯文本三类。 */
enum class FileCategory {
    AUDIO,
    VIDEO,
    TEXT,
    OTHER;

    companion object {
        fun fromName(value: String): FileCategory = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * 已导入到应用私有目录的文件元数据。
 *
 * 数据库只保存元数据与 [relativePath]，文件本体保存在应用私有目录中。文件名重复不会覆盖旧文件，
 * [displayName] 是给用户看的原始名称，磁盘上的文件名由 [relativePath] 决定。
 */
data class StoredFile(
    val id: Long,
    val projectId: Long,
    val nodeId: Long?,
    val displayName: String,
    val mimeType: String?,
    val extension: String,
    val sizeBytes: Long,
    val category: FileCategory,
    val relativePath: String,
    val importedAt: Long,
)

/** 文件列表展示行：包含所属项目、所属节点名以及磁盘文件当前是否存在。 */
data class FileRow(
    val file: StoredFile,
    val projectName: String,
    val nodeTitle: String?,
    val exists: Boolean,
)

/** 从系统文件选择器解析出的导入元数据。 */
data class ImportFileMeta(
    val displayName: String,
    val mimeType: String?,
    val extension: String,
    val sizeHint: Long,
)
