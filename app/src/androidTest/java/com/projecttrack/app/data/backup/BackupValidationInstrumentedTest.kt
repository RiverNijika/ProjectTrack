package com.projecttrack.app.data.backup

import androidx.room.Room
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
import kotlinx.coroutines.runBlocking
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 在真机 Android 文件系统 + Room 上验证损坏备份被整体拒绝且不修改数据。
 * 覆盖：缺附件、SHA-256 错误、JSON 损坏、ZIP 路径异常。
 */
@RunWith(AndroidJUnit4::class)
class BackupValidationInstrumentedTest {

    private lateinit var database: ProjectTrackDatabase
    private lateinit var storage: FileStorage
    private lateinit var filesRoot: File
    private lateinit var cacheRoot: File

    private val attachmentBytes = "validation attachment".toByteArray()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProjectTrackDatabase::class.java).build()
        filesRoot = File(context.cacheDir, "validation-files-${UUID.randomUUID()}").apply { mkdirs() }
        storage = FileStorage(filesRoot).also { it.ensureDirectories() }
        cacheRoot = File(context.cacheDir, "validation-cache-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun corruptBackupsAreRejectedWithoutChangingExistingData() = runBlocking {
        val valid = seedAndExport()
        val beforeProjects = database.projectDao().getAll()
        val beforeNodes = database.nodeDao().getAll()
        val beforeNotes = database.noteDao().getAll()
        val beforeFiles = database.fileDao().getAll()
        val beforeAttachments = storage.listAttachments().map { it.name }.sorted()

        val variants = linkedMapOf(
            "missing attachment" to rewriteZip(valid, removeEntry = { it.startsWith("attachments/") }),
            "bad sha256" to rewriteZip(
                valid,
                replaceEntry = { name ->
                    name == "data.json"
                },
                transformEntry = { name, bytes ->
                    if (name == "data.json") {
                        Regex("\"sha256\"\\s*:\\s*\"[0-9a-fA-F]{64}\"")
                            .replaceFirst(
                                bytes.toString(Charsets.UTF_8),
                                "\"sha256\": \"${"0".repeat(64)}\"",
                            )
                            .toByteArray(Charsets.UTF_8)
                    } else {
                        bytes
                    }
                },
            ),
            "corrupt json" to rewriteZip(
                valid,
                replaceEntry = { it == "data.json" },
                transformEntry = { name, bytes ->
                    if (name == "data.json") "{not valid json".toByteArray() else bytes
                },
            ),
            "path traversal" to rewriteZip(valid, extraEntry = "../evil.txt"),
        )

        for ((label, bytes) in variants) {
            val result = createImporter().restore(ByteArrayInputStream(bytes))
            assertTrue("$label 必须被拒绝，实际：$result", result is BackupRestoreResult.Failure)
            assertEquals("$label 不得新增项目", beforeProjects, database.projectDao().getAll())
            assertEquals("$label 不得新增节点", beforeNodes, database.nodeDao().getAll())
            assertEquals("$label 不得新增记录", beforeNotes, database.noteDao().getAll())
            assertEquals("$label 不得新增文件", beforeFiles, database.fileDao().getAll())
            assertEquals(
                "$label 不得残留新附件",
                beforeAttachments,
                storage.listAttachments().map { it.name }.sorted(),
            )
        }
    }

    private suspend fun seedAndExport(): ByteArray {
        val projectId = database.projectDao().insert(
            ProjectEntity(name = "验证项目", description = "校验", createdAt = 1L, updatedAt = 1L),
        )
        val nodeId = database.nodeDao().insert(
            NodeEntity(
                projectId = projectId,
                title = "验证节点",
                description = "",
                occurredAt = 2L,
                createdAt = 2L,
            ),
        )
        database.noteDao().insert(
            NoteEntity(projectId = projectId, nodeId = nodeId, content = "记录", createdAt = 3L, updatedAt = 3L),
        )
        storage.storedFile("attachments/seed").apply {
            parentFile?.mkdirs()
            writeBytes(attachmentBytes)
        }
        database.fileDao().insert(
            FileEntity(
                projectId = projectId,
                nodeId = nodeId,
                displayName = "seed.txt",
                mimeType = "text/plain",
                extension = "txt",
                sizeBytes = attachmentBytes.size.toLong(),
                category = FileCategory.TEXT,
                relativePath = "attachments/seed",
                importedAt = 4L,
            ),
        )
        val exporter = BackupExporter(
            database.projectDao(),
            database.nodeDao(),
            database.noteDao(),
            database.fileDao(),
            storage,
            DataMutationGuard(),
            "0.1.0",
            1L,
            PROJECT_TRACK_DATABASE_VERSION,
            clock = { 1_000L },
        )
        val output = ByteArrayOutputStream()
        val result = exporter.export(output)
        assertTrue("导出应成功：$result", result is BackupExportResult.Success)
        return output.toByteArray()
    }

    private fun createImporter(): BackupImporter = BackupImporter(
        database.projectDao(),
        database.nodeDao(),
        database.noteDao(),
        database.fileDao(),
        storage,
        RoomTransactionRunner(database),
        DataMutationGuard(),
        cacheRoot,
        PROJECT_TRACK_DATABASE_VERSION,
        clock = { 2_000L },
    )

    private fun rewriteZip(
        input: ByteArray,
        removeEntry: (String) -> Boolean = { false },
        replaceEntry: (String) -> Boolean = { false },
        transformEntry: (String, ByteArray) -> ByteArray = { _, bytes -> bytes },
        extraEntry: String? = null,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            ZipInputStream(ByteArrayInputStream(input)).use { source ->
                while (true) {
                    val entry = source.nextEntry ?: break
                    val bytes = source.readBytes()
                    if (!removeEntry(entry.name)) {
                        val transformed = if (replaceEntry(entry.name)) {
                            transformEntry(entry.name, bytes)
                        } else {
                            bytes
                        }
                        zip.putNextEntry(ZipEntry(entry.name))
                        zip.write(transformed)
                        zip.closeEntry()
                    }
                }
            }
            if (extraEntry != null) {
                zip.putNextEntry(ZipEntry(extraEntry))
                zip.write("evil".toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
