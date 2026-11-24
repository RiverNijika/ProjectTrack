package com.projecttrack.app.testutil

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * 设备测试辅助：部分厂商 ROM（本机 HyperOS）会阻止 instrumentation 进程发起后台
 * Activity 启动。使用 UiAutomation 以 shell 身份启动目标 Activity，可绕过这一厂商
 * 限制而不修改系统设置或应用权限。
 */
object DeviceTestSupport {

    fun runShellCommand(command: String): String {
        val parcel = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(parcel).use { input ->
            input.reader().readText()
        }
    }

    fun launchMainActivity() {
        runShellCommand("am start -W -n com.projecttrack.app/.MainActivity")
    }

    fun launchTestHostActivity() {
        runShellCommand("am start -W -n com.projecttrack.app/.ui.TestHostActivity")
    }
}
