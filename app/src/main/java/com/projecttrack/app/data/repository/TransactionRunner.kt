package com.projecttrack.app.data.repository

import androidx.room.withTransaction
import com.projecttrack.app.data.local.ProjectTrackDatabase

/** 将数据库事务抽象出来，便于仓库层在 JVM 单元测试中替换为简单实现。 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val database: ProjectTrackDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}
