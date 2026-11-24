package com.projecttrack.app.data.backup

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 真机 SAF 截断验证：
 * 对同一个 provider 文档 URI 先写入 2 MB，再用 "rwt" 写入 1 KB，确认旧尾部被截断。
 * 这验证的是设备 provider 对 safely-truncating 模式的实际支持；UI 的 CreateDocument
 * 在同名时可能自动改名，因此不能总是通过保存对话框构造“覆盖已有文件”。
 */
@RunWith(AndroidJUnit4::class)
class SafTruncationInstrumentedTest {

    @Test
    fun rwtModeTruncatesExistingDocument() {
        assumeTrue("MediaStore Downloads 需要 API 29+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "pt-truncate-${UUID.randomUUID()}.zip")
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/ProjectTrackTest",
            )
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw AssertionError("无法创建测试文档")
        try {
            val large = ByteArray(2 * 1024 * 1024) { 1 }
            val small = ByteArray(1024) { 2 }

            resolver.openOutputStream(uri, "rwt")!!.use { output ->
                output.write(large)
            }
            resolver.openOutputStream(uri, "rwt")!!.use { output ->
                output.write(small)
            }
            val read = resolver.openInputStream(uri)!!.use { it.readBytes() }
            assertEquals("rwt 必须截断旧内容", small.size, read.size)
            assertArrayEquals(small, read)
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
