package com.projecttrack.app.data.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.model.StoredFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * 回归测试：Application Context 外部打开必须使用 NEW_TASK。
 * 真机上曾出现 "Calling startActivity() from outside of an Activity context
 * requires the FLAG_ACTIVITY_NEW_TASK flag"。
 */
@RunWith(AndroidJUnit4::class)
class FileOpenerIntentInstrumentedTest {

    private lateinit var filesRoot: File
    private lateinit var storage: FileStorage

    @Before
    fun setUp() {
        // FileProvider 只暴露 filesDir/attachments；测试用唯一文件名，结束后单独删除。
        val context = ApplicationProvider.getApplicationContext<Context>()
        filesRoot = context.filesDir
        storage = FileStorage(filesRoot).also { it.ensureDirectories() }
    }

    @After
    fun tearDown() {
        storage.listAttachments()
            .filter { it.name.startsWith("file-opener-test-") }
            .forEach { it.delete() }
    }

    @Test
    fun viewIntentRequiresNewTaskAndReadGrant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val relativePath = "attachments/file-opener-test-${UUID.randomUUID()}.png"
        storage.storedFile(relativePath).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            storage.storedFile(relativePath),
        )
        val opener = FileOpener(context, storage)

        val intent = opener.buildViewIntent(uri, "image/png", "sample.png")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.data)
        assertEquals("image/png", intent.type)
        assertEquals("sample.png", intent.clipData?.description?.label)
    }

    @Test
    fun openMissingFileDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opener = FileOpener(context, storage)
        val file = StoredFile(
            id = 1L,
            projectId = 1L,
            nodeId = null,
            displayName = "missing.txt",
            mimeType = "text/plain",
            extension = "txt",
            sizeBytes = 1L,
            category = FileCategory.TEXT,
            relativePath = "attachments/missing.txt",
            importedAt = 1L,
        )

        assertEquals(FileOpener.Result.MissingFile, opener.open(file))
    }
}
