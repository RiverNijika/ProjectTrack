package com.projecttrack.app.data.model

/**
 * 用户随时定义的进度节点。
 *
 * 节点标题完全由用户输入，不预置任何流程模板；[occurredAt] 允许修改，以便补录历史进展。
 */
data class ProgressNode(
    val id: Long,
    val projectId: Long,
    val title: String,
    val description: String,
    val occurredAt: Long,
    val createdAt: Long,
)
