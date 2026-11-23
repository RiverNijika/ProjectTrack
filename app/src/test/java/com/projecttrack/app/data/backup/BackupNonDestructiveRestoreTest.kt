package com.projecttrack.app.data.backup

import com.projecttrack.app.data.file.FileStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class BackupNonDestructiveRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var source: BackupFixture
    private lateinit var backupBytes: ByteArray

    @Before
    fun setUp() {
        runBlocking {
            source = createFixture(tempFolder.newFolder("source"))
            seedSampleData(source)
            backupBytes = exportToBytes(source)
        }
    }

    @Test
    fun restoreIntoNonEmptyAppKeepsExistingProjectsAndAttachments() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        seedExistingData(target)
        val cache = tempFolder.newFolder("cache")

        val existingProject = target.projectDao.getById(1L)
        val existingEntity = target.fileDao.getAll().single()
        val existingBytes = target.storage.storedFile(existingEntity.relativePath).readBytes()
        val existingFileCount = target.fileDao.getAll().size

        val result = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))

        assertTrue("恢复应成功：$result", result is BackupRestoreResult.Success)
        assertEquals(existingProject, target.projectDao.getById(1L))
        assertEquals(existingFileCount + 4, target.fileDao.getAll().size)
        assertEquals(1 + 2, target.projectDao.getAll().size)
        assertEquals(existingEntity, target.fileDao.getAll().first { it.id == existingEntity.id })
        assertEquals(existingBytes.toList(), target.storage.storedFile(existingEntity.relativePath).readBytes().toList())

        val restoredProjectIds = target.projectDao.getAll().filter { it.id != 1L }.map { it.id }.toSet()
        val restoredFileProjectIds = target.fileDao.getAll().filter { it.id != existingEntity.id }.map { it.projectId }.toSet()
        assertTrue(restoredFileProjectIds.all { it in restoredProjectIds })
    }

    @Test
    fun restoringSameBackupAgainAddsAnotherCopy() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        seedExistingData(target)
        val cache = tempFolder.newFolder("cache")
        val existingEntity = target.fileDao.getAll().single()

        repeat(2) {
            val result = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))
            assertTrue(result is BackupRestoreResult.Success)
        }

        assertEquals(1 + 4, target.projectDao.getAll().size)
        assertEquals(1 + 8, target.fileDao.getAll().size)
        assertEquals(1, target.fileDao.getAll().count { it.displayName == "existing.txt" })
        val newPaths = target.fileDao.getAll().map { it.relativePath }
        assertEquals(newPaths.size, newPaths.toSet().size)
        assertTrue(target.storage.exists(existingEntity.relativePath))
    }
}

/** 恢复到非空数据库但数据库写入失败时，已有数据和已有附件必须保持不变。 */
class BackupRestoreFailureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var source: BackupFixture
    private lateinit var backupBytes: ByteArray

    @Before
    fun setUp() {
        runBlocking {
            source = createFixture(tempFolder.newFolder("source"))
            seedSampleData(source)
            backupBytes = exportToBytes(source)
        }
    }

    @Test
    fun databaseFailureRollsBackPartialRestoreAndKeepsExistingData() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        seedExistingData(target)
        val cache = tempFolder.newFolder("cache")
        val projectBefore = target.projectDao.getAll()
        val fileBefore = target.fileDao.getAll()
        val bytesBefore = target.storage.storedFile(fileBefore.single().relativePath).readBytes()

        target.fileDao.failOnInsert = true
        val transactions = object : com.projecttrack.app.data.repository.TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                val projectsSnapshot = target.projectDao.projects.value
                val nodesSnapshot = target.nodeDao.nodes.value
                val notesSnapshot = target.noteDao.notes.value
                val filesSnapshot = target.fileDao.files.value
                try {
                    return block()
                } catch (error: Throwable) {
                    target.projectDao.projects.value = projectsSnapshot
                    target.nodeDao.nodes.value = nodesSnapshot
                    target.noteDao.notes.value = notesSnapshot
                    target.fileDao.files.value = filesSnapshot
                    throw error
                }
            }
        }

        val result = createImporter(target, cache, transactions).restore(ByteArrayInputStream(backupBytes))

        assertTrue(result is BackupRestoreResult.Failure)
        assertEquals(projectBefore, target.projectDao.getAll())
        assertEquals(fileBefore, target.fileDao.getAll())
        assertEquals(bytesBefore.toList(), target.storage.storedFile(fileBefore.single().relativePath).readBytes().toList())
        assertEquals(1, target.storage.listAttachments().size)
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun attachmentWriteFailureDoesNotCreateDatabaseRows() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")
        val attachmentsPath = target.storage.attachmentsDirectory()
        attachmentsPath.deleteRecursively()
        attachmentsPath.writeText("not a directory")

        val result = createImporter(target, cache).restore(ByteArrayInputStream(backupBytes))

        assertTrue(result is BackupRestoreResult.Failure)
        assertTrue(target.projectDao.getAll().isEmpty())
        assertTrue(target.fileDao.getAll().isEmpty())
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun readFailureDoesNotCreateDatabaseRows() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")

        val failingStream = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("模拟读取失败")
        }
        val result = createImporter(target, cache).restore(failingStream)

        assertTrue(result is BackupRestoreResult.Failure)
        assertTrue(target.projectDao.getAll().isEmpty())
        assertTrue(target.fileDao.getAll().isEmpty())
        assertTrue(cache.listFiles().isNullOrEmpty())
    }
}
