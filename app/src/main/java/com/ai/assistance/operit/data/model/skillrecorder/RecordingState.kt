package com.ai.assistance.operit.data.model.skillrecorder

/**
 * 录制状态机
 */
enum class RecordingState {
    /** 未开始 */
    IDLE,
    /** 构建步骤中（不录制） */
    BUILDING,
    /** 录制单个步骤中 */
    STEP_RECORDING,
    /** 单步录制暂停 */
    STEP_PAUSED,
    /** AI 正在总结 */
    SUMMARIZING,
    /** 总结完成，用户审阅中 */
    REVIEW
}
