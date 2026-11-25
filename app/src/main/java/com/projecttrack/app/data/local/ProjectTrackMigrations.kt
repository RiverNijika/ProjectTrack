package com.projecttrack.app.data.local

import androidx.room.migration.Migration

/**
 * Room 迁移链。
 *
 * 当前数据库版本为 1，因此这里暂时为空。未来新增版本时必须逐级加入 Migration，
 * 并在 androidTest 中使用 MigrationTestHelper 验证。不得使用
 * fallbackToDestructiveMigration 或以删库重建掩盖升级问题。
 */
object ProjectTrackMigrations {
    val ALL: Array<Migration> = emptyArray()
}
