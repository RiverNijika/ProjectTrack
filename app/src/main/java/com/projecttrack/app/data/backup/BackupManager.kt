package com.projecttrack.app.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * 应用级备份/恢复协调器。
 *
 * 任务运行在应用级作用域中，页面切换或旋转不会中断；状态集中在 [status] 中供界面展示。
 * 用户取消时会取消真实协程任务，并在清理完临时输出后才进入“已取消”状态。
 */
class BackupManager(
    private val context: Context,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val scope: CoroutineScope,
) {

    enum class Operation {
        EXPORT,
        RESTORE,
    }

    sealed interface Status {
        data object Idle : Status

        data class Running(
            val operation: Operation,
            val stage: String,
            val processedFiles: Int = 0,
            val totalFiles: Int = 0,
            val processedBytes: Long = 0L,
            val totalBytes: Long = 0L,
        ) : Status

        data class Finished(
            val operation: Operation,
            val summary: String,
        ) : Status

        data class Failed(
            val operation: Operation,
            val message: String,
            val cleanupNote: String? = null,
        ) : Status

        data class Canceled(
            val operation: Operation,
            val cleanupNote: String? = null,
        ) : Status

        data class Rejected(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var currentJob: Job? = null

    fun export(uri: Uri) {
        launchOperation(Operation.EXPORT) { runExport(uri) }
    }

    fun restore(uri: Uri) {
        launchOperation(Operation.RESTORE) { runRestore(uri) }
    }

    fun cancel() {
        // 不在这里把状态改成“已取消”：必须等真实任务结束、清理完成后由任务本身更新状态。
        currentJob?.cancel()
    }

    fun clearStatus() {
        when (_status.value) {
            is Status.Finished, is Status.Failed, is Status.Canceled, is Status.Rejected ->
                _status.value = Status.Idle
            else -> Unit
        }
    }

    private fun launchOperation(operation: Operation, block: suspend () -> Unit) {
        if (currentJob?.isActive == true) {
            _status.value = Status.Rejected("已有备份或恢复任务正在进行，请等待其完成")
            return
        }
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        job.invokeOnCompletion { cause ->
            // 协程在真正开始前就被取消时，也要把状态从“正在取消”收敛为“已取消”。
            if (cause is CancellationException && _status.value is Status.Running) {
                _status.value = Status.Canceled(operation)
            }
        }
        currentJob = job
        job.start()
    }

    private suspend fun runExport(uri: Uri) {
        _status.value = Status.Running(Operation.EXPORT, "准备导出备份")
        var outputStarted = false
        try {
            val outputStream = openExportOutputStream(uri)
            outputStarted = true
            val result = try {
                exporter.export(outputStream) { progress ->
                    _status.value = Status.Running(
                        operation = Operation.EXPORT,
                        stage = "正在导出文件",
                        processedFiles = progress.processedFiles,
                        totalFiles = progress.totalFiles,
                        processedBytes = progress.processedBytes,
                        totalBytes = progress.totalBytes,
                    )
                }
            } finally {
                // 导出器会关闭 ZipOutputStream；这里再关闭一次以覆盖异常路径。
                // 若导出已成功而关闭流报错，不能让已完成的备份被误报为失败。
                runCatching { outputStream.close() }
            }

            when (result) {
                is BackupExportResult.Success -> {
                    _status.value = Status.Finished(
                        operation = Operation.EXPORT,
                        summary = "备份导出成功：${result.projectCount} 个项目、${result.nodeCount} 个节点、" +
                            "${result.fileCount} 个文件（${formatBytes(result.totalFileBytes)}）。",
                    )
                }

                is BackupExportResult.Failure -> {
                    val cleanupNote = if (outputStarted) deleteIncompleteOutput(uri) else null
                    _status.value = Status.Failed(
                        operation = Operation.EXPORT,
                        message = result.message,
                        cleanupNote = cleanupNote,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            val cleanupNote = if (outputStarted) {
                withContext(NonCancellable) { deleteIncompleteOutput(uri) }
            } else {
                null
            }
            _status.value = Status.Canceled(Operation.EXPORT, cleanupNote)
            throw cancellation
        } catch (error: Exception) {
            val cleanupNote = if (outputStarted) {
                withContext(NonCancellable) { deleteIncompleteOutput(uri) }
            } else {
                null
            }
            val message = if (error is SafeTruncationUnsupportedException) {
                error.message ?: "当前保存位置不支持安全截断写入，请重新选择位置"
            } else {
                "导出失败：${error.message ?: "写入备份时出错"}"
            }
            _status.value = Status.Failed(
                operation = Operation.EXPORT,
                message = message,
                cleanupNote = cleanupNote,
            )
        }
    }

    private suspend fun runRestore(uri: Uri) {
        _status.value = Status.Running(Operation.RESTORE, "正在读取备份")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法读取所选备份文件")
            val result = try {
                importer.restore(input) { progress ->
                    _status.value = Status.Running(
                        operation = Operation.RESTORE,
                        stage = restoreStageLabel(progress.stage),
                        processedFiles = progress.processedFiles,
                        totalFiles = progress.totalFiles,
                        processedBytes = progress.processedBytes,
                        totalBytes = progress.totalBytes,
                    )
                }
            } finally {
                // 恢复成功返回后即使关闭输入流出错，也不能把已提交的恢复误报为失败。
                runCatching { input.close() }
            }
            when (result) {
                is BackupRestoreResult.Success -> {
                    _status.value = Status.Finished(
                        operation = Operation.RESTORE,
                        summary = "恢复成功：新增 ${result.projectCount} 个项目、${result.nodeCount} 个节点、" +
                            "${result.noteCount} 条记录、${result.fileCount} 个文件。现有项目未被覆盖。",
                    )
                }

                is BackupRestoreResult.Failure -> {
                    _status.value = Status.Failed(
                        operation = Operation.RESTORE,
                        message = result.message,
                        cleanupNote = result.cleanupWarning,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            _status.value = Status.Canceled(Operation.RESTORE)
            throw cancellation
        } catch (error: Exception) {
            _status.value = Status.Failed(
                operation = Operation.RESTORE,
                message = "恢复失败：${error.message ?: "读取备份时出错"}",
            )
        }
    }

    /**
     * 只为导出打开支持“安全截断”的 SAF 输出流：优先 rwt，其次 wt。
     * “w” 模式不保证截断已有文档，可能留下旧文件尾部内容，因此不再作为回退。
     * 如果提供方不接受 rwt/wt，返回明确错误，提示用户新建空白文件或更换位置。
     */
    private fun openExportOutputStream(uri: Uri): OutputStream {
        for (mode in listOf("rwt", "wt")) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, mode)
                if (stream != null) return stream
            } catch (_: FileNotFoundException) {
                // 尝试下一个安全模式。
            } catch (_: IllegalArgumentException) {
                // 某些提供方用 IllegalArgumentException 表示不支持的打开模式。
            } catch (_: UnsupportedOperationException) {
                // 同上；SecurityException 等真实权限错误继续向上抛出。
            }
        }
        throw SafeTruncationUnsupportedException()
    }

    private class SafeTruncationUnsupportedException : IOException(
        "当前保存位置不支持安全的截断写入（需要 rwt 或 wt 模式），旧文件尾部可能残留。 " +
            "请新建空白文件作为备份目标，或选择其他保存位置。",
    )

    private suspend fun deleteIncompleteOutput(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            if (deletedRows > 0) {
                null
            } else {
                "目标位置可能残留未完成的备份文件，请手动删除。"
            }
        } catch (error: Exception) {
            "目标位置可能残留未完成的备份文件，请手动删除。"
        }
    }

    private fun restoreStageLabel(stage: RestoreStage): String = when (stage) {
        RestoreStage.VALIDATING -> "正在校验备份结构与 SHA-256"
        RestoreStage.COPYING_FILES -> "正在复制附件"
        RestoreStage.WRITING_DATABASE -> "正在写入项目数据"
        RestoreStage.COMPLETED -> "正在完成恢复"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kilo = bytes / 1024.0
        if (kilo < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f KB", kilo)
        val mega = kilo / 1024.0
        if (mega < 1024.0) return String.format(java.util.Locale.ROOT, "%.1f MB", mega)
        return String.format(java.util.Locale.ROOT, "%.2f GB", mega / 1024.0)
    }
}
