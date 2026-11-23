package com.projecttrack.app.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 验证 Room schema 确实由 KSP 导出到可提交的 app/schemas 目录。
 *
 * 该测试依赖 testDebugUnitTest 之前的 kspDebugKotlin 任务；如果 schema 导出配置被移除，
 * 测试会明确失败，而不是让 schema 悄悄消失。
 */
class RoomSchemaExportTest {

    @Test
    fun versionOneSchemaIsExportedToSchemasDirectory() {
        val relativePath = "schemas/com.projecttrack.app.data.local.ProjectTrackDatabase/1.json"
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        val schema = candidates.firstOrNull { it.isFile }
        assertTrue(
            "未找到 Room schema。已尝试：" + candidates.joinToString { it.absolutePath } +
                "。请确认 exportSchema=true 且 KSP room.schemaLocation 配置存在。",
            schema != null,
        )
        val text = requireNotNull(schema).readText()
        assertTrue("schema 应包含数据库版本 1", text.contains("\"version\": 1"))
        assertTrue("schema 应包含 projects 表", text.contains("\"tableName\": \"projects\""))
        assertTrue("schema 应包含 files 表", text.contains("\"tableName\": \"files\""))
    }
}
