package com.projecttrack.app.data.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class BackupValidationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun valid(): SimpleBackup = simpleValidBackup()

    private fun restoreExpectFailure(zip: ByteArray, messagePart: String? = null) = runBlocking {
        val target = createFixture(tempFolder.newFolder())
        val cache = tempFolder.newFolder()
        val result = createImporter(target, cache).restore(ByteArrayInputStream(zip))
        assertTrue("预期恢复失败，实际：$result", result is BackupRestoreResult.Failure)
        if (messagePart != null) {
            val message = (result as BackupRestoreResult.Failure).message
            assertTrue("失败信息应包含「$messagePart」，实际：$message", message.contains(messagePart))
        }
        assertTrue("失败后不应新增项目", target.projectDao.getAll().isEmpty())
        assertTrue("失败后不应新增节点", target.nodeDao.getAll().isEmpty())
        assertTrue("失败后不应新增记录", target.noteDao.getAll().isEmpty())
        assertTrue("失败后不应新增文件记录", target.fileDao.getAll().isEmpty())
        assertTrue("失败后不应留下附件副本", target.storage.listAttachments().isEmpty())
        assertTrue("失败后临时目录应被清理", cache.listFiles().isNullOrEmpty())
    }

    @Test
    fun validBackupRestoresSuccessfully() = runBlocking {
        val target = createFixture(tempFolder.newFolder())
        val cache = tempFolder.newFolder()
        val result = createImporter(target, cache).restore(ByteArrayInputStream(valid().toZip()))
        assertTrue("完整备份应通过校验并恢复：$result", result is BackupRestoreResult.Success)
        assertEquals(1, target.projectDao.getAll().size)
        assertEquals(2, target.fileDao.getAll().size)
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        val backup = valid()
        val manifestJson = BackupJson.encodeManifest(backup.manifest.copy(formatVersion = 2))
        restoreExpectFailure(backup.toZip(manifestJson = manifestJson), "不支持的备份格式版本")
    }

    @Test
    fun mismatchedDatabaseVersionIsRejected() {
        val backup = valid()
        val manifestJson = BackupJson.encodeManifest(backup.manifest.copy(databaseVersion = 99))
        restoreExpectFailure(backup.toZip(manifestJson = manifestJson), "数据库版本")
    }

    @Test
    fun missingManifestIsRejected() {
        val backup = valid()
        val dataJson = BackupJson.encodeData(backup.data)
        val zip = buildZip(
            listOf(BackupFormat.DATA_ENTRY to dataJson.toByteArray()) +
                backup.attachments.map { it.key to it.value },
        )
        restoreExpectFailure(zip, "缺少 manifest.json")
    }

    @Test
    fun missingAttachmentIsRejected() {
        restoreExpectFailure(
            valid().toZip(omitAttachments = setOf("attachments/000002")),
            "缺少附件",
        )
    }

    @Test
    fun attachmentSizeMismatchIsRejected() {
        val backup = valid()
        val changedData = backup.data.copy(
            files = backup.data.files.map { file ->
                if (file.id == 5L) file.copy(sizeBytes = file.sizeBytes + 1) else file
            },
        )
        val changedManifest = backup.manifest.copy(
            totalFileBytes = changedData.files.sumOf { it.sizeBytes },
        )
        restoreExpectFailure(
            backup.toZip(
                manifestJson = BackupJson.encodeManifest(changedManifest),
                dataJson = BackupJson.encodeData(changedData),
            ),
            "附件大小",
        )
    }

    @Test
    fun attachmentShaMismatchIsRejected() {
        val backup = valid()
        val changedData = backup.data.copy(
            files = backup.data.files.map { file ->
                if (file.id == 5L) file.copy(sha256 = "0".repeat(64)) else file
            },
        )
        restoreExpectFailure(
            backup.toZip(dataJson = BackupJson.encodeData(changedData)),
            "SHA-256",
        )
    }

    @Test
    fun corruptDataJsonIsRejected() {
        restoreExpectFailure(
            valid().toZip(dataJson = "{ 这不是合法 JSON"),
            "JSON",
        )
    }

    @Test
    fun missingRequiredFieldIsRejected() {
        val backup = valid()
        val manifestJson = BackupJson.encodeManifest(backup.manifest)
            .replaceFirst("\"fileCount\"", "\"fileCountMissing\"")
        restoreExpectFailure(backup.toZip(manifestJson = manifestJson), "缺少字段")
    }

    @Test
    fun invalidRelationshipIsRejected() {
        val backup = valid()
        val changedData = backup.data.copy(
            notes = backup.data.notes.map { note ->
                if (note.id == 3L) note.copy(nodeId = 999L) else note
            },
        )
        restoreExpectFailure(
            backup.toZip(dataJson = BackupJson.encodeData(changedData)),
            "节点",
        )
    }

    @Test
    fun manifestCountMismatchIsRejected() {
        val backup = valid()
        val manifestJson = BackupJson.encodeManifest(backup.manifest.copy(projectCount = 2))
        restoreExpectFailure(backup.toZip(manifestJson = manifestJson), "数量")
    }

    @Test
    fun pathTraversalEntryIsRejected() {
        restoreExpectFailure(
            valid().toZip(extraEntries = listOf("../evil.txt" to byteArrayOf(1, 2, 3))),
            "穿越",
        )
    }

    @Test
    fun absolutePathEntryIsRejected() {
        restoreExpectFailure(
            valid().toZip(extraEntries = listOf("/evil.txt" to byteArrayOf(1, 2, 3))),
            "绝对路径",
        )
    }

    @Test
    fun duplicateEntryIsRejected() {
        val backup = valid()
        val manifestBytes = BackupJson.encodeManifest(backup.manifest).toByteArray()
        val dataBytes = BackupJson.encodeData(backup.data).toByteArray()
        val one = backup.attachments.getValue("attachments/000001")
        val two = backup.attachments.getValue("attachments/000002")
        val zip = buildRawStoredZip(
            listOf(
                BackupFormat.MANIFEST_ENTRY to manifestBytes,
                BackupFormat.DATA_ENTRY to dataBytes,
                "attachments/000001" to one,
                "attachments/000001" to one,
                "attachments/000002" to two,
            ),
        )
        restoreExpectFailure(zip, "重复条目")
    }

    @Test
    fun unknownEntryIsRejected() {
        restoreExpectFailure(
            valid().toZip(extraEntries = listOf("random.txt" to byteArrayOf(1))),
            "未知条目",
        )
    }
}
