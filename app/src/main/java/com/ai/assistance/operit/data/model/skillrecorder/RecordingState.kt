package com.ai.assistance.operit.data.model.skillrecorder

/**
 * 录制状态机
 */
enum class RecordingState {
    /** 未录制 */
    IDLE,
    /** 录制中 */
    RECORDING,
    /** 暂停 */
    PAUSED,
    /** AI 正在总结 */
    SUMMARIZING,
    /** 总结完成，用户审阅中 */
    REVIEW
}
