package com.projecttrack.app.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.projecttrack.app.data.backup.BackupManager
import kotlinx.coroutines.flow.StateFlow

/** 备份/恢复界面 ViewModel：状态由应用级 BackupManager 统一持有，页面切换不会丢失进度。 */
class BackupViewModel(
    private val backupManager: BackupManager,
) : ViewModel() {

    val status: StateFlow<BackupManager.Status> = backupManager.status

    fun export(uri: Uri) = backupManager.export(uri)

    fun restore(uri: Uri) = backupManager.restore(uri)

    fun cancel() = backupManager.cancel()

    fun clearStatus() = backupManager.clearStatus()
}
