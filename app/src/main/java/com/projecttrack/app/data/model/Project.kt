package com.projecttrack.app.data.model

/** 项目。名称、简介、创建时间和更新时间均由本应用维护。 */
data class Project(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 项目列表展示所需的摘要信息。
 *
 * [latestNodeTitle] 与 [latestNodeOccurredAt] 来自 [LatestNodeSelector][com.projecttrack.app.domain.LatestNodeSelector]，
 * 代表项目当前状态；为空表示项目还没有任何进度节点。
 */
data class ProjectSummary(
    val project: Project,
    val latestNodeTitle: String?,
    val latestNodeOccurredAt: Long?,
)
