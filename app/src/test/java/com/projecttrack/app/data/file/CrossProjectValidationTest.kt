package com.projecttrack.app.data.file

import com.projecttrack.app.data.DataMutationGuard
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.data.repository.TransactionRunner
import com.projecttrack.app.testutil.FakeFileDao
import com.projecttrack.app.testutil.FakeNodeDao
import com.projecttrack.app.testutil.FakeNoteDao
import com.projecttrack.app.testutil.FakeProjectDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class CrossProjectValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: FileStorage
    private lateinit var projectDao: FakeProjectDao
    private lateinit var nodeDao: FakeNodeDao
    private lateinit var noteDao: FakeNoteDao
    private lateinit var fileDao: FakeFileDao
    private lateinit var transactions: TransactionRunner

    @Before
    fun setUp() {
        runBlocking {
            storage = FileStorage(tempFolder.newFolder("files"))
            projectDao = FakeProjectDao()
            nodeDao = FakeNodeDao()
            noteDao = FakeNoteDao()
            fileDao = FakeFileDao()
            transactions = object : TransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T = block()
            }
            projectDao.insert(
                ProjectEntity(
                    id = 1L,
                    name = "项目一",
                    description = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            projectDao.insert(
                ProjectEntity(
                    id = 2L,
                    name = "项目二",
                    description = "",
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            )
            nodeDao.insert(
                NodeEntity(
                    id = 11L,
                    projectId = 1L,
                    title = "项目一节点",
                    description = "",
                    occurredAt = 1L,
                    createdAt = 1L,
                ),
            )
            nodeDao.insert(
                NodeEntity(
                    id = 10L,
                    projectId = 2L,
                    title = "项目二节点",
                    description = "",
                    occurredAt = 2L,
                    createdAt = 2L,
                ),
            )
        }
    }

    @Test
    fun importingFileWithNodeFromAnotherProjectIsRejectedBeforeCopy() = runBlocking {
        val importer = createImporter()
        var streamOpened = false
        val result = importer.import(
            projectId = 1L,
            nodeId = 10L,
            meta = ImportFileMeta("wrong.txt", "text/plain", "txt", -1L),
        ) {
            streamOpened = true
            ByteArrayInputStream("content".toByteArray())
        }

        assertTrue(result is FileImporter.Result.Failure)
        assertTrue("归属校验应发生在复制之前", !streamOpened)
        assertTrue(fileDao.files.value.isEmpty())
        assertTrue(storage.listAttachments().isEmpty())
    }

    @Test
    fun importingFileWithNodeFromSameProjectSucceeds() = runBlocking {
        val importer = createImporter()
        val result = importer.import(
            projectId = 1L,
            nodeId = 11L,
            meta = ImportFileMeta("ok.txt", "text/plain", "txt", -1L),
        ) {
            ByteArrayInputStream("content".toByteArray())
        }

        assertTrue(result is FileImporter.Result.Success)
        val file = (result as FileImporter.Result.Success).file
        assertEquals(11L, file.nodeId)
        assertEquals(1L, file.projectId)
    }

    @Test
    fun creatingNoteWithNodeFromAnotherProjectIsRejected() = runBlocking {
        val repository = createRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createNote(1L, 10L, "跨项目记录") }
        }
        assertTrue(noteDao.notes.value.isEmpty())

        val noteId = repository.createNote(1L, 11L, "正确记录")
        val note = noteDao.getById(noteId)
        assertTrue(note != null)
        assertEquals(11L, note?.nodeId)
        assertEquals(1L, note?.projectId)
    }

    @Test
    fun creatingProjectNoteWithoutNodeStillWorks() = runBlocking {
        val repository = createRepository()
        val noteId = repository.createNote(1L, null, "项目记录")
        val note = noteDao.getById(noteId)
        assertNull(note?.nodeId)
        assertEquals("项目记录", note?.content)
    }

    private fun createImporter(): FileImporter = FileImporter(
        storage = storage,
        fileDao = fileDao,
        projectDao = projectDao,
        nodeDao = nodeDao,
        transactions = transactions,
        clock = { 1000L },
    )

    private fun createRepository(): ProjectRepository = ProjectRepository(
        projectDao = projectDao,
        nodeDao = nodeDao,
        noteDao = noteDao,
        fileDao = fileDao,
        storage = storage,
        transactions = transactions,
        clock = { 2000L },
        mutationGuard = DataMutationGuard(),
    )
}
