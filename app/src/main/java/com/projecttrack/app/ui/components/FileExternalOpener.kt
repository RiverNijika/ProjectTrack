package com.projecttrack.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.projecttrack.app.ProjectTrackApplication
import com.projecttrack.app.data.file.FileOpener
import com.projecttrack.app.data.model.FileRow
import kotlinx.coroutines.launch

/**
 * 返回一个使用 FileProvider 打开文件并展示失败提示的处理器。
 */
@Composable
fun rememberFileExternalOpener(snackbarHostState: SnackbarHostState): (FileRow) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as ProjectTrackApplication
    return remember(application, snackbarHostState) {
        { row ->
            val message = when (val result = application.container.fileOpener.open(row.file)) {
                FileOpener.Result.Launched -> null
                FileOpener.Result.NoHandler -> "没有可以打开该文件的应用"
                FileOpener.Result.MissingFile -> "文件不存在，可能已被删除"
                is FileOpener.Result.Failed -> "打开失败：${result.message}"
            }
            if (message != null) {
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
}
