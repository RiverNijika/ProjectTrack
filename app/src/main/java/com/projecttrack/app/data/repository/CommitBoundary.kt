package com.projecttrack.app.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 执行最终数据库提交，并区分“提交已完整返回”与“提交前/提交中被取消”。
 *
 * Kotlin `withContext(NonCancellable)` 保护 block 本身，但当 block 已正常完成、
 * 准备把结果返回给一个已被取消的外部协程时，退出 `withContext` 仍可能抛出
 * `CancellationException`。Room 的 `withTransaction` 在另一个事务 Executor 上完成
 * 提交后同样会经过这个返回边界。
 *
 * 因此不能仅凭 `withContext` 是否抛取消判断事务是否提交：
 * - [completed] 只有在事务 block 正常返回后才写入；
 * - 若退出时收到取消但 [completed] 已有值，说明事务已返回，提交结果有效，直接返回它；
 * - 若取消发生在进入 block 前或 block 内，[completed] 为空，继续抛出，由调用方清理。
 *
 * 该函数只包裹短暂数据库提交；ZIP 解压、SHA-256/附件复制仍必须留在可取消区间。
 */
internal suspend fun <T> runCommittedDatabaseTransaction(block: suspend () -> T): T {
    var completed: Result<T>? = null
    try {
        withContext(NonCancellable) {
            completed = Result.success(block())
        }
    } catch (cancellation: CancellationException) {
        val committed = completed
        if (committed != null) return committed.getOrThrow()
        throw cancellation
    }
    return completed?.getOrThrow()
        ?: error("数据库提交未返回结果，也未抛出异常")
}
