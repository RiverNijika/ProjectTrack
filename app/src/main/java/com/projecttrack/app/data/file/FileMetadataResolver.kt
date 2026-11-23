package com.projecttrack.app.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.projecttrack.app.data.model.ImportFileMeta
import java.io.InputStream

/** 通过 ContentResolver 读取系统文件选择器返回的展示名称、MIME、大小等信息。 */
class FileMetadataResolver(private val context: Context) {

    fun resolve(uri: Uri): ImportFileMeta {
        var displayName: String? = null
        var sizeHint = -1L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeHint = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // 元数据查询失败不影响导入：使用回退名称继续尝试复制。
        }

        val fallbackName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "未命名文件"
        val finalName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        return ImportFileMeta(
            displayName = finalName,
            mimeType = mimeType,
            extension = FileTypeClassifier.extensionOf(finalName),
            sizeHint = sizeHint,
        )
    }

    fun openStream(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
}
