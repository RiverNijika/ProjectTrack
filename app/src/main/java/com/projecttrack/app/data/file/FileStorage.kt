package com.projecttrack.app.data.file

import com.projecttrack.app.data.file.FileTypeClassifier.extensionOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

/** 已分配的磁盘存储位置。展示名称与磁盘内部名称分离，保证同名文件互不覆盖。 */
data class StoredTarget(
    val displayName: String,
    val internalName: String,
    val relativePath: String,
)

/** 文本预览结果。解码失败必须显式返回失败，不能把乱码当作成功。 */
sealed interface TextPreviewResult {
    data class Success(val text: String, val truncated: Boolean) : TextPreviewResult
    data object InvalidUtf8 : TextPreviewResult
    data object Missing : TextPreviewResult
    data class Failure(val message: String) : TextPreviewResult
}

/**
 * 应用私有目录中的文件存储。
 *
 * 所有导入文件复制到 `filesDir/attachments/` 下，数据库只保存相对路径。
 * 复制使用流式读写，不会把整个文件读入内存。
 */
class FileStorage(private val filesRoot: File) {

    fun attachmentsDirectory(): File = File(filesRoot, ATTACHMENTS_DIRECTORY)

    fun ensureDirectories() {
        attachmentsDirectory().mkdirs()
    }

    fun storedFile(relativePath: String): File = File(filesRoot, relativePath)

    fun exists(relativePath: String): Boolean = storedFile(relativePath).isFile

    /**
     * 分配一个唯一的磁盘文件名。内部名称使用“时间戳 + 随机串 + 扩展名”，
     * 与展示名称完全分离；同名导入不会覆盖已有文件。
     */
    fun createStoredTarget(
        displayName: String,
        now: Long = System.currentTimeMillis(),
        uuid: String = UUID.randomUUID().toString(),
    ): StoredTarget {
        ensureDirectories()
        val extension = extensionOf(displayName).filter { it.isLetterOrDigit() }.take(12)
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        val randomPart = uuid.replace("-", "").lowercase(Locale.ROOT).take(12)
        val internalName = "${now}_$randomPart$suffix"
        return StoredTarget(
            displayName = displayName,
            internalName = internalName,
            relativePath = "$ATTACHMENTS_DIRECTORY/$internalName",
        )
    }

    /**
     * 流式复制输入流到目标相对路径。失败时删除不完整文件并抛出异常。
     * 返回实际写入的字节数。
     */
    suspend fun copyFromStream(relativePath: String, openStream: () -> InputStream?): Long =
        withContext(Dispatchers.IO) {
            val target = storedFile(relativePath)
            target.parentFile?.mkdirs()
            var totalBytes = 0L
            try {
                val input = openStream() ?: throw IOException("无法打开源文件")
                input.use { source ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                totalBytes += read
                            }
                        }
                    }
                }
                totalBytes
            } catch (throwable: Throwable) {
                target.delete()
                throw throwable
            }
        }

    suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val target = storedFile(relativePath)
        !target.exists() || target.delete()
    }

    /**
     * 读取文本预览。最多读取 [maxBytes] 字节：
     * - 未截断时严格按 UTF-8 解码，失败返回 [TextPreviewResult.InvalidUtf8]；
     * - 截断时对截断位置做宽松解码，并提示“仅显示开头部分”。
     */
    suspend fun readTextPreview(
        relativePath: String,
        maxBytes: Long = MAX_TEXT_PREVIEW_BYTES,
    ): TextPreviewResult = withContext(Dispatchers.IO) {
        val target = storedFile(relativePath)
        if (!target.isFile) return@withContext TextPreviewResult.Missing
        try {
            val bytes = target.inputStream().buffered().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (total <= maxBytes) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val remaining = maxBytes + 1 - total
                    if (remaining <= 0) break
                    val toWrite = minOf(read.toLong(), remaining).toInt()
                    output.write(buffer, 0, toWrite)
                    total += toWrite
                }
                output.toByteArray()
            }
            val truncated = bytes.size > maxBytes
            val slice = if (truncated) bytes.copyOf(maxBytes.toInt()) else bytes
            val text = if (truncated) {
                String(slice, Charsets.UTF_8)
            } else {
                decodeUtf8Strict(slice) ?: return@withContext TextPreviewResult.InvalidUtf8
            }
            TextPreviewResult.Success(text = stripBom(text), truncated = truncated)
        } catch (error: IOException) {
            TextPreviewResult.Failure(error.message ?: "读取文件失败")
        } catch (error: SecurityException) {
            TextPreviewResult.Failure("没有读取该文件的权限")
        }
    }

    /** 列出 attachments 目录下的所有文件，用于孤立文件清理。 */
    fun listAttachments(): List<File> =
        attachmentsDirectory().listFiles()?.filter { it.isFile }.orEmpty()

    fun relativePathOf(file: File): String =
        file.relativeTo(filesRoot).invariantSeparatorsPath

    /**
     * 删除不被数据库引用的孤立文件。只清理超过 [minAgeMillis] 的文件，
     * 避免误删正在复制、尚未写入数据库的文件。
     */
    suspend fun deleteOrphanFiles(
        referencedPaths: Set<String>,
        minAgeMillis: Long = ORPHAN_MIN_AGE_MILLIS,
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        listAttachments().forEach { file ->
            val age = now - file.lastModified()
            if (age < minAgeMillis) return@forEach
            val relativePath = relativePathOf(file)
            if (relativePath !in referencedPaths && file.delete()) {
                deleted++
            }
        }
        deleted
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun stripBom(text: String): String =
        if (text.startsWith('\uFEFF')) text.substring(1) else text

    companion object {
        const val ATTACHMENTS_DIRECTORY = "attachments"

        /** 文本预览上限 256 KB：超过时只显示开头并明确提示，避免超大文本拖垮界面。 */
        const val MAX_TEXT_PREVIEW_BYTES = 256L * 1024L
        const val ORPHAN_MIN_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
