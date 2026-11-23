package com.projecttrack.app.data.backup

import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.repository.ProjectRepository
import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class BackupExportConsistencyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var source: BackupFixture

    private val transactions = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        runBlocking {
            source = createFixture(tempFolder.newFolder("source"))
            seedSampleData(source)
        }
    }

    @Test
    fun exportFailsWhenAttachmentCopyIsMissing() = runBlocking {
        val missingFile = source.storage.storedFile("attachments/sample-audio")
        assertTrue(missingFile.delete())

        val result = createExporter(source).export(ByteArrayOutputStream())

        assertTrue(result is BackupExportResult.Failure)
        assertTrue((result as BackupExportResult.Failure).message.contains("缺失"))
    }

    @Test
    fun exportFailsWhenAttachmentSizeDoesNotMatchDatabase() = runBlocking {
        val target = source.storage.storedFile("attachments/sample-text")
        target.appendText("extra bytes")

        val result = createExporter(source).export(ByteArrayOutputStream())

        assertTrue(result is BackupExportResult.Failure)
        assertTrue((result as BackupExportResult.Failure).message.contains("大小"))
    }

    @Test
    fun mutationsWaitWhileExportIsRunning() = runBlocking {
        val gateReached = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        var exportResult: BackupExportResult? = null
        val output = ByteArrayOutputStream()
        val exporter = createExporter(source)
        val repository = ProjectRepository(
            projectDao = source.projectDao,
            nodeDao = source.nodeDao,
            noteDao = source.noteDao,
            fileDao = source.fileDao,
            storage = source.storage,
            transactions = transactions,
            clock = { 5_000L },
            mutationGuard = source.guard,
        )

        val exportJob = launch(Dispatchers.IO) {
            exportResult = exporter.export(output) { progress ->
                if (progress.processedFiles == 1 && !gateReached.isCompleted) {
                    gateReached.complete(Unit)
                    releaseGate.await()
                }
            }
        }
        withTimeout(5_000) { gateReached.await() }

        val mutationJob = launch(Dispatchers.IO) {
            repository.createProject("导出期间的并发新增", "")
        }
        val mutationFinishedEarly = withTimeoutOrNull(300) {
            mutationJob.join()
            true
        } ?: false
        assertFalse("导出持锁期间不应允许修改项目", mutationFinishedEarly)

        releaseGate.complete(Unit)
        exportJob.join()
        mutationJob.join()

        assertTrue("导出应成功完成：$exportResult", exportResult is BackupExportResult.Success)
        assertTrue(source.projectDao.getAll().any { it.name == "导出期间的并发新增" })
        assertTrue(output.size() > 0)
    }

    @Test
    fun exportCancellationPropagatesAndReleasesMutationLock() = runBlocking {
        val exporter = createExporter(source)
        val repository = ProjectRepository(
            projectDao = source.projectDao,
            nodeDao = source.nodeDao,
            noteDao = source.noteDao,
            fileDao = source.fileDao,
            storage = source.storage,
            transactions = transactions,
            clock = { 6_000L },
            mutationGuard = source.guard,
        )

        val result = runCatching {
            exporter.export(ByteArrayOutputStream()) { progress ->
                if (progress.processedFiles >= 1) {
                    throw CancellationException("模拟导出取消")
                }
            }
        }
        assertTrue(
            "导出取消必须向上传播 CancellationException",
            result.exceptionOrNull() is CancellationException,
        )

        // 取消后锁必须释放，后续修改可以正常完成。
        repository.createProject("取消后新增", "")
        assertTrue(source.projectDao.getAll().any { it.name == "取消后新增" })
    }
}
