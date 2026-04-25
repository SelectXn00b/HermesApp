package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/**
 * 提供给UI列表显示的模型信息
 */
@Serializable
data class ModelOption(
    val id: String,
    val name: String
)
