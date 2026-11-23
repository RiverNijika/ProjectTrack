package com.projecttrack.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.projecttrack.app.ProjectTrackApplication
import com.projecttrack.app.data.file.FileImportManager
import com.projecttrack.app.ui.navigation.ProjectTrackNavHost

/**
 * 应用根界面：导航宿主 + 全局导入进度与结果提示。
 *
 * 导入运行在应用级作用域中，因此切换页面后仍会继续，并在完成后给出成功/失败提示。
 */
@Composable
fun ProjectTrackApp() {
    val context = LocalContext.current
    val application = context.applicationContext as ProjectTrackApplication
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val importStatus by application.container.importManager.status.collectAsStateWithLifecycle()

    LaunchedEffect(importStatus) {
        val status = importStatus
        if (status is FileImportManager.Status.Finished) {
            val message = if (status.failures.isEmpty()) {
                "已导入 ${status.successCount} 个文件"
            } else {
                "导入完成：成功 ${status.successCount} 个，失败 ${status.failures.size} 个。${status.failures.first()}"
            }
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
            application.container.importManager.clearStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ProjectTrackNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
        val running = importStatus as? FileImportManager.Status.Running
        if (running != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "正在导入 ${running.completed}/${running.total}" +
                            (running.currentName?.let { "：$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
