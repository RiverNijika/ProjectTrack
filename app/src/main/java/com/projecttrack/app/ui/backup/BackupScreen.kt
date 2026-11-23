package com.projecttrack.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projecttrack.app.data.backup.BackupFormat
import com.projecttrack.app.data.backup.BackupManager
import com.projecttrack.app.ui.AppViewModelProvider
import com.projecttrack.app.ui.components.ConfirmDialog
import com.projecttrack.app.ui.util.Formatters
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var cancelRequested by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            cancelRequested = false
            viewModel.export(uri)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }

    LaunchedEffect(status) {
        if (status !is BackupManager.Status.Running) {
            cancelRequested = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackupInfoCard()

            ActionCard(
                title = "导出完整备份",
                description = "把全部项目、节点、记录和文件副本保存为本地 ZIP 备份文件。备份文件未加密，任何拿到它的人都能读取其中的内容，请妥善保管。",
                actionLabel = "选择导出位置",
                enabled = status !is BackupManager.Status.Running,
                onClick = { exportLauncher.launch(suggestedBackupFileName()) },
            )

            ActionCard(
                title = "从备份恢复",
                description = "恢复是非破坏性的：不会覆盖或删除现有项目，而是把备份中的项目作为新副本导入并重新生成 ID。重复恢复同一备份会产生重复项目。",
                actionLabel = "选择备份文件",
                enabled = status !is BackupManager.Status.Running,
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
            )

            BackupStatusCard(
                status = status,
                cancelRequested = cancelRequested,
                onCancel = {
                    cancelRequested = true
                    viewModel.cancel()
                },
                onDismiss = viewModel::clearStatus,
            )

            BackupFormatCard()
        }
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "恢复备份",
            message = "恢复会把备份中的项目作为新的项目副本导入，不会覆盖或删除现有项目。" +
                "重复恢复同一备份可能产生重复内容。请确认备份文件来源可信，并确认不会与本机现有数据产生混淆。",
            confirmLabel = "开始恢复",
            isDestructive = false,
            onConfirm = {
                showRestoreConfirm = false
                val uri = pendingRestoreUri
                pendingRestoreUri = null
                if (uri != null) {
                    cancelRequested = false
                    viewModel.restore(uri)
                }
            },
            onDismiss = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
        )
    }
}

@Composable
private fun BackupInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本地备份，不上传",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "备份只在用户选择的位置生成，不经过账号、服务器或云同步。" +
                    "导出或恢复进行期间，项目修改、文件导入和删除会被暂时阻止，以保证数据一致性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, enabled = enabled) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun BackupStatusCard(
    status: BackupManager.Status,
    cancelRequested: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (status) {
        is BackupManager.Status.Idle -> Unit
        is BackupManager.Status.Running -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text = status.stage, style = MaterialTheme.typography.titleSmall)
                }
                if (status.totalFiles > 0) {
                    Spacer(Modifier.height(12.dp))
                    val fraction = (
                        status.processedFiles.toFloat() / status.totalFiles.toFloat()
                        ).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "文件 ${status.processedFiles}/${status.totalFiles} · " +
                            "${Formatters.fileSize(status.processedBytes)} / " +
                            Formatters.fileSize(status.totalBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !cancelRequested,
                ) {
                    Text(if (cancelRequested) "正在取消…" else "取消任务")
                }
                if (cancelRequested) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "正在取消并清理未完成的部分，请稍候。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is BackupManager.Status.Finished -> ResultCard(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            title = "操作完成",
            message = status.summary,
            onDismiss = onDismiss,
        )

        is BackupManager.Status.Failed -> ResultCard(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            title = "操作失败",
            message = status.message,
            extra = status.cleanupNote,
            onDismiss = onDismiss,
        )

        is BackupManager.Status.Canceled -> ResultCard(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = "操作已取消",
            message = "未完成的导出文件会被清理，恢复不会修改现有数据。",
            extra = status.cleanupNote,
            onDismiss = onDismiss,
        )

        is BackupManager.Status.Rejected -> ResultCard(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = "暂时无法开始",
            message = status.message,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ResultCard(
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    title: String,
    message: String,
    extra: String? = null,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = textColor)
            Spacer(Modifier.height(6.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = textColor)
            if (!extra.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = extra, style = MaterialTheme.typography.bodySmall, color = textColor)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun BackupFormatCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "备份格式与限制", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "格式：ProjectTrackBackup v1（ZIP 容器，扩展名 .ptbackup）。" +
                    "包含 manifest.json、data.json 和 attachments/。每个附件都带大小与 SHA-256 校验；" +
                    "未知或不受支持的格式版本、数据库版本会被拒绝，不会做部分恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun suggestedBackupFileName(): String {
    val timestamp = LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT),
    )
    return "ProjectTrack-$timestamp${BackupFormat.FILE_EXTENSION}"
}
