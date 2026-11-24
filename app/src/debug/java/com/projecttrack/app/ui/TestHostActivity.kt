package com.projecttrack.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.projecttrack.app.ui.components.EmptyState
import com.projecttrack.app.ui.components.ErrorState
import com.projecttrack.app.ui.theme.ProjectTrackTheme

/**
 * 仅 debug 构建存在的测试宿主 Activity。
 *
 * HyperOS 会阻止 instrumentation 进程自身启动 Activity，因此 androidTest 通过
 * UiAutomation 以 shell 身份启动该 Activity，再用 EmptyComposeRule 验证空状态、
 * 错误状态及按钮回调。release 变体不包含本文件。
 */
class TestHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProjectTrackTheme {
                var emptyActionClicked by remember { mutableStateOf(false) }
                var retryClicked by remember { mutableStateOf(false) }
                Column {
                    if (emptyActionClicked) {
                        Text("空操作已触发")
                    } else {
                        EmptyState(
                            title = "还没有项目",
                            description = "创建一个项目开始记录",
                            actionLabel = "新建项目",
                            onAction = { emptyActionClicked = true },
                        )
                    }
                    if (retryClicked) {
                        Text("重试已触发")
                    } else {
                        ErrorState(
                            message = "读取项目失败",
                            onRetry = { retryClicked = true },
                        )
                    }
                }
            }
        }
    }
}
