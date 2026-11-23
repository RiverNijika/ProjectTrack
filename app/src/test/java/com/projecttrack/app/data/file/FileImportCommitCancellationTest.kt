package com.projecttrack.app.data.file

import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.repository.TransactionRunner
import com.projecttrack.app.testutil.FakeFileDao
import com.projecttrack.app.testutil.FakeNodeDao
import com.projecttrack.app.testutil.FakeProjectDao
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
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 验证文件导入的提交/取消时序。
 *
 * 这些测试使用可控 TransactionRunner 模拟“DAO 已写入、事务调用尚未返回”的窗口，
 * 验证的是代码的取消语义，不等同于真实 Room 竞争或设备验证。
 */
class FileImportCommitCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun meta(name: String) = ImportFileMeta(
        displayName = name,
        mimeType = "text/plain",
        extension = "txt",
        sizeHint = -1L,
    )

    @Test
    fun cancellingOuterJobAfterDaoWriteCompletesCommitAndKeepsFile() = runBlocking {
        val storage = FileStorage(tempFolder.newFolder("files"))
        val fileDao = FakeFileDao()
        val projectDao = FakeProjectDao()
        val nodeDao = FakeNodeDao()
        projectDao.insert(
            ProjectEntity(
                id = 1L,
                name = "项目",
                description = "",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val daoWritten = CompletableDeferred<Unit>()
        val releaseTransaction = CompletableDeferred<Unit>()
        val transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T {
                val result = block()
                daoWritten.complete(Unit)
                // 该挂起位于导入器的 NonCancellable 提交临界区中，
                // 外部 Job 取消不应打断它；测试释放后事务正常返回。
                releaseTransaction.await()
                return result
            }
        }
        val importer = FileImporter(
            storage = storage,
            fileDao = fileDao,
            projectDao = projectDao,
            nodeDao = nodeDao,
            transactions = transactions,
            clock = { 1000L },
        )

        val outcome = CompletableDeferred<FileImporter.Result>()
        val job = launch(Dispatchers.IO) {
            outcome.complete(
                importer.import(1L, null, meta("race.txt")) {
                    ByteArrayInputStream("race-data".toByteArray())
                },
            )
        }
        withTimeout(5_000) { daoWritten.await() }

        // 模拟提交返回前外部取消。
        job.cancel()
        releaseTransaction.complete(Unit)
        job.join()

        assertTrue("已提交的导入应返回结果", outcome.isCompleted)
        val result = outcome.await()
        assertTrue("提交已完成后应按成功处理：$result", result is FileImporter.Result.Success)
        val entity = fileDao.files.value.single()
        assertEquals("race.txt", entity.displayName)
        assertTrue("已入库附件不能被删除", storage.exists(entity.relativePath))
        assertEquals(
            "race-data",
            storage.storedFile(entity.relativePath).readText(),
        )
    }

    @Test
    fun cancellingBeforeCommitCleansAttachmentAndLeavesNoDatabaseRow() = runBlocking {
        val storage = FileStorage(tempFolder.newFolder("files"))
        val fileDao = FakeFileDao()
        val projectDao = FakeProjectDao()
        val nodeDao = FakeNodeDao()
        projectDao.insert(
            ProjectEntity(
                id = 1L,
                name = "项目",
                description = "",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
        val importer = FileImporter(
            storage = storage,
            fileDao = fileDao,
            projectDao = projectDao,
            nodeDao = nodeDao,
            transactions = transactions,
            clock = { 1000L },
        )

        val readStarted = CountDownLatch(1)
        val continueRead = CountDownLatch(1)
        val blockingStream = object : InputStream() {
            private var firstRead = true
            private val bytes = "pre-commit-data".toByteArray()

            override fun read(): Int = throw UnsupportedOperationException("使用 read(byte[])")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (firstRead) {
                    firstRead = false
                    val count = minOf(length, bytes.size)
                    bytes.copyInto(buffer, offset, 0, count)
                    readStarted.countDown()
                    return count
                }
                continueRead.await()
                return -1
            }
        }

        val job = launch(Dispatchers.IO) {
            runCatching {
                importer.import(1L, null, meta("cancelled.txt")) { blockingStream }
            }
        }
        assertTrue("应开始复制附件", readStarted.await(5, TimeUnit.SECONDS))

        // 复制完成后、进入最终提交前取消外层任务。
        job.cancel()
        continueRead.countDown()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue("提交前取消不得写入文件记录", fileDao.files.value.isEmpty())
        assertTrue("提交前取消必须清理已复制附件", storage.listAttachments().isEmpty())
    }
}
