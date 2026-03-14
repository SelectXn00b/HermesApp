/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: data models.
 */
package com.xiaomo.androidforclaw.data.model

import android.graphics.Bitmap
import com.xiaomo.androidforclaw.ViewNode

data class ResultBean(
    val action: String? = null,
    val preImage: String? = null,
    val afterImage: String? = null
)

data class CheckResult(
    val lastScreenshot: Bitmap?,
    val newScreenshot: Bitmap,
    val lastPerceptionInfos: List<ViewNode>,
    val newPerceptionInfos: List<ViewNode>,
    val lastKeyboardActive: Boolean,
    val newKeyboardActive: Boolean,
    val summary: String,
    val action: String,
    // 反思评分是否为A
    val isA: Boolean
)
