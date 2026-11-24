package com.projecttrack.app.data.file

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.data.local.ProjectTrackDatabase
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.ImportFileMeta
import com.projecttrack.app.data.repository.RoomTransactionRunner
import com.projecttrack.app.data.repository.TransactionRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 文件导入的提交/取消边界，使用真实 Room 和真实 Android 文件系统。
 * 回归重点：Room 事务返回后退出 NonCancellable 时收到外部取消，不能删除已入库附件。
 */
@RunWith(AndroidJUnit4::class)
class FileImportRoomInstrumentedTest {

    private lateinit var database: ProjectTrackDatabase
    private lateinit var storage: FileStorage
    private lateinit var filesRoot: File

    private val bytes = "file-import-race".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProjectTrackDatabase::class.java).build()
        filesRoot = File(context.cacheDir, "file-import-${UUID.randomUUID()}").apply { mkdirs() }
        storage = FileStorage(filesRoot).also { it.ensureDirectories() }
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
    }

    @Test
    fun cancellingOuterJobAfterDaoWriteStillCompletesCommitAndKeepsFile() = runBlocking {
        val projectId = database.projectDao().insert(
            ProjectEntity(name = "文件导入项目", description = "", createdAt = 1L, updatedAt = 1L),
        )
        val enteredCommit = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T =
                database.withTransaction {
                    val result = block()
                    enteredCommit.complete(Unit)
                    releaseCommit.await()
                    result
                }
        }
        val importer = FileImporter(
            storage = storage,
            fileDao = database.fileDao(),
            projectDao = database.projectDao(),
            nodeDao = database.nodeDao(),
            transactions = transactions,
            clock = { 1000L },
        )
        val outcome = CompletableDeferred<FileImporter.Result>()
        val thrown = AtomicReference<Throwable?>()
        val job = launch(Dispatchers.IO) {
            try {
                outcome.complete(
                    importer.import(projectId, null, meta("race.txt")) {
                        ByteArrayInputStream(bytes)
                    },
                )
            } catch (error: Throwable) {
                thrown.set(error)
                throw error
            }
        }
        withTimeout(10_000) { enteredCommit.await() }

        job.cancel()
        releaseCommit.complete(Unit)
        job.join()

        val failure = thrown.get()
        val diagnostics = buildString {
            appendLine("outcomeCompleted=${outcome.isCompleted}")
            appendLine("thrown=${failure?.stackTraceToString()}")
            appendLine("files=${database.fileDao().getAll()}")
            appendLine("attachments=${storage.listAttachments().map { it.name }}")
        }
        assertTrue("提交已完成后导入必须返回结果\n$diagnostics", outcome.isCompleted)
        val result = outcome.await()
        assertTrue("提交已完成后必须成功：$result\n$diagnostics", result is FileImporter.Result.Success)
        val entity = database.fileDao().getAll().single()
        assertEquals(bytes.size.toLong(), entity.sizeBytes)
        assertTrue("已入库附件不能被删除", storage.exists(entity.relativePath))
        assertEquals(bytes.toList(), storage.storedFile(entity.relativePath).readBytes().toList())
    }

    @Test
    fun cancellingBeforeCommitCleansAttachmentAndLeavesNoDatabaseRow() = runBlocking {
        val projectId = database.projectDao().insert(
            ProjectEntity(name = "文件导入项目", description = "", createdAt = 1L, updatedAt = 1L),
        )
        val importer = FileImporter(
            storage = storage,
            fileDao = database.fileDao(),
            projectDao = database.projectDao(),
            nodeDao = database.nodeDao(),
            transactions = RoomTransactionRunner(database),
            clock = { 1000L },
        )
        val readStarted = CountDownLatch(1)
        val continueRead = CountDownLatch(1)
        val blockingStream = object : InputStream() {
            private var firstRead = true

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
                importer.import(projectId, null, meta("pre-cancel.txt")) { blockingStream }
            }
        }
        assertTrue("应开始复制附件", readStarted.await(5, TimeUnit.SECONDS))

        job.cancel()
        continueRead.countDown()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue("提交前取消不得写入文件记录", database.fileDao().getAll().isEmpty())
        assertTrue("提交前取消必须清理附件", storage.listAttachments().isEmpty())
    }

    private fun meta(name: String) = ImportFileMeta(
        displayName = name,
        mimeType = "text/plain",
        extension = "txt",
        sizeHint = -1L,
    )
}
