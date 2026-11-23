package com.projecttrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.projecttrack.app.data.model.FileCategory

/**
 * 导入文件的元数据。只保存相对路径等元数据，不保存文件内容。
 *
 * 外键规则：项目删除时级联删除记录；节点删除时 [nodeId] 置空（文件与记录都保留）。
 */
@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("projectId"),
        Index("nodeId"),
        Index(value = ["relativePath"], unique = true),
        Index("importedAt"),
    ],
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
