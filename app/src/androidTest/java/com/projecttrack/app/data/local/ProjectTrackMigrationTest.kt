package com.projecttrack.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 迁移链基础设施。
 *
 * 当前 schema 为 v1，因此没有实际 Migration；本测试验证：
 * 1. 导出的 v1 schema 可由 MigrationTestHelper 读取；
 * 2. 用 ProjectTrackMigrations.ALL 打开旧 v1 数据库时不会走破坏性重建；
 * 3. 旧库中的行在打开后仍可读取。
 *
 * 未来新增 2、3... 版本时，在此加入逐级 `runMigrationsAndValidate` 和真实迁移数据断言。
 */
@RunWith(AndroidJUnit4::class)
class ProjectTrackMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProjectTrackDatabase::class.java,
    )

    private val databaseName = "projecttrack-migration-test.db"

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .deleteDatabase(databaseName)
    }

    @Test
    fun v1SchemaOpensWithCurrentMigrationsAndPreservesData() = runBlocking {
        helper.createDatabase(databaseName, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO projects (id, name, description, createdAt, updatedAt)
                VALUES (1, 'v1 migration sample', 'synthetic', 100, 100)
                """.trimIndent(),
            )
        }

        // 未来版本加入 Migration 后，MigrationTestHelper 会在这里执行并校验 schema。
        helper.runMigrationsAndValidate(
            databaseName,
            PROJECT_TRACK_DATABASE_VERSION,
            true,
            *ProjectTrackMigrations.ALL,
        ).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.databaseBuilder(
            context,
            ProjectTrackDatabase::class.java,
            databaseName,
        )
            .addMigrations(*ProjectTrackMigrations.ALL)
            .build()
        try {
            val projects = database.projectDao().getAll()
            assertEquals(1, projects.size)
            assertEquals("v1 migration sample", projects.single().name)
        } finally {
            database.close()
        }
    }
}
