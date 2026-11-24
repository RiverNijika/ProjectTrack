package com.projecttrack.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.projecttrack.app.testutil.DeviceTestSupport
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Compose 主流程验收：
 * 创建项目、节点、节点记录、返回导航、备份入口与删除清理。
 *
 * 测试使用带随机后缀的独立测试数据，不删除设备上其他项目。
 * 需要设备/模拟器；没有设备时仅参与编译。
 *
 * HyperOS/MIUI 会阻止 instrumentation 进程发起的后台 Activity 启动。这里使用
 * UiAutomation 以 shell 身份启动 MainActivity，再用 EmptyComposeRule 操作界面，
 * 不修改系统设置。
 */
@RunWith(AndroidJUnit4::class)
class ProjectFlowUiTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun launchApp() {
        DeviceTestSupport.launchMainActivity()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("本地项目进度跟踪").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickBackButton() {
        composeRule.onNodeWithContentDescription("返回").performClick()
    }

    private fun waitForContentDescription(description: String, timeoutMillis: Long = 15_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun createProjectNodeAndNoteThenNavigateBackThroughBackupEntryAndDeleteTestProject() {
        val suffix = UUID.randomUUID().toString().take(8)
        val projectName = "仪器测试项目-$suffix"
        val nodeTitle = "阻塞节点-$suffix"
        val noteContent = "节点记录-$suffix"

        // 备份入口与返回导航。
        waitForText("备份与恢复")
        composeRule.onAllNodesWithText("备份与恢复").onFirst().performClick()
        waitForText("导出完整备份")
        composeRule.onNodeWithText("导出完整备份").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
        composeRule.onNodeWithText("选择导出位置").assertIsDisplayed()
        clickBackButton()
        // 备份页底部也包含 “ProjectTrackBackup”，必须用项目列表独有的副标题确认返回。
        waitForText("本地项目进度跟踪")
        assertTrue(composeRule.onAllNodesWithText("备份与恢复").fetchSemanticsNodes().isNotEmpty())

        // 有项目时 FAB 的文字可能不进入语义树，使用 contentDescription 定位。
        waitForContentDescription("新建项目")
        composeRule.onNodeWithContentDescription("新建项目").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(projectName)
        composeRule.onNodeWithText("保存").performClick()
        waitForText(projectName)

        composeRule.onNodeWithText(projectName).performClick()
        waitForText("添加节点")
        composeRule.onAllNodesWithText("添加节点").onFirst().performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2
        }
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(nodeTitle)
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextInput("阻塞情况说明")
        composeRule.onNodeWithText("保存节点").performClick()
        waitForText(nodeTitle)

        // 项目详情的时间线卡片与“当前状态”卡片都可能显示节点标题，选择最后一个可点击卡片。
        composeRule.onAllNodesWithText(nodeTitle).onLast().performClick()
        waitForText("添加记录")
        composeRule.onAllNodesWithText("添加记录").onFirst().performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(noteContent)
        composeRule.onNodeWithText("保存记录").performClick()
        waitForText(noteContent)
        // 节点页可滚动；保存后笔记可能在视口外，滚动后再断言可见。
        composeRule.onNodeWithText(noteContent).performScrollTo().assertIsDisplayed()

        // 返回导航：节点详情 -> 项目详情（使用页面顶栏返回按钮）。
        clickBackButton()
        waitForText(projectName)

        // 清理本测试创建的项目；只删除随机命名的测试项目。
        composeRule.onNodeWithContentDescription("项目操作").performClick()
        composeRule.onNodeWithText("删除项目").performClick()
        composeRule.onAllNodesWithText("删除项目").onLast().performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(projectName).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText(projectName).fetchSemanticsNodes().isEmpty())
    }
}
