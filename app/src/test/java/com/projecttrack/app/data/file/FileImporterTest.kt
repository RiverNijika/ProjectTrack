package com.projecttrack.app.data.file

import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.repository.TransactionRunner
import com.projecttrack.app.testutil.FakeFileDao
import com.projecttrack.app.testutil.FakeProjectDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException

class FileImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: FileStorage
    private lateinit var fileDao: FakeFileDao
    private lateinit var projectDao: FakeProjectDao
    private lateinit var importer: FileImporter

    private val passthroughTransactions = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        runBlocking {
            storage = FileStorage(tempFolder.newFolder("files"))
            fileDao = FakeFileDao()
            projectDao = FakeProjectDao()
            projectDao.insert(
                ProjectEntity(
                    id = 1L,
                    name = "测试项目",
                    description = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            importer = FileImporter(
                storage = storage,
                fileDao = fileDao,
                projectDao = projectDao,
                transactions = passthroughTransactions,
                clock = { 1000L },
            )
        }
    }

    private fun meta(name: String, mime: String?) = ImportFileMeta(
        displayName = name,
        mimeType = mime,
        extension = FileTypeClassifier.extensionOf(name),
        sizeHint = -1L,
    )

    @Test
    fun successfulImportWritesFileAndDatabaseRow() = runBlocking {
        val result = importer.import(
            projectId = 1L,
            nodeId = null,
            meta = meta("notes.json", "application/json"),
        ) {
            ByteArrayInputStream("{\"a\":1}".toByteArray())
        }

        assertTrue(result is FileImporter.Result.Success)
        val stored = (result as FileImporter.Result.Success).file
        assertEquals("notes.json", stored.displayName)
        assertEquals(FileCategory.TEXT, stored.category)
        assertEquals(1, fileDao.files.value.size)
        assertTrue(storage.exists(stored.relativePath))
        assertEquals(7L, stored.sizeBytes)
        assertEquals(1000L, projectDao.projects.value.single().updatedAt)
    }

    @Test
    fun duplicateDisplayNamesDoNotOverwriteEachOther() = runBlocking {
        val first = importer.import(1L, null, meta("报告.pdf", "application/pdf")) {
            ByteArrayInputStream("first".toByteArray())
        }
        val second = importer.import(1L, null, meta("报告.pdf", "application/pdf")) {
            ByteArrayInputStream("second-content".toByteArray())
        }

        assertTrue(first is FileImporter.Result.Success)
        assertTrue(second is FileImporter.Result.Success)
        val firstFile = (first as FileImporter.Result.Success).file
        val secondFile = (second as FileImporter.Result.Success).file

        assertNotEquals(firstFile.relativePath, secondFile.relativePath)
        assertEquals(2, fileDao.files.value.size)
        assertTrue(storage.exists(firstFile.relativePath))
        assertTrue(storage.exists(secondFile.relativePath))
        assertEquals("first", storage.storedFile(firstFile.relativePath).readText())
        assertEquals("second-content", storage.storedFile(secondFile.relativePath).readText())
        assertEquals("报告.pdf", firstFile.displayName)
        assertEquals("报告.pdf", secondFile.displayName)
    }

    @Test
    fun copyFailureLeavesNeitherFileNorDatabaseRow() = runBlocking {
        val result = importer.import(1L, 99L, meta("broken.bin", "application/octet-stream")) {
            throw IOException("模拟读取失败")
        }

        assertTrue(result is FileImporter.Result.Failure)
        assertEquals(0, fileDao.files.value.size)
        assertEquals(0, storage.listAttachments().size)
    }

    @Test
    fun databaseFailureRollsBackCopiedFile() = runBlocking {
        fileDao.failOnInsert = true

        val result = importer.import(1L, null, meta("config.yaml", "application/yaml")) {
            ByteArrayInputStream("key: value".toByteArray())
        }

        assertTrue(result is FileImporter.Result.Failure)
        assertEquals(0, fileDao.files.value.size)
        assertEquals(0, storage.listAttachments().size)
    }

    @Test
    fun unsupportedFormatsAreStillImportedAsOther() = runBlocking {
        val result = importer.import(1L, null, meta("deck.pptx", "application/vnd.ms-powerpoint")) {
            ByteArrayInputStream(ByteArray(16) { 0x01 })
        }

        assertTrue(result is FileImporter.Result.Success)
        assertEquals(
            FileCategory.OTHER,
            (result as FileImporter.Result.Success).file.category,
        )
        assertFalse(storage.listAttachments().isEmpty())
    }
}
