package com.projecttrack.app.data.backup

import com.projecttrack.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

/**
 * 导出端预算测试。使用注入的小预算验证边界，不创建 100,000 个真实文件或 32 MB 数据。
 * 这些是 JVM 逻辑测试，SAF 目标清理仍属于设备验收项。
 */
class BackupExportLimitsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun dataJsonOverUtf8LimitFailsExport() = runBlocking {
        val fixture = createFixture(tempFolder.newFolder("fixture"))
        fixture.projectDao.insert(
            ProjectEntity(
                id = 1L,
                name = "大文本项目",
                // 1000 个中文字符 = 3000 UTF-8 字节，但 String.length 仅为 1000；
                // 若导出端错误地按字符数判断，会误判为可通过。
                description = "汉".repeat(1000),
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val result = createExporter(
            fixture,
            BackupExportLimits(maxJsonEntryBytes = 2048L),
        ).export(ByteArrayOutputStream())

        assertTrue("超过 JSON 字节上限的导出必须失败，实际：$result", result is BackupExportResult.Failure)
        assertTrue((result as BackupExportResult.Failure).message.contains("data.json"))
    }

    @Test
    fun manifestJsonOverLimitFailsExport() = runBlocking {
        val fixture = createFixture(tempFolder.newFolder("fixture"))

        val result = createExporter(
            fixture,
            BackupExportLimits(maxJsonEntryBytes = 16L),
        ).export(ByteArrayOutputStream())

        assertTrue("manifest 超过 JSON 上限时必须失败，实际：$result", result is BackupExportResult.Failure)
        assertTrue((result as BackupExportResult.Failure).message.contains("manifest.json"))
    }

    @Test
    fun zipEntryBudgetAllowsExactBoundaryAndRejectsOneLess() = runBlocking {
        // seedSampleData 含 4 个文件；manifest + data + 4 附件 = 6 个 ZIP 条目。
        val fixture = createFixture(tempFolder.newFolder("fixture"))
        seedSampleData(fixture)

        val allowed = createExporter(
            fixture,
            BackupExportLimits(maxZipEntries = 6),
        ).export(ByteArrayOutputStream())
        assertTrue("恰好等于条目预算时应成功，实际：$allowed", allowed is BackupExportResult.Success)

        val rejected = createExporter(
            fixture,
            BackupExportLimits(maxZipEntries = 5),
        ).export(ByteArrayOutputStream())
        assertTrue("超过条目预算 1 个即应失败，实际：$rejected", rejected is BackupExportResult.Failure)
        assertTrue((rejected as BackupExportResult.Failure).message.contains("ZIP 条目"))
    }
}
