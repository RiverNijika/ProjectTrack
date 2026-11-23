package com.projecttrack.app.data.model

/**
 * 文字记录。
 *
 * 记录可以挂在项目下（[nodeId] 为空），也可以挂在某个节点下。节点被删除时记录不会被删除，
 * 而是变为项目下的“未关联节点”记录。
 */
data class Note(
    val id: Long,
    val projectId: Long,
    val nodeId: Long?,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
