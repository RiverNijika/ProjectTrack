package com.projecttrack.app.data.file

import android.net.Uri
import com.projecttrack.app.data.repository.FileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局文件导入协调器。
 *
 * 导入运行在应用级协程作用域中，因此旋转屏幕或离开当前页面不会取消复制、也不会重复导入。
 * 界面通过 [status] 展示处理中、成功和失败状态。
 */
class FileImportManager(
    private val metadataResolver: FileMetadataResolver,
    private val fileRepository: FileRepository,
    private val scope: CoroutineScope,
) {

    sealed interface Status {
        data object Idle : Status
        data class Running(val completed: Int, val total: Int, val currentName: String?) : Status
        data class Finished(val successCount: Int, val failures: List<String>) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var currentJob: Job? = null

    fun importFiles(projectId: Long, nodeId: Long?, uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (currentJob?.isActive == true) {
            _status.value = Status.Finished(
                successCount = 0,
                failures = listOf("已有导入任务正在进行，请稍后再试"),
            )
            return
        }
        currentJob = scope.launch {
            _status.value = Status.Running(completed = 0, total = uris.size, currentName = null)
            var successCount = 0
            val failures = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                val meta = try {
                    metadataResolver.resolve(uri)
                } catch (error: Exception) {
                    failures += "无法读取所选文件：${error.message ?: "未知错误"}"
                    _status.value = Status.Running(index + 1, uris.size, null)
                    return@forEachIndexed
                }
                _status.value = Status.Running(
                    completed = index,
                    total = uris.size,
                    currentName = meta.displayName,
                )
                when (val result = try {
                    fileRepository.importFile(projectId, nodeId, meta) { metadataResolver.openStream(uri) }
                } catch (error: Exception) {
                    null
                }) {
                    is FileImporter.Result.Success -> successCount++
                    is FileImporter.Result.Failure -> failures += "${result.displayName}：${result.message}"
                    null -> failures += "${meta.displayName}：导入失败"
                }
                _status.value = Status.Running(
                    completed = index + 1,
                    total = uris.size,
                    currentName = meta.displayName,
                )
            }
            _status.value = Status.Finished(successCount = successCount, failures = failures)
        }
    }

    fun clearStatus() {
        if (_status.value is Status.Finished) {
            _status.value = Status.Idle
        }
    }
}
