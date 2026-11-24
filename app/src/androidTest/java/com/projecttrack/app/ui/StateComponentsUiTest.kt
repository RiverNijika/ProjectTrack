package com.projecttrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.testutil.DeviceTestSupport
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证空状态、错误状态与按钮回调。
 *
 * 使用 debug-only 的 TestHostActivity：HyperOS 阻止 instrumentation 直接启动
 * Activity，因此通过 UiAutomation 以 shell 身份启动，再用 EmptyComposeRule 断言。
 */
@RunWith(AndroidJUnit4::class)
class StateComponentsUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun launchTestHost() {
        DeviceTestSupport.launchTestHostActivity()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("还没有项目").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emptyAndErrorStatesDisplayExpectedTextAndActions() {
        composeRule.onNodeWithText("还没有项目").assertIsDisplayed()
        composeRule.onNodeWithText("创建一个项目开始记录").assertIsDisplayed()
        composeRule.onNodeWithText("出了点问题").assertIsDisplayed()
        composeRule.onNodeWithText("读取项目失败").assertIsDisplayed()

        composeRule.onNodeWithText("新建项目").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("空操作已触发").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("空操作已触发").fetchSemanticsNodes().isNotEmpty())

        composeRule.onNodeWithText("重试").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("重试已触发").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("重试已触发").fetchSemanticsNodes().isNotEmpty())
    }
}
