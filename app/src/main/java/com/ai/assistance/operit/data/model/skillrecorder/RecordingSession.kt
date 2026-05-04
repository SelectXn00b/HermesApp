package com.ai.assistance.operit.data.model.skillrecorder

import java.util.UUID

/**
 * 完整录制会话
 */
data class RecordingSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    /** 构建器步骤列表 */
    val steps: MutableList<BuilderStep> = mutableListOf(),
    /** 用户录制前写的草稿（意图描述） */
    val draftText: String? = null,
    /** AI 生成的 SKILL.md 内容 */
    var generatedSkillMd: String? = null,
    /** 保存后的 Skill 名称 */
    var savedSkillName: String? = null
) {
    /** 所有录制步骤中的帧（按步骤顺序展平） */
    val allFrames: List<RecordingFrame>
        get() = steps.filterIsInstance<BuilderStep.Record>()
            .sortedBy { it.orderIndex }
            .flatMap { it.frames }

    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    /** 所有录制步骤的帧数总和 */
    val frameCount: Int
        get() = steps.filterIsInstance<BuilderStep.Record>().sumOf { it.frames.size }

    /** 总步骤数 */
    val stepCount: Int
        get() = steps.size
}
