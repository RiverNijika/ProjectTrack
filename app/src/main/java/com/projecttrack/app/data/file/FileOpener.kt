package com.projecttrack.app.data.file

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.projecttrack.app.data.model.StoredFile

/**
 * 通过 FileProvider 把私有目录中的文件以只读临时授权的方式交给外部应用打开。
 * 所有失败情况都返回明确结果，由界面提示，不会导致崩溃。
 */
class FileOpener(
    private val context: Context,
    private val storage: FileStorage,
) {

    sealed interface Result {
        data object Launched : Result
        data object NoHandler : Result
        data object MissingFile : Result
        data class Failed(val message: String) : Result
    }

    fun open(file: StoredFile): Result {
        val target = storage.storedFile(file.relativePath)
        if (!target.isFile) return Result.MissingFile
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            val mimeType = resolveMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(file.displayName, uri)
            }
            context.startActivity(intent)
            Result.Launched
        } catch (_: ActivityNotFoundException) {
            Result.NoHandler
        } catch (error: Exception) {
            Result.Failed(error.message ?: "无法打开文件")
        }
    }

    /** 为应用内媒体播放提供 content:// URI；文件不存在时返回 null。 */
    fun contentUri(file: StoredFile): Uri? {
        val target = storage.storedFile(file.relativePath)
        if (!target.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
        }.getOrNull()
    }

    private fun resolveMimeType(file: StoredFile): String {
        val declared = file.mimeType?.takeIf {
            it.isNotBlank() && it != "application/octet-stream"
        }
        if (declared != null) return declared
        val fromExtension = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension)
        return fromExtension ?: "*/*"
    }

    @Suppress("unused")
    private fun buildUri(file: StoredFile): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        storage.storedFile(file.relativePath),
    )
}
