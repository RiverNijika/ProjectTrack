package com.projecttrack.app.data.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

/**
 * 固定 v1 兼容样本测试。
 *
 * 样本为 `app/src/test/resources/backup-v1-sample.ptbackup`，格式说明见同目录
 * `backup-v1-sample.txt`。它是人工构造的小型合成数据，不含任何用户真实数据。
 */
class BackupV1CompatibilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun fixedV1BackupRestoresThroughCurrentCompatibilityLayer() = runBlocking {
        val bytes = javaClass.getResourceAsStream("/backup-v1-sample.ptbackup")
            ?.use { it.readBytes() }
            ?: error("缺少 v1 固定样本资源")
        val target = createFixture(tempFolder.newFolder("target"))
        val cache = tempFolder.newFolder("cache")

        val result = createImporter(target, cache).restore(ByteArrayInputStream(bytes))

        assertTrue("固定 v1 样本应恢复到当前版本：$result", result is BackupRestoreResult.Success)
        val projects = target.projectDao.getAll()
        val nodes = target.nodeDao.getAll()
        val notes = target.noteDao.getAll()
        val files = target.fileDao.getAll()
        assertEquals(1, projects.size)
        assertEquals(1, nodes.size)
        assertEquals(1, notes.size)
        assertEquals(1, files.size)
        assertEquals("v1 compatibility sample", projects.single().name)
        assertEquals("Sample node", nodes.single().title)
        assertEquals("Synthetic v1 backup note", notes.single().content)
        assertEquals("sample.txt", files.single().displayName)
        assertEquals(projects.single().id, nodes.single().projectId)
        assertEquals(nodes.single().id, notes.single().nodeId)
        assertEquals(nodes.single().id, files.single().nodeId)
        assertTrue(target.storage.exists(files.single().relativePath))
        assertEquals(
            "ProjectTrack v1 compatibility sample\n",
            target.storage.storedFile(files.single().relativePath).readText(),
        )
    }

    @Test
    fun v1ReaderCanMapToAOneStepNewerCurrentDatabaseVersion() {
        val backup = simpleValidBackup()

        val decoded = BackupCompatibility.decode(
            manifestJson = BackupJson.encodeManifest(backup.manifest),
            dataJson = BackupJson.encodeData(backup.data),
            currentDatabaseVersion = 2,
        )

        assertEquals(1, decoded.manifest.formatVersion)
        assertEquals(1, decoded.manifest.databaseVersion)
        assertEquals(backup.data, decoded.data)
    }

    @Test
    fun databaseVersionNewerThanCurrentIsRejectedWithUpgradeGuidance() {
        val backup = simpleValidBackup()
        val manifestJson = BackupJson.encodeManifest(backup.manifest.copy(databaseVersion = 2))
        val error = try {
            BackupCompatibility.decode(
                manifestJson = manifestJson,
                dataJson = BackupJson.encodeData(backup.data),
                currentDatabaseVersion = 1,
            )
            null
        } catch (validation: BackupValidationException) {
            validation
        }
        assertTrue("未来数据库版本必须被拒绝", error != null)
        assertTrue(
            "应提示升级应用，实际：${error?.message}",
            error?.message?.contains("升级应用") == true,
        )
    }
}
