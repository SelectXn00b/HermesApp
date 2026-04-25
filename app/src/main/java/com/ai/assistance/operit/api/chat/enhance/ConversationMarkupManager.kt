package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 *
 * This class handles the generation of standardized XML-formatted status messages, tool invocation
 * formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        private const val TAG = "ConversationMarkupManager"
        private const val STATUS_COMPLETE = "complete"
        private const val STATUS_WAIT_FOR_USER_NEED = "wait_for_user_need"

        /**
         * Creates a 'complete' status markup element.
         *
         * @return The formatted status element
         */
        fun createCompleteStatus(): String {
            return "<status type=\"$STATUS_COMPLETE\"></status>"
        }

        /**
         * Creates a 'wait for user need' status markup element. Similar to complete but doesn't
         * trigger problem analysis.
         *
         * @return The formatted status element
         */
        fun createWaitForUserNeedStatus(): String {
            return "<status type=\"$STATUS_WAIT_FOR_USER_NEED\"></status>"
        }

        /**
         * Creates an 'error' status markup element for a tool.
         *
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return createToolResultXml(
                toolName = toolName,
                status = "error",
                content = "<content><error>${errorMessage}</error></content>"
            )
        }

        /**
         * Creates a 'warning' status markup element.
         *
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }


        /**
         * Formats a tool result message for sending to the AI.
         *
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return if (result.success) {
                createToolResultXml(
                    toolName = result.toolName,
                    status = "success",
                    content = "<content>${result.result}</content>"
                )
            } else {
                createToolResultXml(
                    toolName = result.toolName,
                    status = "error",
                    content = "<content><error>${result.error ?: "Unknown error"}</error></content>"
                )
            }
        }

        /**
         * Formats a message indicating multiple tool invocations were found but only one will be
         * processed.
         *
         * @param context The context to access string resources
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(context: Context, toolName: String): String {
            return createWarningStatus(
                    context.getString(R.string.conversation_markup_multiple_tools_warning, toolName)
            )
        }

        /**
         * Creates a message for when a tool is not available.
         *
         * @param toolName The name of the unavailable tool
         * @param details Optional detailed error message
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String, details: String? = null): String {
            val errorMessage = details ?: "The tool `$toolName` is not available."
            return createToolErrorStatus(toolName, errorMessage)
        }

        /**
         * Creates a warning when tools and task completion are reported together.
         *
         * @param context The context to access string resources
         * @param toolNames The names of the tools involved
         * @return The formatted warning message
         */
        fun createToolsSkippedByCompletionWarning(context: Context, toolNames: List<String>): String {
            val uniqueNames =
                    toolNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val toolDescription =
                    if (uniqueNames.isEmpty()) {
                        context.getString(R.string.conversation_markup_tool_calls)
                    } else {
                        context.getString(R.string.conversation_markup_tools_call, uniqueNames.joinToString("`, `"))
                    }
            val message =
                    context.getString(R.string.conversation_markup_completion_with_tools_warning, toolDescription) +
                            context.getString(R.string.conversation_markup_completion_with_tools_hint)
            return createWarningStatus(message)
        }

        /**
         * Cleans task completion content by removing the completion marker and adding a completion
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with completion status
         */
        fun createTaskCompletionContent(content: String): String {
            return content.replace("<status type=\"$STATUS_COMPLETE\"></status>", "").trim() +
                    "\n" +
                    createCompleteStatus()
        }

        /**
         * Cleans wait for user need content by removing the marker and adding the appropriate
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with wait_for_user_need status
         */
        fun createWaitForUserNeedContent(content: String): String {
            return content.replace("<status type=\"$STATUS_WAIT_FOR_USER_NEED\"></status>", "").trim() +
                    "\n" +
                    createWaitForUserNeedStatus()
        }

        /**
         * Checks if content contains a task completion marker.
         *
         * @param content The content to check
         * @return True if the content contains a task completion marker
         */
        fun containsTaskCompletion(content: String): Boolean {
            return containsStatusType(content, STATUS_COMPLETE)
        }

        /**
         * Checks if content contains a wait for user need marker.
         *
         * @param content The content to check
         * @return True if the content contains a wait for user need marker
         */
        fun containsWaitForUserNeed(content: String): Boolean {
            return containsStatusType(content, STATUS_WAIT_FOR_USER_NEED)
        }

        private fun containsStatusType(content: String, statusType: String): Boolean {
            val escapedType = Regex.escape(statusType)
            val statusRegex = Regex(
                "<status\\b[^>]*\\btype\\s*=\\s*\"$escapedType\"[^>]*(?:/>|>[\\s\\S]*?</status>)",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            return statusRegex.containsMatchIn(content)
        }

        private fun createToolResultXml(toolName: String, status: String, content: String): String {
            val tagName = ChatMarkupRegex.generateRandomToolResultTagName()
            return """<$tagName name="$toolName" status="$status">$content</$tagName>""".trimIndent()
        }

    }
}
