package com.projecttrack.app.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 在应用私有临时目录中流式解压备份，并在解压过程中完成结构与路径安全检查。
 *
 * 安全边界：
 * - 只接受 manifest.json、data.json 和 attachments/<单个文件名>；
 * - 拒绝绝对路径、反斜杠、`..`、`.`、空路径段、目录条目和重复条目；
 * - 限制条目数量、JSON 大小、单个附件大小与附件总大小；
 * - 附件必须写出到目标临时目录内部，防止路径逃逸。
 *
 * 调用方负责在 finally 中删除 [targetDirectory]。
 */
object BackupZipReader {

    suspend fun extract(input: InputStream, targetDirectory: File): BackupExtraction =
        withContext(Dispatchers.IO) {
            if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                throw BackupValidationException("无法创建备份临时目录")
            }
            val attachmentsDirectory = File(targetDirectory, BackupFormat.ATTACHMENTS_DIRECTORY)
            if (!attachmentsDirectory.exists() && !attachmentsDirectory.mkdirs()) {
                throw BackupValidationException("无法创建附件临时目录")
            }

            val seenEntries = mutableSetOf<String>()
            val attachments = linkedMapOf<String, BackupAttachmentFile>()
            var manifestJson: String? = null
            var dataJson: String? = null
            var entryCount = 0
            var totalAttachmentBytes = 0L

            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > BackupFormat.MAX_ZIP_ENTRIES) {
                        throw BackupValidationException("备份条目过多，已拒绝处理")
                    }
                    val entryName = entry.name ?: throw BackupValidationException("备份中存在没有名称的条目")
                    if (!seenEntries.add(entryName)) {
                        throw BackupValidationException("备份中存在重复条目：$entryName")
                    }
                    validateEntryName(entryName)

                    when {
                        entryName == BackupFormat.MANIFEST_ENTRY -> {
                            if (manifestJson != null) {
                                throw BackupValidationException("备份中存在重复的 manifest.json")
                            }
                            manifestJson = readJsonEntry(
                                zip = zip,
                                entry = entry,
                                entryName = entryName,
                            )
                        }

                        entryName == BackupFormat.DATA_ENTRY -> {
                            if (dataJson != null) {
                                throw BackupValidationException("备份中存在重复的 data.json")
                            }
                            dataJson = readJsonEntry(
                                zip = zip,
                                entry = entry,
                                entryName = entryName,
                            )
                        }

                        entryName.startsWith(BackupFormat.ATTACHMENTS_PREFIX) -> {
                            val fileName = entryName.removePrefix(BackupFormat.ATTACHMENTS_PREFIX)
                            if (fileName.isEmpty() || fileName.contains('/')) {
                                throw BackupValidationException("备份附件路径无效：$entryName")
                            }
                            val targetFile = File(attachmentsDirectory, fileName).canonicalFile
                            if (targetFile.parentFile?.canonicalPath != attachmentsDirectory.canonicalPath) {
                                throw BackupValidationException("备份附件路径越界：$entryName")
                            }
                            if (attachments.containsKey(entryName)) {
                                throw BackupValidationException("备份中存在重复附件：$entryName")
                            }
                            val remainingTotal = BackupFormat.MAX_TOTAL_ATTACHMENT_BYTES - totalAttachmentBytes
                            val extracted = extractAttachment(
                                zip = zip,
                                entry = entry,
                                targetFile = targetFile,
                                entryName = entryName,
                                remainingTotalBytes = remainingTotal,
                            )
                            totalAttachmentBytes += extracted.sizeBytes
                            attachments[entryName] = BackupAttachmentFile(
                                archivePath = entryName,
                                file = targetFile,
                                sizeBytes = extracted.sizeBytes,
                                sha256 = extracted.sha256,
                            )
                        }

                        else -> throw BackupValidationException("备份包含未知条目：$entryName")
                    }
                    zip.closeEntry()
                }
            }

            val manifest = manifestJson ?: throw BackupValidationException("备份缺少 manifest.json")
            val data = dataJson ?: throw BackupValidationException("备份缺少 data.json")
            BackupExtraction(
                manifestJson = manifest,
                dataJson = data,
                attachments = attachments,
            )
        }

    private fun validateEntryName(name: String) {
        if (name.isBlank()) throw BackupValidationException("备份中存在空名称条目")
        if (name.endsWith("/")) throw BackupValidationException("备份中包含不支持的目录条目：$name")
        if (name.startsWith("/") || name.startsWith("\\")) {
            throw BackupValidationException("备份中存在绝对路径条目：$name")
        }
        if (name.contains('\\') || name.contains('\u0000')) {
            throw BackupValidationException("备份中存在不安全的路径条目：$name")
        }
        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw BackupValidationException("备份中存在路径穿越条目：$name")
        }
    }

    private suspend fun readJsonEntry(
        zip: ZipInputStream,
        entry: ZipEntry,
        entryName: String,
    ): String {
        if (entry.size > BackupFormat.MAX_JSON_ENTRY_BYTES) {
            throw BackupValidationException("$entryName 过大，已拒绝处理")
        }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupFormat.MAX_JSON_ENTRY_BYTES) {
                throw BackupValidationException("$entryName 过大，已拒绝处理")
            }
            output.write(buffer, 0, read)
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    private suspend fun extractAttachment(
        zip: ZipInputStream,
        entry: ZipEntry,
        targetFile: File,
        entryName: String,
        remainingTotalBytes: Long,
    ): ExtractedAttachment {
        if (entry.size > BackupFormat.MAX_ATTACHMENT_BYTES) {
            throw BackupValidationException("附件过大，已拒绝处理：$entryName")
        }
        if (remainingTotalBytes <= 0L) {
            throw BackupValidationException("备份附件总大小超出限制")
        }
        val digest = BackupHashing.newSha256()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        try {
            targetFile.outputStream().buffered().use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > BackupFormat.MAX_ATTACHMENT_BYTES) {
                        throw BackupValidationException("附件过大，已拒绝处理：$entryName")
                    }
                    if (total > remainingTotalBytes) {
                        throw BackupValidationException("备份附件总大小超出限制")
                    }
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
            if (entry.size >= 0 && entry.size != total) {
                throw BackupValidationException("附件大小与 ZIP 声明不符：$entryName")
            }
            return ExtractedAttachment(
                sizeBytes = total,
                sha256 = BackupHashing.hex(digest),
            )
        } catch (error: Exception) {
            targetFile.delete()
            throw error
        }
    }

    private data class ExtractedAttachment(
        val sizeBytes: Long,
        val sha256: String,
    )
}
