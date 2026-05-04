package com.ai.assistance.operit.data.model.skillrecorder

import java.util.UUID

/**
 * 分步构建器中的单个步骤。
 * 可以是录制步骤（从设备捕获操作）或思考步骤（手写推理逻辑）。
 */
sealed class BuilderStep(
    open val id: String,
    open val orderIndex: Int
) {
    /** 录制步骤：包含从设备捕获的操作帧 */
    data class Record(
        override val id: String = UUID.randomUUID().toString(),
        override val orderIndex: Int,
        val label: String = "",
        val frames: List<RecordingFrame>,
        val startTime: Long,
        val endTime: Long
    ) : BuilderStep(id = id, orderIndex = orderIndex)

    /** 思考步骤：用户手写的推理逻辑和条件判断 */
    data class Think(
        override val id: String = UUID.randomUUID().toString(),
        override val orderIndex: Int,
        val content: String
    ) : BuilderStep(id = id, orderIndex = orderIndex)
}
