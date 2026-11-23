package com.projecttrack.app.data.backup

import com.projecttrack.app.data.file.FileImporter
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class BackupCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val transactions = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Test
    fun cancelDuringRestoreCleansCopiedFilesAndDoesNotWriteDatabase() = runBlocking {
        val backup = simpleValidBackup()
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")
        val importer = createImporter(target, cache)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                importer.restore(ByteArrayInputStream(backup.toZip())) { progress ->
                    if (progress.stage == RestoreStage.COPYING_FILES && progress.processedFiles >= 1) {
                        throw CancellationException("模拟用户取消恢复")
                    }
                }
            }
        }

        assertTrue(target.projectDao.getAll().isEmpty())
        assertTrue(target.nodeDao.getAll().isEmpty())
        assertTrue(target.noteDao.getAll().isEmpty())
        assertTrue(target.fileDao.getAll().isEmpty())
        assertTrue(target.storage.listAttachments().isEmpty())
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun cancelDuringImportRemovesPartialFileAndPropagatesCancel() = runBlocking {
        val fixture = createFixture(tempFolder.newFolder("fixture"))
        fixture.projectDao.insert(
            ProjectEntity(
                id = 1L,
                name = "项目",
                description = "",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val importer = FileImporter(
            storage = fixture.storage,
            fileDao = fixture.fileDao,
            projectDao = fixture.projectDao,
            nodeDao = fixture.nodeDao,
            transactions = transactions,
            clock = { 1000L },
        )
        val meta = ImportFileMeta(
            displayName = "cancel.bin",
            mimeType = "application/octet-stream",
            extension = "bin",
            sizeHint = -1L,
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                importer.import(1L, null, meta) {
                    throw CancellationException("模拟用户取消导入")
                }
            }
        }

        assertTrue(fixture.fileDao.files.value.isEmpty())
        assertTrue(fixture.storage.listAttachments().isEmpty())
    }
}
