package com.ai.assistance.operit.data.model.skillrecorder

import java.util.UUID

/**
 * 完整录制会话
 */
data class RecordingSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val frames: MutableList<RecordingFrame> = mutableListOf(),
    /** AI 生成的 SKILL.md 内容 */
    var generatedSkillMd: String? = null,
    /** 保存后的 Skill 名称 */
    var savedSkillName: String? = null
) {
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    val frameCount: Int
        get() = frames.size
}
