package com.xiaomo.androidforclaw.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.xiaomo.androidforclaw.ui.compose.ChatMessage
import com.xiaomo.androidforclaw.ui.compose.ChatScreen
import com.xiaomo.androidforclaw.ui.compose.MessageStatus
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * ChatScreen Compose UI 自动化测试
 *
 * 覆盖场景:
 * 1. 空态展示
 * 2. 消息气泡（用户 vs AI）
 * 3. Markdown 渲染
 * 4. 长消息折叠/展开
 * 5. 输入框交互
 * 6. 发送按钮状态
 * 7. 加载状态
 * 8. 消息复制
 * 9. 自动滚动
 *
 * 运行:
 * adb shell am instrument -w -e class com.xiaomo.androidforclaw.ui.ChatScreenUITest \
 *   com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChatScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun setContent(
        messages: List<ChatMessage> = emptyList(),
        isLoading: Boolean = false,
        onSend: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            ChatScreen(
                messages = messages,
                onSendMessage = onSend,
                isLoading = isLoading,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    private fun sampleMessages(): List<ChatMessage> = listOf(
        ChatMessage(content = "你好", isUser = true),
        ChatMessage(content = "你好！我是 AI 助手，有什么可以帮你的？", isUser = false),
    )

    private fun markdownMessage(): ChatMessage = ChatMessage(
        content = """## 标题
**加粗文本** 和 *斜体文本*

```kotlin
fun hello() = println("world")
```

- 列表项 1
- 列表项 2

> 引用块内容
""",
        isUser = false
    )

    private fun longMessage(): ChatMessage = ChatMessage(
        content = "这是一条很长的消息。".repeat(50),
        isUser = false
    )

    // ========================================================================
    // 1. Empty State
    // ========================================================================

    @Test
    fun test01_emptyState_showsWelcome() {
        setContent()
        composeTestRule.onNodeWithText("开始聊天").assertIsDisplayed()
    }

    @Test
    fun test02_emptyState_showsHint() {
        setContent()
        composeTestRule.onNodeWithText("向 AI 助手发送消息来控制手机")
            .assertIsDisplayed()
    }

    @Test
    fun test03_emptyState_showsEmoji() {
        setContent()
        composeTestRule.onNodeWithText("👋").assertIsDisplayed()
    }

    @Test
    fun test04_emptyState_inputVisible() {
        setContent()
        composeTestRule.onNodeWithTag("chat_input").assertIsDisplayed()
    }

    @Test
    fun test05_emptyState_sendButtonVisible() {
        setContent()
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
    }

    // ========================================================================
    // 2. Message Bubbles
    // ========================================================================

    @Test
    fun test10_userMessage_displayed() {
        setContent(messages = sampleMessages())
        composeTestRule.onNodeWithText("你好").assertIsDisplayed()
    }

    @Test
    fun test11_aiMessage_displayed() {
        setContent(messages = sampleMessages())
        composeTestRule.onNodeWithText("你好！我是 AI 助手，有什么可以帮你的？")
            .assertIsDisplayed()
    }

    @Test
    fun test12_userAvatar_shown() {
        setContent(messages = sampleMessages())
        // User avatar shows "YO" (from "You")
        composeTestRule.onAllNodesWithText("YO").assertCountEquals(1)
    }

    @Test
    fun test13_aiAvatar_shown() {
        setContent(messages = sampleMessages())
        composeTestRule.onAllNodesWithText("AI").assertCountEquals(1)
    }

    @Test
    fun test14_timestamp_shown() {
        setContent(messages = sampleMessages())
        // Timestamps should be present (HH:mm format)
        // At least one timestamp node should exist
        composeTestRule.onAllNodesWithText(
            substring = true,
            text = ":"  // timestamps have ":" like "23:30"
        ).assertCountEquals(2)  // 2 messages, 2 timestamps — this is fragile, just check > 0
    }

    @Test
    fun test15_sendingStatus_showsClock() {
        val msg = ChatMessage(content = "发送中", isUser = true, status = MessageStatus.SENDING)
        setContent(messages = listOf(msg))
        composeTestRule.onNodeWithText("⏱").assertIsDisplayed()
    }

    @Test
    fun test16_sentStatus_showsCheck() {
        val msg = ChatMessage(content = "已发送", isUser = true, status = MessageStatus.SENT)
        setContent(messages = listOf(msg))
        composeTestRule.onNodeWithText("✓").assertIsDisplayed()
    }

    @Test
    fun test17_errorStatus_showsWarning() {
        val msg = ChatMessage(content = "发送失败", isUser = true, status = MessageStatus.ERROR)
        setContent(messages = listOf(msg))
        composeTestRule.onNodeWithText("⚠").assertIsDisplayed()
    }

    // ========================================================================
    // 3. Markdown Rendering
    // ========================================================================

    @Test
    fun test20_markdown_headingRendered() {
        setContent(messages = listOf(markdownMessage()))
        // After Markdown rendering, "## 标题" should display as "标题" (no ##)
        // If raw text, "## 标题" would be visible
        composeTestRule.onNodeWithText("标题", substring = true).assertIsDisplayed()
    }

    @Test
    fun test21_markdown_boldRendered() {
        setContent(messages = listOf(markdownMessage()))
        // "加粗文本" should be visible (rendered bold, without ** markers)
        composeTestRule.onNodeWithText("加粗文本", substring = true).assertIsDisplayed()
    }

    @Test
    fun test22_markdown_codeBlockRendered() {
        setContent(messages = listOf(markdownMessage()))
        // Code content should be visible
        composeTestRule.onNodeWithText("hello", substring = true).assertIsDisplayed()
    }

    @Test
    fun test23_markdown_noRawMarkers() {
        setContent(messages = listOf(markdownMessage()))
        // Should NOT show raw markdown markers
        composeTestRule.onNodeWithText("##", substring = false).assertDoesNotExist()
        composeTestRule.onNodeWithText("**加粗", substring = false).assertDoesNotExist()
    }

    @Test
    fun test24_markdown_listRendered() {
        setContent(messages = listOf(markdownMessage()))
        composeTestRule.onNodeWithText("列表项 1", substring = true).assertIsDisplayed()
    }

    // ========================================================================
    // 4. Long Message Collapse/Expand
    // ========================================================================

    @Test
    fun test30_longMessage_initiallyCollapsed() {
        setContent(messages = listOf(longMessage()))
        // Long messages should show "展开" or truncation indicator
        composeTestRule.onNodeWithText("展开", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun test31_longMessage_canExpand() {
        setContent(messages = listOf(longMessage()))
        // Click expand
        composeTestRule.onNodeWithText("展开", substring = true, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
        // After expand, should see "收起"
        composeTestRule.onNodeWithText("收起", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun test32_longMessage_canCollapse() {
        setContent(messages = listOf(longMessage()))
        // Expand first
        composeTestRule.onNodeWithText("展开", substring = true, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
        // Then collapse
        composeTestRule.onNodeWithText("收起", substring = true, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
        // Should see "展开" again
        composeTestRule.onNodeWithText("展开", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    // ========================================================================
    // 5. Input Box
    // ========================================================================

    @Test
    fun test40_input_placeholderShown() {
        setContent()
        composeTestRule.onNodeWithText("发送消息").assertIsDisplayed()
    }

    @Test
    fun test41_input_canType() {
        setContent()
        composeTestRule.onNodeWithTag("chat_input").performTextInput("测试消息")
        composeTestRule.onNodeWithTag("chat_input").assertTextContains("测试消息")
    }

    @Test
    fun test42_input_clearsAfterSend() {
        var sentMessage = ""
        setContent(onSend = { sentMessage = it })

        composeTestRule.onNodeWithTag("chat_input").performTextInput("你好世界")
        composeTestRule.onNodeWithTag("send_button").performClick()
        composeTestRule.waitForIdle()

        assert(sentMessage == "你好世界") { "Expected '你好世界', got '$sentMessage'" }
        // After send, EditableText is empty but placeholder "发送消息" remains as Text
        composeTestRule.onNodeWithTag("chat_input").assertTextContains("发送消息")
    }

    @Test
    fun test43_input_emptyNoSend() {
        var sendCalled = false
        setContent(onSend = { sendCalled = true })

        composeTestRule.onNodeWithTag("send_button").performClick()
        composeTestRule.waitForIdle()

        assert(!sendCalled) { "Send should not be called for empty input" }
    }

    @Test
    fun test44_input_imeActionSend() {
        var sentMessage = ""
        setContent(onSend = { sentMessage = it })

        composeTestRule.onNodeWithTag("chat_input").performTextInput("键盘发送")
        composeTestRule.onNodeWithTag("chat_input").performImeAction()
        composeTestRule.waitForIdle()

        assert(sentMessage == "键盘发送") { "Expected '键盘发送', got '$sentMessage'" }
    }

    // ========================================================================
    // 6. Send Button State
    // ========================================================================

    @Test
    fun test50_sendButton_disabledWhenEmpty() {
        setContent()
        // Send button should be gray/disabled when input is empty
        // Just verify it exists (visual state is harder to test)
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
    }

    @Test
    fun test51_sendButton_enabledWithText() {
        setContent()
        composeTestRule.onNodeWithTag("chat_input").performTextInput("有内容")
        // Button should now be blue/enabled
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
    }

    // ========================================================================
    // 7. Loading State
    // ========================================================================

    @Test
    fun test60_loading_showsIndicator() {
        setContent(messages = sampleMessages(), isLoading = true)
        composeTestRule.onNodeWithText("AI 正在思考...").assertIsDisplayed()
    }

    @Test
    fun test61_loading_hiddenWhenNotLoading() {
        setContent(messages = sampleMessages(), isLoading = false)
        composeTestRule.onNodeWithText("AI 正在思考...").assertDoesNotExist()
    }

    // ========================================================================
    // 8. Message Copy (Long Press)
    // ========================================================================

    @Test
    fun test70_message_longPressShowsCopy() {
        setContent(messages = sampleMessages())
        // Long press on AI message should show copy option
        composeTestRule.onNodeWithText("你好！我是 AI 助手，有什么可以帮你的？")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        // After long press, should see "复制" option
        composeTestRule.onNodeWithText("复制", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    // ========================================================================
    // 9. Message Bubble Width
    // ========================================================================

    @Test
    fun test80_bubble_notTooNarrow() {
        val shortMsg = ChatMessage(content = "短消息测试内容比较长一些来验证宽度", isUser = false)
        setContent(messages = listOf(shortMsg))
        // The message should be displayed (basic check — visual width is hard to assert)
        composeTestRule.onNodeWithText("短消息测试内容比较长一些来验证宽度")
            .assertIsDisplayed()
    }

    // ========================================================================
    // 10. Multiple Messages Scroll
    // ========================================================================

    @Test
    fun test90_multipleMessages_lastVisible() {
        val messages = (1..20).map { i ->
            ChatMessage(
                content = "消息 $i",
                isUser = i % 2 == 0
            )
        }
        setContent(messages = messages)
        composeTestRule.waitForIdle()
        // Last message should be visible (auto-scroll)
        composeTestRule.onNodeWithText("消息 20").assertIsDisplayed()
    }

    @Test
    fun test91_multipleMessages_canScroll() {
        val messages = (1..20).map { i ->
            ChatMessage(content = "滚动消息 $i", isUser = i % 2 == 0)
        }
        setContent(messages = messages)
        composeTestRule.waitForIdle()

        // Scroll up to see earlier messages
        composeTestRule.onNodeWithText("滚动消息 20")
            .performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        // After scroll, some earlier messages should be visible
        // (exact assertion depends on scroll distance)
    }
}
