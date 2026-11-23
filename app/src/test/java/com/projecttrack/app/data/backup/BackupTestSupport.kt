package com.projecttrack.app.data.backup

import com.projecttrack.app.data.DataMutationGuard
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.FileCategory
import com.projecttrack.app.data.repository.TransactionRunner
import com.projecttrack.app.testutil.FakeFileDao
import com.projecttrack.app.testutil.FakeNodeDao
import com.projecttrack.app.testutil.FakeNoteDao
import com.projecttrack.app.testutil.FakeProjectDao
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class PassthroughTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

internal data class BackupFixture(
    val storage: FileStorage,
    val projectDao: FakeProjectDao,
    val nodeDao: FakeNodeDao,
    val noteDao: FakeNoteDao,
    val fileDao: FakeFileDao,
    val guard: DataMutationGuard,
)

internal const val SAMPLE_APP_VERSION_NAME = "0.1.0"
internal const val SAMPLE_APP_VERSION_CODE = 1L
internal const val SAMPLE_DATABASE_VERSION = 1

internal val SAMPLE_AUDIO_BYTES = ByteArray(1024) { (it % 251).toByte() }
internal val SAMPLE_TEXT_BYTES = "备份文本内容\n第二行 UTF-8 中文".toByteArray(Charsets.UTF_8)
internal val SAMPLE_IMAGE_BYTES = ByteArray(512) { (255 - it % 256).toByte() }
internal val SAMPLE_UNLINKED_BYTES = "unlinked attachment bytes".toByteArray(Charsets.UTF_8)

internal fun createFixture(root: File): BackupFixture {
    val storage = FileStorage(File(root, "files"))
    storage.ensureDirectories()
    return BackupFixture(
        storage = storage,
        projectDao = FakeProjectDao(),
        nodeDao = FakeNodeDao(),
        noteDao = FakeNoteDao(),
        fileDao = FakeFileDao(),
        guard = DataMutationGuard(),
    )
}

internal fun createExporter(
    fixture: BackupFixture,
    limits: BackupExportLimits = BackupExportLimits(),
): BackupExporter = BackupExporter(
    projectDao = fixture.projectDao,
    nodeDao = fixture.nodeDao,
    noteDao = fixture.noteDao,
    fileDao = fixture.fileDao,
    storage = fixture.storage,
    mutationGuard = fixture.guard,
    appVersionName = SAMPLE_APP_VERSION_NAME,
    appVersionCode = SAMPLE_APP_VERSION_CODE,
    databaseVersion = SAMPLE_DATABASE_VERSION,
    limits = limits,
    clock = { 9_000L },
)

internal fun createImporter(
    fixture: BackupFixture,
    cacheRoot: File,
    transactionRunner: TransactionRunner = PassthroughTransactionRunner(),
): BackupImporter = BackupImporter(
    projectDao = fixture.projectDao,
    nodeDao = fixture.nodeDao,
    noteDao = fixture.noteDao,
    fileDao = fixture.fileDao,
    storage = fixture.storage,
    transactions = transactionRunner,
    mutationGuard = fixture.guard,
    cacheRoot = cacheRoot,
    databaseVersion = SAMPLE_DATABASE_VERSION,
    clock = { 20_000L },
)

/**
 * 种子数据：两个项目、三个节点、四类记录（含未关联记录）、四个文件（含未关联文件），
 * 每个文件都有实际的附件内容。
 */
