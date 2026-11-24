package com.projecttrack.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.FileCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在真实 Android SQLite/Room 上验证外键规则。
 * 该测试需要设备或模拟器；没有设备时仅参与编译。
 */
@RunWith(AndroidJUnit4::class)
class RoomRulesInstrumentedTest {

    private lateinit var database: ProjectTrackDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ProjectTrackDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingNodeDetachesNotesAndFiles() = runBlocking {
        val projectId = database.projectDao().insert(project())
        val nodeId = database.nodeDao().insert(node(projectId))
        val noteId = database.noteDao().insert(note(projectId, nodeId))
        val fileId = database.fileDao().insert(file(projectId, nodeId))

        database.nodeDao().deleteById(nodeId)

        assertNull(database.noteDao().getById(noteId)?.nodeId)
        assertNull(database.fileDao().getById(fileId)?.nodeId)
        assertEquals(1, database.noteDao().getAll().size)
        assertEquals(1, database.fileDao().getAll().size)
    }

    @Test
    fun deletingProjectCascadesNodesNotesAndFiles() = runBlocking {
        val projectId = database.projectDao().insert(project())
        val nodeId = database.nodeDao().insert(node(projectId))
        database.noteDao().insert(note(projectId, nodeId))
        database.noteDao().insert(note(projectId, null))
        database.fileDao().insert(file(projectId, nodeId))
        database.fileDao().insert(file(projectId, null))

        database.projectDao().deleteById(projectId)

        assertEquals(0, database.nodeDao().getAll().size)
        assertEquals(0, database.noteDao().getAll().size)
        assertEquals(0, database.fileDao().getAll().size)
    }

    private fun project() = ProjectEntity(
        name = "仪器测试项目",
        description = "Room FK",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun node(projectId: Long) = NodeEntity(
        projectId = projectId,
        title = "节点",
        description = "说明",
        occurredAt = 2L,
        createdAt = 2L,
    )

    private fun note(projectId: Long, nodeId: Long?) = NoteEntity(
        projectId = projectId,
        nodeId = nodeId,
        content = "记录",
        createdAt = 3L,
        updatedAt = 3L,
    )

    private fun file(projectId: Long, nodeId: Long?) = FileEntity(
        projectId = projectId,
        nodeId = nodeId,
        displayName = "attachment.txt",
        mimeType = "text/plain",
        extension = "txt",
        sizeBytes = 3L,
        category = FileCategory.TEXT,
        relativePath = "attachments/instrumented-${System.nanoTime()}-${nodeId ?: "none"}",
        importedAt = 4L,
    )
}
