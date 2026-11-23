package com.projecttrack.app.data.file

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
import java.io.File
import java.io.IOException

class FileStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: FileStorage

    @Before
    fun setUp() {
        storage = FileStorage(tempFolder.newFolder("files"))
    }

    @Test
    fun sameDisplayNameGetsDistinctInternalNames() {
        val first = storage.createStoredTarget("报告.pdf")
        val second = storage.createStoredTarget("报告.pdf")

        assertNotEquals(first.relativePath, second.relativePath)
        assertEquals("报告.pdf", first.displayName)
        assertEquals("报告.pdf", second.displayName)
        assertTrue(first.relativePath.endsWith(".pdf"))
        assertTrue(second.relativePath.endsWith(".pdf"))
    }

    @Test
    fun copyStreamsContentAndReturnsByteCount() = runBlocking {
        val target = storage.createStoredTarget("sample.txt")
        val content = "hello projecttrack".toByteArray()

        val copied = storage.copyFromStream(target.relativePath) {
            ByteArrayInputStream(content)
        }

        assertEquals(content.size.toLong(), copied)
        assertTrue(storage.exists(target.relativePath))
        assertEquals("hello projecttrack", storage.storedFile(target.relativePath).readText())
    }

    @Test
    fun failedCopyLeavesNoPartialFile() = runBlocking {
        val target = storage.createStoredTarget("broken.bin")

        val result = runCatching {
            storage.copyFromStream(target.relativePath) {
                throw IOException("模拟读取失败")
            }
        }

        assertTrue(result.isFailure)
        assertFalse(storage.exists(target.relativePath))
    }

    @Test
    fun textPreviewReadsUtf8Content() = runBlocking {
        val target = storage.createStoredTarget("note.txt")
        storage.storedFile(target.relativePath).writeText("项目进度：正常\n第二行")

        val result = storage.readTextPreview(target.relativePath)

        assertTrue(result is TextPreviewResult.Success)
        val success = result as TextPreviewResult.Success
        assertTrue(success.text.contains("项目进度"))
        assertFalse(success.truncated)
    }

    @Test
    fun invalidUtf8IsReportedInsteadOfGarbage() = runBlocking {
        val target = storage.createStoredTarget("bad.txt")
        storage.storedFile(target.relativePath).writeBytes(byteArrayOf(0xC3.toByte(), 0x28))

        val result = storage.readTextPreview(target.relativePath)

        assertTrue(result is TextPreviewResult.InvalidUtf8)
    }

    @Test
    fun largeTextIsTruncatedWithExplicitFlag() = runBlocking {
        val target = storage.createStoredTarget("big.log")
        storage.storedFile(target.relativePath).writeText("abcdefghij".repeat(20))

        val result = storage.readTextPreview(target.relativePath, maxBytes = 10L)

        assertTrue(result is TextPreviewResult.Success)
        val success = result as TextPreviewResult.Success
        assertTrue(success.truncated)
        assertEquals(10, success.text.length)
    }

    @Test
    fun missingTextFileReturnsMissing() = runBlocking {
        assertEquals(
            TextPreviewResult.Missing,
            storage.readTextPreview("attachments/not-exists.txt"),
        )
    }

    @Test
    fun orphanCleanupDeletesOnlyOldUnreferencedFiles() = runBlocking {
        val now = 1_000_000_000L
        val referenced = storage.createStoredTarget("keep.txt")
        val orphan = storage.createStoredTarget("orphan.txt")
        val freshUnreferenced = storage.createStoredTarget("fresh.txt")
        listOf(referenced, orphan, freshUnreferenced).forEach { target ->
            storage.storedFile(target.relativePath).writeText("x")
        }
        val old = now - 100_000L
        storage.storedFile(referenced.relativePath).setLastModified(old)
        storage.storedFile(orphan.relativePath).setLastModified(old)

        val deleted = storage.deleteOrphanFiles(
            referencedPaths = setOf(referenced.relativePath),
            minAgeMillis = 60_000L,
            now = now,
        )

        assertEquals(1, deleted)
        assertTrue(storage.exists(referenced.relativePath))
        assertFalse(storage.exists(orphan.relativePath))
        assertTrue(storage.exists(freshUnreferenced.relativePath))
    }

    @Test
    fun storedFileUsesRelativePathInsideFilesRoot() {
        val target = storage.createStoredTarget("a.bin")
        val expected: File = File(tempFolder.root, "files/${target.relativePath}")
        assertEquals(expected.canonicalPath, storage.storedFile(target.relativePath).canonicalPath)
    }
}
