package com.projecttrack.app.data.repository

import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.testutil.FakeFileDao
import com.projecttrack.app.testutil.FakeNodeDao
import com.projecttrack.app.testutil.FakeNoteDao
import com.projecttrack.app.testutil.FakeProjectDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDao: FakeProjectDao
    private lateinit var nodeDao: FakeNodeDao
    private lateinit var noteDao: FakeNoteDao
    private lateinit var fileDao: FakeFileDao
    private lateinit var repository: ProjectRepository

    private val passthroughTransactions = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        runBlocking {
            projectDao = FakeProjectDao()
            nodeDao = FakeNodeDao()
            noteDao = FakeNoteDao()
            fileDao = FakeFileDao()
            repository = ProjectRepository(
                projectDao = projectDao,
                nodeDao = nodeDao,
                noteDao = noteDao,
                fileDao = fileDao,
                storage = FileStorage(tempFolder.newFolder("files")),
                transactions = passthroughTransactions,
                clock = { 5000L },
            )
            projectDao.insert(
                ProjectEntity(
                    id = 1L,
                    name = "测试项目",
                    description = "简介",
                    createdAt = 100L,
                    updatedAt = 100L,
                ),
            )
        }
    }

    @Test
    fun deletingNodeKeepsNotesAndFilesAsUnlinkedContent() = runBlocking {
        nodeDao.nodes.value += NodeEntity(
            id = 10L,
            projectId = 1L,
            title = "等待评审",
            description = "节点说明",
            occurredAt = 200L,
            createdAt = 150L,
        )
        noteDao.notes.value += NoteEntity(
            id = 100L,
            projectId = 1L,
            nodeId = 10L,
            content = "需要保留的记录",
            createdAt = 160L,
            updatedAt = 160L,
        )
        fileDao.files.value += FileEntity(
            id = 200L,
            projectId = 1L,
            nodeId = 10L,
            displayName = "设计稿.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            sizeBytes = 10L,
            category = FileCategory.OTHER,
            relativePath = "attachments/200",
            importedAt = 170L,
        )

        val deleted = repository.deleteNode(10L)

        assertTrue(deleted)
        assertNull(nodeDao.getById(10L))
        val note = noteDao.notes.value.single()
        assertNull("节点删除后记录应变为未关联节点", note.nodeId)
        assertEquals("需要保留的记录", note.content)
        val file = fileDao.files.value.single()
        assertNull("节点删除后文件应变为未关联节点", file.nodeId)
        assertEquals("设计稿.pdf", file.displayName)
        assertEquals(5000L, projectDao.projects.value.single().updatedAt)
    }

    @Test
    fun deletingMissingNodeReportsFalse() = runBlocking {
        assertFalse(repository.deleteNode(999L))
    }

    @Test
    fun projectSummaryUsesLatestNodeAsCurrentState() = runBlocking {
        projectDao.projects.value += ProjectEntity(
            id = 2L,
            name = "空项目",
            description = "",
            createdAt = 100L,
            updatedAt = 100L,
        )
        nodeDao.nodes.value += NodeEntity(
            id = 11L,
            projectId = 1L,
            title = "较早节点",
            description = "",
            occurredAt = 200L,
            createdAt = 200L,
        )
        nodeDao.nodes.value += NodeEntity(
            id = 12L,
            projectId = 1L,
            title = "最新节点",
            description = "",
            occurredAt = 300L,
            createdAt = 300L,
        )

        val summaries = repository.observeProjectSummaries().first()
        val projectOne = summaries.first { it.project.id == 1L }
        assertEquals("最新节点", projectOne.latestNodeTitle)
        assertEquals(300L, projectOne.latestNodeOccurredAt)

        val projectTwo = summaries.first { it.project.id == 2L }
        assertNull("没有节点时不应有当前状态", projectTwo.latestNodeTitle)
    }

    @Test
    fun projectNotesOnlyContainUnlinkedNotes() = runBlocking {
        noteDao.notes.value += NoteEntity(
            id = 1L,
            projectId = 1L,
            nodeId = null,
            content = "项目记录",
            createdAt = 1L,
            updatedAt = 1L,
        )
        noteDao.notes.value += NoteEntity(
            id = 2L,
            projectId = 1L,
            nodeId = 99L,
            content = "节点记录",
            createdAt = 1L,
            updatedAt = 1L,
        )

        val projectNotes = repository.observeProjectNotes(1L).first()
        assertEquals(1, projectNotes.size)
        assertEquals("项目记录", projectNotes.single().content)
    }

    @Test
    fun editingProjectKeepsIdentityAndUpdatesTimestamp() = runBlocking {
        repository.updateProject(1L, "新名称", "新简介")

        val project = projectDao.getById(1L)
        assertNotNull(project)
        assertEquals("新名称", project?.name)
        assertEquals("新简介", project?.description)
        assertEquals(5000L, project?.updatedAt)
    }
}
