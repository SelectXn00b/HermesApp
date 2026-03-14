/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: debug and test runners.
 */
package com.xiaomo.androidforclaw.data.model

import com.xiaomo.androidforclaw.agent.Prompt
import com.xiaomo.androidforclaw.ui.adapter.TestLogAdapter
import kotlinx.coroutines.flow.Flow
import java.lang.ref.WeakReference


data class TestLogItem(
    val id: String,
    val type: ItemType,
    var status: StreamingStatus,
    var title: String = "",
    var content: String = "",
    var prompt: Prompt ,
    val image1: String = "",
    val image2: String = "",
    val flow: Flow<String>? = null,
    var holder : WeakReference<TestLogAdapter.Type1ViewHolder>? = null,
    var duration: Long = 0 // 请求时长（毫秒）

)

enum class ItemType {
    TEXT,
    IMAGE,
    IMAGE_IMAGE_TEXT
}

enum class StreamingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}