internal suspend fun seedSampleData(fixture: BackupFixture) {
    fixture.projectDao.insert(
        ProjectEntity(
            id = 10L,
            name = "项目甲",
            description = "项目甲简介",
            createdAt = 100L,
            updatedAt = 200L,
        ),
    )
    fixture.projectDao.insert(
        ProjectEntity(
            id = 20L,
            name = "项目乙",
            description = "项目乙简介",
            createdAt = 300L,
            updatedAt = 400L,
        ),
    )
    fixture.nodeDao.insert(
        NodeEntity(
            id = 100L,
            projectId = 10L,
            title = "设计确认",
            description = "设计已确认",
            occurredAt = 150L,
            createdAt = 140L,
        ),
    )
    fixture.nodeDao.insert(
        NodeEntity(
            id = 101L,
            projectId = 10L,
            title = "开发暂停",
            description = "等待外部依赖",
            occurredAt = 350L,
            createdAt = 340L,
        ),
    )
    fixture.nodeDao.insert(
        NodeEntity(
            id = 200L,
            projectId = 20L,
            title = "测试完成",
            description = "",
            occurredAt = 500L,
            createdAt = 490L,
        ),
    )
    fixture.noteDao.insert(
        NoteEntity(
            id = 1000L,
            projectId = 10L,
            nodeId = 100L,
            content = "节点记录：设计文档已归档",
            createdAt = 160L,
            updatedAt = 170L,
        ),
    )
    fixture.noteDao.insert(
        NoteEntity(
            id = 1001L,
            projectId = 10L,
            nodeId = null,
            content = "项目记录：整体节奏正常",
            createdAt = 210L,
            updatedAt = 220L,
        ),
    )
    fixture.noteDao.insert(
        NoteEntity(
            id = 1002L,
            projectId = 10L,
            nodeId = null,
            content = "未关联记录：原节点已删除",
            createdAt = 230L,
            updatedAt = 240L,
        ),
    )
    fixture.noteDao.insert(
        NoteEntity(
            id = 1003L,
            projectId = 20L,
            nodeId = 200L,
            content = "乙节点记录",
            createdAt = 510L,
            updatedAt = 520L,
        ),
    )

    writeAttachment(fixture, "attachments/sample-audio", SAMPLE_AUDIO_BYTES)
    writeAttachment(fixture, "attachments/sample-text", SAMPLE_TEXT_BYTES)
    writeAttachment(fixture, "attachments/sample-image", SAMPLE_IMAGE_BYTES)
    writeAttachment(fixture, "attachments/sample-unlinked", SAMPLE_UNLINKED_BYTES)

    fixture.fileDao.insert(
        FileEntity(
            id = 5000L,
            projectId = 10L,
            nodeId = 100L,
            displayName = "meeting.mp3",
            mimeType = "audio/mpeg",
            extension = "mp3",
            sizeBytes = SAMPLE_AUDIO_BYTES.size.toLong(),
            category = FileCategory.AUDIO,
            relativePath = "attachments/sample-audio",
            importedAt = 600L,
        ),
    )
    fixture.fileDao.insert(
        FileEntity(
            id = 5001L,
            projectId = 10L,
            nodeId = null,
            displayName = "notes.txt",
            mimeType = "text/plain",
            extension = "txt",
            sizeBytes = SAMPLE_TEXT_BYTES.size.toLong(),
            category = FileCategory.TEXT,
            relativePath = "attachments/sample-text",
            importedAt = 700L,
        ),
    )
    fixture.fileDao.insert(
        FileEntity(
            id = 5002L,
            projectId = 20L,
            nodeId = 200L,
            displayName = "screen.png",
            mimeType = "image/png",
            extension = "png",
            sizeBytes = SAMPLE_IMAGE_BYTES.size.toLong(),
            category = FileCategory.OTHER,
            relativePath = "attachments/sample-image",
            importedAt = 800L,
        ),
    )
    fixture.fileDao.insert(
        FileEntity(
            id = 5003L,
            projectId = 10L,
            nodeId = null,
            displayName = "unlinked.bin",
            mimeType = "application/octet-stream",
            extension = "bin",
            sizeBytes = SAMPLE_UNLINKED_BYTES.size.toLong(),
            category = FileCategory.OTHER,
            relativePath = "attachments/sample-unlinked",
            importedAt = 900L,
        ),
    )
}

internal suspend fun seedExistingData(fixture: BackupFixture) {
    fixture.storage.attachmentsDirectory().mkdirs()
    val existingBytes = "existing attachment content".toByteArray(Charsets.UTF_8)
    writeAttachment(fixture, "attachments/existing", existingBytes)
    fixture.projectDao.insert(
        ProjectEntity(
            id = 1L,
            name = "已有项目",
            description = "恢复前已存在的项目",
            createdAt = 10L,
            updatedAt = 20L,
        ),
    )
    fixture.fileDao.insert(
        FileEntity(
            id = 9L,
            projectId = 1L,
            nodeId = null,
            displayName = "existing.txt",
            mimeType = "text/plain",
            extension = "txt",
            sizeBytes = existingBytes.size.toLong(),
            category = FileCategory.TEXT,
            relativePath = "attachments/existing",
            importedAt = 30L,
        ),
    )
}

internal fun writeAttachment(fixture: BackupFixture, relativePath: String, bytes: ByteArray) {
    val target = fixture.storage.storedFile(relativePath)
    target.parentFile?.mkdirs()
    target.writeBytes(bytes)
}

internal suspend fun exportToBytes(fixture: BackupFixture): ByteArray {
    val output = ByteArrayOutputStream()
    val result = createExporter(fixture).export(output)
    val failure = result as? BackupExportResult.Failure
    check(failure == null) { "导出失败：${failure?.message}" }
    return output.toByteArray()
}

/** 手工构造 ZIP，用于构造损坏、缺字段、路径穿越等恶意/异常备份。 */
internal fun buildZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

/**
 * 构造仅含本地文件头（stored 未压缩）的 ZIP 字节流。
 * ZipOutputStream 禁止重复条目，因此重复条目安全测试需要手工生成原始 ZIP。
 */
