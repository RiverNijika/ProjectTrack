package com.projecttrack.app.data.model

/** 文件列表的节点筛选条件。 */
sealed interface NodeFilter {
    data object All : NodeFilter
    data object Unlinked : NodeFilter
    data class ById(val nodeId: Long) : NodeFilter
}

enum class FileSort {
    IMPORTED_DESC,
    NAME_ASC,
}

/**
 * 文件查询条件。[projectId] 为空表示跨项目查询。
 */
data class FileQuery(
    val projectId: Long? = null,
    val nodeFilter: NodeFilter = NodeFilter.All,
    val category: FileCategory? = null,
    val search: String = "",
    val sort: FileSort = FileSort.IMPORTED_DESC,
)

/** 节点筛选下拉项，带上项目名称以便跨项目区分。 */
data class NodeLabel(
    val nodeId: Long,
    val projectId: Long,
    val projectName: String,
    val nodeTitle: String,
)

data class ProjectOption(
    val projectId: Long,
    val name: String,
)
