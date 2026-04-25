package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer

/**
 * MCP JSON序列化配置
 */
val McpJson = Json {
    // 忽略未知键，使序列化更加宽容
    ignoreUnknownKeys = true
    // 允许序列化Kotlin对象的默认值
    encodeDefaults = true
    // 允许松散的JSON解析
    isLenient = true
}

// Use a simpler approach without contextual serializers
// This will avoid the need for complex serialization setup 