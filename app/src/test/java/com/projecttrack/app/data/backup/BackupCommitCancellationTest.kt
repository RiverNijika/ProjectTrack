package com.projecttrack.app.data.backup

import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

/**
 * 验证备份恢复的提交/取消时序。
 *
 * 使用可控 TransactionRunner 模拟“假 DAO 已写入、事务调用尚未返回”的窗口；
 * 验证的是取消语义。未使用真实 Room/SQLite，也未做 SAF 设备验证。
 */
class BackupCommitCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cancellingOuterJobAfterRestoreDaoWriteCompletesCommitAndKeepsData() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")
        val backupBytes = simpleValidBackup().toZip()

        val daoWritten = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                val result = block()
                daoWritten.complete(Unit)
                // 位于恢复器的 NonCancellable 提交临界区，外部取消不应打断它。
                releaseTransaction.await()
                return result
            }
        }
        val outcome = CompletableDeferred<BackupRestoreResult>()
        val job = launch(Dispatchers.IO) {
            outcome.complete(
                createImporter(target, cache, transactions)
                    .restore(ByteArrayInputStream(backupBytes)),
            )
        }
        withTimeout(5_000) { daoWritten.await() }

        job.cancel()
        releaseTransaction.complete(Unit)
        job.join()

        assertTrue("已提交的恢复应返回结果", outcome.isCompleted)
        val result = outcome.await()
        assertTrue("提交已完成后应按成功处理：$result", result is BackupRestoreResult.Success)
        assertEquals(1, target.projectDao.getAll().size)
        assertEquals(1, target.nodeDao.getAll().size)
        assertEquals(2, target.noteDao.getAll().size)
        assertEquals(2, target.fileDao.getAll().size)
        assertEquals("已入库附件不能因取消而删除", 2, target.storage.listAttachments().size)
        target.fileDao.getAll().forEach { file ->
            assertTrue(target.storage.exists(file.relativePath))
        }
    }

    @Test
    fun cancellingBeforeRestoreCommitCleansNewAttachmentsAndKeepsExistingData() = runBlocking {
        val target = createFixture(tempFolder.newFolder("target"))
        seedExistingData(target)
        val cache = tempFolder.newFolder("cache")
        val backupBytes = simpleValidBackup().toZip()
        val existingProject = target.projectDao.getById(1L)
        val existingFile = target.fileDao.getAll().single()
        val existingBytes = target.storage.storedFile(existingFile.relativePath).readBytes()

        val firstAttachmentCopied = CompletableDeferred<Unit>()
        val holdBeforeCommit = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            runCatching {
                createImporter(target, cache).restore(ByteArrayInputStream(backupBytes)) { progress ->
                    if (progress.stage == RestoreStage.COPYING_FILES && progress.processedFiles >= 1) {
                        firstAttachmentCopied.complete(Unit)
                        // 该挂起位于提交前的可取消区间；外层取消应打断它。
                        holdBeforeCommit.await()
                    }
                }
            }
        }
        withTimeout(5_000) { firstAttachmentCopied.await() }

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(listOf(existingProject), target.projectDao.getAll())
        assertEquals(listOf(existingFile), target.fileDao.getAll())
        assertEquals(
            existingBytes.toList(),
            target.storage.storedFile(existingFile.relativePath).readBytes().toList(),
        )
        assertEquals("提交前取消必须清理所有新复制的附件", 1, target.storage.listAttachments().size)
        assertTrue("临时解压目录应被清理", cache.listFiles().isNullOrEmpty())
    }
}
