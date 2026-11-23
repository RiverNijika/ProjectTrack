package com.projecttrack.app.domain

import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.FileQuery
import com.projecttrack.app.data.model.FileRow
import com.projecttrack.app.data.model.FileSort
import com.projecttrack.app.data.model.NodeFilter
import com.projecttrack.app.data.model.StoredFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FileFilterEngineTest {

    private fun row(
        id: Long,
        projectId: Long,
        projectName: String,
        nodeId: Long?,
        nodeTitle: String?,
        displayName: String,
        category: FileCategory,
        importedAt: Long,
    ): FileRow = FileRow(
        file = StoredFile(
            id = id,
            projectId = projectId,
            nodeId = nodeId,
            displayName = displayName,
            mimeType = null,
            extension = displayName.substringAfterLast('.', ""),
            sizeBytes = 1024,
            category = category,
            relativePath = "attachments/$id",
            importedAt = importedAt,
        ),
        projectName = projectName,
        nodeTitle = nodeTitle,
        exists = true,
    )

    private val rows = listOf(
        row(1, 10, "项目甲", nodeId = 100, nodeTitle = "节点A", displayName = "Meeting.mp3", category = FileCategory.AUDIO, importedAt = 300),
        row(2, 10, "项目甲", nodeId = null, nodeTitle = null, displayName = "notes.json", category = FileCategory.TEXT, importedAt = 200),
        row(3, 20, "项目乙", nodeId = 200, nodeTitle = "节点B", displayName = "demo.mp4", category = FileCategory.VIDEO, importedAt = 100),
        row(4, 20, "项目乙", nodeId = null, nodeTitle = null, displayName = "report.pdf", category = FileCategory.OTHER, importedAt = 400),
    )

    @Test
    fun projectScopeOnlyReturnsThatProject() {
        val result = FileFilterEngine.apply(rows, FileQuery(projectId = 10L))
        assertEquals(listOf(1L, 2L), result.map { it.file.id })
    }

    @Test
    fun crossProjectReturnsEverythingSortedByImportTimeDescending() {
        val result = FileFilterEngine.apply(rows, FileQuery())
        assertEquals(listOf(4L, 1L, 2L, 3L), result.map { it.file.id })
    }

    @Test
    fun nodeFilterSupportsUnlinkedAndSpecificNode() {
        val unlinked = FileFilterEngine.apply(
            rows,
            FileQuery(projectId = 10L, nodeFilter = NodeFilter.Unlinked),
        )
        assertEquals(listOf(2L), unlinked.map { it.file.id })

        val byNode = FileFilterEngine.apply(
            rows,
            FileQuery(projectId = 20L, nodeFilter = NodeFilter.ById(200L)),
        )
        assertEquals(listOf(3L), byNode.map { it.file.id })
    }

    @Test
    fun categoryFilterAndSearchCombine() {
        val audio = FileFilterEngine.apply(rows, FileQuery(category = FileCategory.AUDIO))
        assertEquals(listOf(1L), audio.map { it.file.id })

        val search = FileFilterEngine.apply(rows, FileQuery(search = "REPORT"))
        assertEquals(listOf(4L), search.map { it.file.id })

        val combined = FileFilterEngine.apply(
            rows,
            FileQuery(projectId = 10L, category = FileCategory.TEXT, search = "json"),
        )
        assertEquals(listOf(2L), combined.map { it.file.id })
    }

    @Test
    fun nameSortIsCaseInsensitive() {
        val result = FileFilterEngine.apply(
            rows,
            FileQuery(sort = FileSort.NAME_ASC),
        )
        assertEquals(
            listOf("demo.mp4", "Meeting.mp3", "notes.json", "report.pdf"),
            result.map { it.file.displayName },
        )
    }

    @Test
    fun noResultsReturnsEmptyList() {
        val result = FileFilterEngine.apply(rows, FileQuery(search = "不存在的文件"))
        assertEquals(emptyList<FileRow>(), result)
    }
}
