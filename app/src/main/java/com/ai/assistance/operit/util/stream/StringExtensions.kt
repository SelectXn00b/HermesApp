package com.ai.assistance.operit.util.stream

/** 将字符串转换为字符流 用于在MarkdownTextComposable中将普通字符串转换为所需的字符流 */
fun String.stream(): Stream<Char> {
    val source = this
    return stream {
        for (c in source) {
            emit(c)
        }
    }
}
