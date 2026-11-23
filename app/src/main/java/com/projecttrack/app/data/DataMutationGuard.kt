package com.projecttrack.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 应用级数据变更锁。
 *
 * 导出备份和恢复过程中会持有该锁，阻止项目、节点、记录、文件导入/删除等修改，
 * 从而保证备份元数据与附件内容来自同一份稳定状态。读取操作不加锁，界面仍可浏览。
 *
 * 注意：Mutex 不可重入。仓库层方法只在本层获取一次锁；备份导出/恢复直接访问 DAO，
 * 不会在持锁期间再调用会再次加锁的仓库方法。
 */
class DataMutationGuard {
    private val mutex = Mutex()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutex.withLock { block() }
}
