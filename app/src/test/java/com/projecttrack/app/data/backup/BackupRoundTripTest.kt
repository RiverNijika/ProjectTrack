package com.projecttrack.app.data.backup

import com.projecttrack.app.data.file.FileStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class BackupRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var source: BackupFixture
    private lateinit var sourceTargets: Map<Long, String>
    private lateinit var backupBytes: ByteArray

    @Before
    fun setUp() {
        runBlocking {
            source = createFixture(tempFolder.newFolder("source"))
            seedSampleData(source)
            sourceTargets = source.fileDao.files.value.associate { it.id to it.relativePath }
            backupBytes = exportToBytes(source)
        }
    }

    @Test
    fun fullBackupRestoresAllContentAndRelationshipsWithNewIds() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")

        val result = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))
        assertTrue("恢复应成功：$result", result is BackupRestoreResult.Success)
        val success = result as BackupRestoreResult.Success
        assertEquals(2, success.projectCount)
        assertEquals(3, success.nodeCount)
        assertEquals(4, success.noteCount)
        assertEquals(4, success.fileCount)

        assertEquals(2, target.projectDao.getAll().size)
        assertEquals(3, target.nodeDao.getAll().size)
        assertEquals(4, target.noteDao.getAll().size)
        assertEquals(4, target.fileDao.getAll().size)

        val projects = target.projectDao.getAll()
        assertTrue("恢复后项目 ID 应重新生成", projects.none { it.id in setOf(10L, 20L) })
        val projectA = projects.first { it.name == "项目甲" }
        val projectB = projects.first { it.name == "项目乙" }
        assertEquals("项目甲简介", projectA.description)
        assertEquals(100L, projectA.createdAt)
        assertEquals(200L, projectA.updatedAt)
        assertEquals("项目乙简介", projectB.description)
        assertEquals(300L, projectB.createdAt)
        assertEquals(400L, projectB.updatedAt)

        val nodes = target.nodeDao.getAll()
        assertTrue(nodes.none { it.id in setOf(100L, 101L, 200L) })
        val designNode = nodes.first { it.title == "设计确认" }
        assertEquals(projectA.id, designNode.projectId)
        assertEquals(150L, designNode.occurredAt)
        assertEquals(140L, designNode.createdAt)
        val pausedNode = nodes.first { it.title == "开发暂停" }
        assertEquals(projectA.id, pausedNode.projectId)
        val testNode = nodes.first { it.title == "测试完成" }
        assertEquals(projectB.id, testNode.projectId)

        val notes = target.noteDao.getAll()
        assertTrue(notes.none { it.id in setOf(1000L, 1001L, 1002L, 1003L) })
        val nodeNote = notes.first { it.content == "节点记录：设计文档已归档" }
        assertEquals(projectA.id, nodeNote.projectId)
        assertEquals(designNode.id, nodeNote.nodeId)
        assertEquals(160L, nodeNote.createdAt)
        assertEquals(170L, nodeNote.updatedAt)

        val projectNote = notes.first { it.content == "项目记录：整体节奏正常" }
        assertNull(projectNote.nodeId)
        val unlinkedNote = notes.first { it.content == "未关联记录：原节点已删除" }
        assertNull("未关联记录恢复后仍应没有 nodeId", unlinkedNote.nodeId)
        val projectBNote = notes.first { it.content == "乙节点记录" }
        assertEquals(projectB.id, projectBNote.projectId)
        assertEquals(testNode.id, projectBNote.nodeId)

        val files = target.fileDao.getAll()
        assertTrue(files.none { it.id in setOf(5000L, 5001L, 5002L, 5003L) })
        assertEquals(4, files.map { it.relativePath }.toSet().size)
        files.forEach { restored ->
            assertFalse("恢复后的内部文件名不应沿用原文件", restored.relativePath in sourceTargets.values)
            assertTrue(target.storage.exists(restored.relativePath))
            assertEquals(restored.sizeBytes, target.storage.storedFile(restored.relativePath).length())
        }

        val meeting = files.first { it.displayName == "meeting.mp3" }
        assertEquals(projectA.id, meeting.projectId)
        assertEquals(designNode.id, meeting.nodeId)
        assertEquals("audio/mpeg", meeting.mimeType)
        assertEquals("mp3", meeting.extension)
        assertEquals(600L, meeting.importedAt)
        assertTrue(meeting.sizeBytes > 0L)
        assertEquals(
            SAMPLE_AUDIO_BYTES.toList(),
            target.storage.storedFile(meeting.relativePath).readBytes().toList(),
        )

        val notesFile = files.first { it.displayName == "notes.txt" }
        assertNull(notesFile.nodeId)
        assertEquals(projectA.id, notesFile.projectId)
        assertEquals(
            SAMPLE_TEXT_BYTES.toList(),
            target.storage.storedFile(notesFile.relativePath).readBytes().toList(),
        )

        val image = files.first { it.displayName == "screen.png" }
        assertEquals(projectB.id, image.projectId)
        assertEquals(testNode.id, image.nodeId)
        assertEquals(
            SAMPLE_IMAGE_BYTES.toList(),
            target.storage.storedFile(image.relativePath).readBytes().toList(),
        )

        val unlinkedFile = files.first { it.displayName == "unlinked.bin" }
        assertNull(unlinkedFile.nodeId)
        assertEquals(projectA.id, unlinkedFile.projectId)
        assertEquals(
            SAMPLE_UNLINKED_BYTES.toList(),
            target.storage.storedFile(unlinkedFile.relativePath).readBytes().toList(),
        )

        // 源数据不应被导出或恢复操作修改。
        assertEquals(2, source.projectDao.getAll().size)
        assertEquals(4, source.fileDao.getAll().size)
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun restoredAttachmentNamesAreUniqueAndDoNotOverwriteExistingData() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        seedExistingData(target)
        val cache = tempFolder.newFolder("cache")
        val existingProject = target.projectDao.getById(1L)
        val existingFile = target.fileDao.getAll().single()
        val existingContent = target.storage.storedFile(existingFile.relativePath).readBytes()

        val first = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))
        assertTrue(first is BackupRestoreResult.Success)
        val allNamesAfterFirst = target.fileDao.getAll().map { it.relativePath }
        assertEquals(allNamesAfterFirst.size, allNamesAfterFirst.toSet().size)
        assertTrue(allNamesAfterFirst.contains(existingFile.relativePath))
        assertEquals(existingFile.relativePath, target.fileDao.getAll().first { it.displayName == "existing.txt" }.relativePath)
        assertEquals(existingProject, target.projectDao.getById(1L))
        assertEquals(
            existingContent.toList(),
            target.storage.storedFile(existingFile.relativePath).readBytes().toList(),
        )

        val second = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))
        assertTrue(second is BackupRestoreResult.Success)
        val allFiles = target.fileDao.getAll()
        assertEquals(1 + 4 + 4, allFiles.size)
        assertEquals(allFiles.size, allFiles.map { it.relativePath }.toSet().size)
        assertEquals(1 + 2 + 2, target.projectDao.getAll().size)
        assertEquals(existingProject, target.projectDao.getById(1L))
        assertEquals(
            existingContent.toList(),
            target.storage.storedFile(existingFile.relativePath).readBytes().toList(),
        )
        val duplicatedNames = allFiles.groupBy { it.displayName }.filterValues { it.size > 1 }
        assertTrue("重复恢复应产生同名副本而非覆盖", duplicatedNames.isNotEmpty())
        assertNotEquals(
            allFiles.filter { it.displayName == "meeting.mp3" }.map { it.id }.toSet().size,
            1,
        )
    }
}