internal fun buildRawStoredZip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val output = ByteArrayOutputStream()
    entries.forEach { (name, bytes) ->
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val crc = java.util.zip.CRC32().apply { update(bytes) }.value
        output.writeU32(0x04034b50L)
        output.writeU16(20)
        output.writeU16(0)
        output.writeU16(0)
        output.writeU16(0)
        output.writeU16(0)
        output.writeU32(crc)
        output.writeU32(bytes.size.toLong())
        output.writeU32(bytes.size.toLong())
        output.writeU16(nameBytes.size)
        output.writeU16(0)
        output.write(nameBytes)
        output.write(bytes)
    }
    return output.toByteArray()
}

private fun ByteArrayOutputStream.writeU16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.writeU32(value: Long) {
    repeat(4) { index ->
        write(((value ushr (8 * index)) and 0xFF).toInt())
    }
}

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(hex[value ushr 4])
            append(hex[value and 0x0F])
        }
    }
}

internal data class SimpleBackup(
    val manifest: BackupManifest,
    val data: BackupData,
    val attachments: Map<String, ByteArray>,
)

/** 结构完整、校验可通过的最小备份（用于恢复链路测试与恶意变体基线）。 */
internal fun simpleValidBackup(): SimpleBackup {
    val attachmentOne = "attachment one".toByteArray(Charsets.UTF_8)
    val attachmentTwo = ByteArray(64) { it.toByte() }
    val data = BackupData(
        projects = listOf(
            BackupProject(
                id = 1L,
                name = "最小项目",
                description = "描述",
                createdAt = 10L,
                updatedAt = 20L,
            ),
        ),
        nodes = listOf(
            BackupNode(
                id = 2L,
                projectId = 1L,
                title = "节点",
                description = "节点说明",
                occurredAt = 30L,
                createdAt = 40L,
            ),
        ),
        notes = listOf(
            BackupNote(
                id = 3L,
                projectId = 1L,
                nodeId = 2L,
                content = "节点记录",
                createdAt = 50L,
                updatedAt = 60L,
            ),
            BackupNote(
                id = 4L,
                projectId = 1L,
                nodeId = null,
                content = "未关联记录",
                createdAt = 70L,
                updatedAt = 80L,
            ),
        ),
        files = listOf(
            backupFileRecord(
                id = 5L,
                archivePath = "attachments/000001",
                displayName = "one.txt",
                sizeBytes = attachmentOne.size.toLong(),
                sha256 = sha256Hex(attachmentOne),
                nodeId = 2L,
            ),
            backupFileRecord(
                id = 6L,
                archivePath = "attachments/000002",
                displayName = "two.bin",
                sizeBytes = attachmentTwo.size.toLong(),
                sha256 = sha256Hex(attachmentTwo),
                nodeId = null,
            ),
        ),
    )
    val manifest = BackupManifest(
        formatName = BackupFormat.FORMAT_NAME,
        formatVersion = BackupFormat.FORMAT_VERSION,
        appVersionName = SAMPLE_APP_VERSION_NAME,
        appVersionCode = SAMPLE_APP_VERSION_CODE,
        databaseVersion = SAMPLE_DATABASE_VERSION,
        createdAt = 99L,
        projectCount = data.projects.size,
        nodeCount = data.nodes.size,
        noteCount = data.notes.size,
        fileCount = data.files.size,
        totalFileBytes = data.files.sumOf { it.sizeBytes },
    )
    return SimpleBackup(
        manifest = manifest,
        data = data,
        attachments = mapOf(
            "attachments/000001" to attachmentOne,
            "attachments/000002" to attachmentTwo,
        ),
    )
}

private fun backupFileRecord(
    id: Long,
    archivePath: String,
    displayName: String,
    sizeBytes: Long,
    sha256: String,
    nodeId: Long?,
): BackupFileRecord = BackupFileRecord(
    id = id,
    projectId = 1L,
    nodeId = nodeId,
    displayName = displayName,
    mimeType = "text/plain",
    extension = displayName.substringAfterLast('.', ""),
    sizeBytes = sizeBytes,
    category = FileCategory.TEXT,
    importedAt = 100L,
    archivePath = archivePath,
    sha256 = sha256,
)

internal fun SimpleBackup.toZip(
    manifestJson: String = BackupJson.encodeManifest(manifest),
    dataJson: String = BackupJson.encodeData(data),
    extraEntries: List<Pair<String, ByteArray>> = emptyList(),
    omitAttachments: Set<String> = emptySet(),
): ByteArray {
    val entries = mutableListOf<Pair<String, ByteArray>>()
    entries += BackupFormat.MANIFEST_ENTRY to manifestJson.toByteArray(Charsets.UTF_8)
    entries += BackupFormat.DATA_ENTRY to dataJson.toByteArray(Charsets.UTF_8)
    attachments.forEach { (path, bytes) ->
        if (path !in omitAttachments) entries += path to bytes
    }
    entries += extraEntries
    return buildZip(entries)
}
