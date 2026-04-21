package com.ai.assistance.operit.ui.features.chat.util

/**
 * 全局配置状态持有者，确保在整个应用生命周期内保持状态
 * 注意：这个状态只在应用单次运行期间有效，应用关闭后将被重置
 */
object ConfigurationStateHolder {
    // 标记默认配置是否已确认过，默认为false
    var hasConfirmedDefaultInSession: Boolean = false
} 