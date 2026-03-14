/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: Kotlin extensions.
 */
package com.xiaomo.androidforclaw.ext

import android.widget.TextView
import com.xiaomo.androidforclaw.core.MyApplication
import com.tencent.mmkv.MMKV
import io.noties.markwon.Markwon


val markwon by lazy {
    Markwon.create(MyApplication.application) // 确保你有 Application 的实例
}


fun TextView.setMarkdownText(content: String) {
    markwon.setMarkdown(this, content)
}

fun String.removeMarkdownMark(): String {
    // 检查是否以 "```markdown" 开头
    if (startsWith("```markdown")) {
        // 移除头部的 "```markdown" 和尾部的 "```"
        // 注意：这里假设尾部只有一个 "```" 并且它位于字符串的末尾
        val startIndex = "```markdown".length
        val endIndex = lastIndexOf("```")

        // 检查 endIndex 是否有效，以避免 IndexOutOfBoundsException
        if (endIndex != -1 && endIndex > startIndex) {
            // 返回移除头部和尾部标记后的字符串
            return substring(startIndex, endIndex)
        } else {
            // 如果没有找到尾部标记或者尾部标记位置不正确，则可能说明字符串格式有误
            // 这里可以返回原始字符串或者抛出一个异常，取决于你的需求
            // 这里我们选择返回原始字符串并附带一个警告信息（实际开发中应该使用日志而不是打印）
            println("Warning: No valid closing '```' found in the instruction string.")
            return this // 或者你可以选择抛出异常
        }
    } else {
        // 如果不以 "```markdown" 开头，则直接返回原始字符串（或者根据需求处理）
        return this // 或者你可以选择抛出一个异常，表示输入格式不正确
    }
}

fun AndroidForClawMMKV(): MMKV = MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, "AndroidForClaw")!!

val mmkv by lazy { AndroidForClawMMKV() }


