package com.projecttrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文字记录。
 *
 * 外键规则：项目删除时级联删除；节点删除时 [nodeId] 置空（记录本身保留）。
 */
@Entity(
    tableName = "notes",
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
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val nodeId: Long?,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
