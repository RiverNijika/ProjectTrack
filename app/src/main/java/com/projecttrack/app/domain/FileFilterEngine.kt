package com.projecttrack.app.domain

import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.FileQuery
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.FileSort
import com.projecttrack.app.data.model.NodeFilter

/**
 * 文件列表筛选与排序的纯函数实现。
 *
 * 项目内文件页与跨项目文件页共用同一套筛选逻辑：项目筛选、节点筛选（含未关联节点）、
 * 分类筛选、文件名搜索，以及按导入时间或名称排序。
 */
object FileFilterEngine {
    fun apply(rows: List<FileRow>, query: FileQuery): List<FileRow> {
        val keyword = query.search.trim().lowercase()
        val filtered = rows.filter { row ->
            val file = row.file
            val matchesProject = query.projectId == null || file.projectId == query.projectId
            val matchesNode = when (val nodeFilter = query.nodeFilter) {
                NodeFilter.All -> true
                NodeFilter.Unlinked -> file.nodeId == null
                is NodeFilter.ById -> file.nodeId == nodeFilter.nodeId
            }
            val matchesCategory = query.category == null || file.category == query.category
            val matchesSearch = keyword.isEmpty() || file.displayName.lowercase().contains(keyword)
            matchesProject && matchesNode && matchesCategory && matchesSearch
        }
        return when (query.sort) {
            FileSort.IMPORTED_DESC -> filtered.sortedWith(
                compareByDescending<FileRow> { it.file.importedAt }.thenByDescending { it.file.id },
            )
            FileSort.NAME_ASC -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.file.displayName },
            )
        }
    }

    fun matchesCategory(category: FileCategory?, row: FileRow): Boolean =
        category == null || row.file.category == category
}
