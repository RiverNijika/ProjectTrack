package com.projecttrack.app.data.backup

import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.data.DataMutationGuard
import com.projecttrack.app.data.file.FileStorage
import com.projecttrack.app.data.local.PROJECT_TRACK_DATABASE_VERSION
import com.projecttrack.app.data.local.ProjectTrackDatabase
import com.projecttrack.app.data.local.entity.FileEntity
import com.projecttrack.app.data.local.entity.NoteEntity
import com.projecttrack.app.data.local.entity.NodeEntity
import com.projecttrack.app.data.local.entity.ProjectEntity
import com.projecttrack.app.data.model.FileCategory
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * 在真实 Room/SQLite 与真实 Android 文件系统上验证备份恢复。
 * 测试使用独立 cacheDir 子目录和内存数据库，不触碰用户数据；需要设备/模拟器才能执行。
 */
@RunWith(AndroidJUnit4::class)
class BackupRoomInstrumentedTest {

    private lateinit var database: ProjectTrackDatabase
    private lateinit var storage: FileStorage
    private lateinit var filesRoot: File
    private lateinit var cacheRoot: File

    private val attachmentBytes = "instrumented attachment".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ProjectTrackDatabase::class.java,
        ).build()
        filesRoot = File(context.cacheDir, "instrumented-files-${UUID.randomUUID()}").apply { mkdirs() }
        storage = FileStorage(filesRoot).also { it.ensureDirectories() }
        cacheRoot = File(context.cacheDir, "instrumented-cache-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun restoreIntoNonEmptyDatabaseKeepsExistingDataAndRemapsIds() = runBlocking {
        val backup = seedAndExport()

        val existingProjectId = database.projectDao().insert(
            ProjectEntity(
                name = "已有项目",
                description = "恢复前存在",
                createdAt = 10L,
                updatedAt = 20L,
            ),
        )
        val existingRelativePath = "attachments/existing"
        val existingBytes = "existing".toByteArray()
        storage.storedFile(existingRelativePath).apply {
            parentFile?.mkdirs()
            writeBytes(existingBytes)
        }
        val existingFileId = database.fileDao().insert(
            FileEntity(
                projectId = existingProjectId,
                nodeId = null,
                displayName = "existing.txt",
                mimeType = "text/plain",
                extension = "txt",
                sizeBytes = existingBytes.size.toLong(),
                category = FileCategory.TEXT,
                relativePath = existingRelativePath,
                importedAt = 30L,
            ),
        )
        val existingProjectBefore = database.projectDao().getById(existingProjectId)
        val existingFileBefore = database.fileDao().getById(existingFileId)

        val projectsBefore = database.projectDao().getAll()
        val nodesBefore = database.nodeDao().getAll()
        val notesBefore = database.noteDao().getAll()
        val filesBefore = database.fileDao().getAll()
        val projectIdsBefore = projectsBefore.map { it.id }.toSet()
        val nodeIdsBefore = nodesBefore.map { it.id }.toSet()
        val noteIdsBefore = notesBefore.map { it.id }.toSet()
        val fileIdsBefore = filesBefore.map { it.id }.toSet()
        val attachmentsBefore = storage.listAttachments().size

        val result = createImporter().restore(ByteArrayInputStream(backup))
        assertTrue("恢复应成功：$result", result is BackupRestoreResult.Success)

        // 已有数据必须完全不变。
        assertEquals(existingProjectBefore, database.projectDao().getById(existingProjectId))
        assertEquals(existingFileBefore, database.fileDao().getById(existingFileId))
        assertEquals(existingBytes.toList(), storage.storedFile(existingRelativePath).readBytes().toList())
        assertEquals(projectsBefore.size + 1, database.projectDao().getAll().size)
        assertEquals(nodesBefore.size + 1, database.nodeDao().getAll().size)
        assertEquals(notesBefore.size + 1, database.noteDao().getAll().size)
        assertEquals(filesBefore.size + 1, database.fileDao().getAll().size)
        assertEquals(attachmentsBefore + 1, storage.listAttachments().size)

        // 只保留一个“新增 ID”的恢复副本，并验证所有关系指向这套新 ID。
        val restoredProject = database.projectDao().getAll().single { it.id !in projectIdsBefore }
        assertEquals("测试项目", restoredProject.name)
        val restoredNode = database.nodeDao().getAll().single { it.id !in nodeIdsBefore }
        assertEquals("测试节点", restoredNode.title)
        assertEquals(restoredProject.id, restoredNode.projectId)
        val restoredNote = database.noteDao().getAll().single { it.id !in noteIdsBefore }
        assertEquals("节点记录", restoredNote.content)
        assertEquals(restoredProject.id, restoredNote.projectId)
        assertEquals(restoredNode.id, restoredNote.nodeId)
        val restoredFile = database.fileDao().getAll().single { it.id !in fileIdsBefore }
        assertEquals("note.txt", restoredFile.displayName)
        assertEquals(restoredProject.id, restoredFile.projectId)
        assertEquals(restoredNode.id, restoredFile.nodeId)
        assertTrue(restoredFile.relativePath != "attachments/seed")
        assertTrue(storage.exists(restoredFile.relativePath))
        assertEquals(attachmentBytes.toList(), storage.storedFile(restoredFile.relativePath).readBytes().toList())
    }

    @Test
    fun realRoomTransactionFailureRollsBackRestore() = runBlocking {
        val backup = seedAndExport()
        database.projectDao().insert(
            ProjectEntity(
                name = "已有项目",
                description = "",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val projectsBefore = database.projectDao().getAll()
        val nodesBefore = database.nodeDao().getAll()
        val notesBefore = database.noteDao().getAll()
        val filesBefore = database.fileDao().getAll()
        val attachmentsBefore = storage.listAttachments()
            .map { storage.relativePathOf(it) }
            .sorted()

        val failingTransactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T =
                database.withTransaction {
                    block()
                    throw IllegalStateException("模拟数据库提交失败")
                }
        }

        val result = createImporter(failingTransactions).restore(ByteArrayInputStream(backup))

        assertTrue(result is BackupRestoreResult.Failure)
        assertEquals(projectsBefore, database.projectDao().getAll())
        assertEquals(nodesBefore, database.nodeDao().getAll())
        assertEquals(notesBefore, database.noteDao().getAll())
        assertEquals(filesBefore, database.fileDao().getAll())
        assertEquals(
            attachmentsBefore,
            storage.listAttachments().map { storage.relativePathOf(it) }.sorted(),
        )
    }

    @Test
    fun cancellingBeforeRealRoomCommitCleansNewAttachmentsAndKeepsExistingData() = runBlocking {
        val backup = seedAndExport()
        val projectsBefore = database.projectDao().getAll()
        val nodesBefore = database.nodeDao().getAll()
        val notesBefore = database.noteDao().getAll()
        val filesBefore = database.fileDao().getAll()
        val attachmentsBefore = storage.listAttachments().map { it.name }.sorted()

        val enteredBeforeCommit = CompletableDeferred<Unit>()
        val holdBeforeCommit = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) {
            runCatching {
                createImporter().restore(ByteArrayInputStream(backup)) { progress ->
                    if (progress.stage == RestoreStage.WRITING_DATABASE) {
                        enteredBeforeCommit.complete(Unit)
                        // 提交前回调位于可取消区间；取消应打断它并触发附件清理。
                        holdBeforeCommit.await()
                    }
                }
            }
        }
        withTimeout(10_000) { enteredBeforeCommit.await() }

        job.cancel()
        job.join()

        assertTrue("提交前取消应结束 Job", job.isCancelled)
        assertEquals(projectsBefore, database.projectDao().getAll())
        assertEquals(nodesBefore, database.nodeDao().getAll())
        assertEquals(notesBefore, database.noteDao().getAll())
        assertEquals(filesBefore, database.fileDao().getAll())
        assertEquals(
            "提交前取消必须清理本次新附件",
            attachmentsBefore,
            storage.listAttachments().map { it.name }.sorted(),
        )
    }

    @Test
    fun cancellingOuterJobInsideRealRoomCommitStillKeepsCommittedData() = runBlocking {
        val backup = seedAndExport()

        val projectsBefore = database.projectDao().getAll()
        val nodesBefore = database.nodeDao().getAll()
        val notesBefore = database.noteDao().getAll()
        val filesBefore = database.fileDao().getAll()
        val projectIdsBefore = projectsBefore.map { it.id }.toSet()
        val nodeIdsBefore = nodesBefore.map { it.id }.toSet()
        val noteIdsBefore = notesBefore.map { it.id }.toSet()
        val fileIdsBefore = filesBefore.map { it.id }.toSet()

        val enteredCommit = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val transactions = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T =
                database.withTransaction {
                    val result = block()
                    enteredCommit.complete(Unit)
                    // 恢复器的 NonCancellable 提交临界区应防止外部取消打断此处。
                    releaseCommit.await()
                    result
                }
        }
        val outcome = CompletableDeferred<BackupRestoreResult>()
        val thrown = AtomicReference<Throwable?>()
        val jobCompletionCause = AtomicReference<Throwable?>()
        val importer = createImporter(transactions)
        val job = launch(Dispatchers.IO) {
            try {
                outcome.complete(importer.restore(ByteArrayInputStream(backup)))
            } catch (error: Throwable) {
                thrown.set(error)
                throw error
            }
        }
        job.invokeOnCompletion { cause -> jobCompletionCause.set(cause) }
        withTimeout(10_000) { enteredCommit.await() }

        job.cancel()
        releaseCommit.complete(Unit)
        job.join()

        val failure = thrown.get()
        val dbProjects = database.projectDao().getAll()
        val dbNodes = database.nodeDao().getAll()
        val dbNotes = database.noteDao().getAll()
        val dbFiles = database.fileDao().getAll()
        val attachmentFiles = storage.listAttachments().map { it.name }.sorted()
        val diagnostics = buildString {
            appendLine("outcomeCompleted=${outcome.isCompleted}")
            appendLine("jobCancelled=${job.isCancelled}")
            appendLine("jobCompletionCause=${jobCompletionCause.get()?.stackTraceToString()}")
            appendLine("thrownType=${failure?.javaClass?.name}")
            appendLine("thrownMessage=${failure?.message}")
            appendLine("projects=${dbProjects.size} nodes=${dbNodes.size} notes=${dbNotes.size} files=${dbFiles.size}")
            appendLine("attachments=${attachmentFiles.size} filesList=${attachmentFiles}")
            if (failure != null) {
                appendLine("stacktrace:")
                appendLine(failure.stackTraceToString())
            }
        }
        Log.e("PT_COMMIT_DIAG", diagnostics)

        assertTrue("已提交的恢复应返回结果\n$diagnostics", outcome.isCompleted)
        val restoreResult = outcome.await()
        assertTrue(
            "真实 Room 提交完成后必须成功：$restoreResult; failure=${failure?.stackTraceToString()}\n$diagnostics",
            restoreResult is BackupRestoreResult.Success,
        )
        assertEquals(projectsBefore.size + 1, database.projectDao().getAll().size)
        assertEquals(nodesBefore.size + 1, database.nodeDao().getAll().size)
        assertEquals(notesBefore.size + 1, database.noteDao().getAll().size)
        assertEquals(filesBefore.size + 1, database.fileDao().getAll().size)
        assertEquals(2, storage.listAttachments().size)

        val restoredProject = database.projectDao().getAll().single { it.id !in projectIdsBefore }
        val restoredNode = database.nodeDao().getAll().single { it.id !in nodeIdsBefore }
        val restoredNote = database.noteDao().getAll().single { it.id !in noteIdsBefore }
        val restoredFile = database.fileDao().getAll().single { it.id !in fileIdsBefore }
        assertEquals(restoredProject.id, restoredNode.projectId)
        assertEquals(restoredNode.id, restoredNote.nodeId)
        assertEquals(restoredProject.id, restoredFile.projectId)
        assertEquals(restoredNode.id, restoredFile.nodeId)
        assertTrue(storage.exists(restoredFile.relativePath))
    }

    private suspend fun seedAndExport(): ByteArray {
        val projectId = database.projectDao().insert(
            ProjectEntity(
                id = 10L,
                name = "测试项目",
                description = "备份测试",
                createdAt = 100L,
                updatedAt = 200L,
            ),
        )
        val nodeId = database.nodeDao().insert(
            NodeEntity(
                id = 100L,
                projectId = projectId,
                title = "测试节点",
                description = "节点说明",
                occurredAt = 300L,
                createdAt = 250L,
            ),
        )
        database.noteDao().insert(
            NoteEntity(
                id = 1000L,
                projectId = projectId,
                nodeId = nodeId,
                content = "节点记录",
                createdAt = 350L,
                updatedAt = 360L,
            ),
        )
        storage.storedFile("attachments/seed").apply {
            parentFile?.mkdirs()
            writeBytes(attachmentBytes)
        }
        database.fileDao().insert(
            FileEntity(
                id = 2000L,
                projectId = projectId,
                nodeId = nodeId,
                displayName = "note.txt",
                mimeType = "text/plain",
                extension = "txt",
                sizeBytes = attachmentBytes.size.toLong(),
                category = FileCategory.TEXT,
                relativePath = "attachments/seed",
                importedAt = 400L,
            ),
        )

        val exporter = BackupExporter(
            projectDao = database.projectDao(),
            nodeDao = database.nodeDao(),
            noteDao = database.noteDao(),
            fileDao = database.fileDao(),
            storage = storage,
            mutationGuard = DataMutationGuard(),
            appVersionName = "0.1.0",
            appVersionCode = 1L,
            databaseVersion = PROJECT_TRACK_DATABASE_VERSION,
            clock = { 1_000L },
        )
        val output = ByteArrayOutputStream()
        val result = exporter.export(output)
        assertTrue("导出应成功：$result", result is BackupExportResult.Success)
        return output.toByteArray()
    }

    private fun createImporter(
        transactions: TransactionRunner = RoomTransactionRunner(database),
    ): BackupImporter = BackupImporter(
        projectDao = database.projectDao(),
        nodeDao = database.nodeDao(),
        noteDao = database.noteDao(),
        fileDao = database.fileDao(),
        storage = storage,
        transactions = transactions,
        mutationGuard = DataMutationGuard(),
        cacheRoot = cacheRoot,
        databaseVersion = PROJECT_TRACK_DATABASE_VERSION,
        clock = { 2_000L },
    )
}
