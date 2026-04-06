package com.xiaomo.androidforclaw.agent.loop

/**
 * Media Wake — 对齐 OpenClaw media-wake 相关逻辑
 *
 * 异步媒体（图片/视频/音频）生成后，在对话中正确附带。
 * 支持的场景：
 * - TTS 音频文件附带
 * - 代码生成的图片附带
 * - 异步任务生成的文件附带
 *
 * OpenClaw 源: ../openclaw/src/agents/pi-embedded-runner/run/attempt.ts (media wake attachments)
 */

// ── Types ──

/** 媒体类型。OpenClaw: media types */
enum class MediaType {
    IMAGE,
    AUDIO,
    VIDEO,
    FILE
}

/** 媒体附件状态。 */
enum class MediaWakeState {
    /** 等待中 */
    PENDING,
    /** 已完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}

/**
 * 异步媒体唤醒附件。
 * OpenClaw: MediaWakeAttachment
 *
 * 用于跟踪异步生成的媒体，在对话中正确附带。
 */
data class MediaWakeAttachment(
    /** 唯一标识 */
    val id: String,
    /** 媒体类型 */
    val type: MediaType,
    /** 文件路径 */
    val filePath: String? = null,
    /** 文件 URL（远程） */
    val url: String? = null,
    /** MIME 类型 */
    val mimeType: String? = null,
    /** 显示标题/描述 */
    val caption: String? = null,
    /** 状态 */
    val state: MediaWakeState = MediaWakeState.PENDING,
    /** 工具调用 ID（关联的工具调用） */
    val toolCallId: String? = null,
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 媒体唤醒管理器。
 * 跟踪和管理对话中的异步媒体附件。
 */
class MediaWakeManager {
    private val attachments = mutableListOf<MediaWakeAttachment>()

    /**
     * 注册一个新的异步媒体附件。
     */
    fun register(
        id: String,
        type: MediaType,
        filePath: String? = null,
        url: String? = null,
        mimeType: String? = null,
        caption: String? = null,
        toolCallId: String? = null
    ): MediaWakeAttachment {
        val attachment = MediaWakeAttachment(
            id = id,
            type = type,
            filePath = filePath,
            url = url,
            mimeType = mimeType,
            caption = caption,
            toolCallId = toolCallId
        )
        attachments.add(attachment)
        return attachment
    }

    /**
     * 标记附件为完成。
     */
    fun complete(id: String, filePath: String? = null, url: String? = null): MediaWakeAttachment? {
        val index = attachments.indexOfFirst { it.id == id }
        if (index == -1) return null
        val updated = attachments[index].copy(
            state = MediaWakeState.COMPLETED,
            filePath = filePath ?: attachments[index].filePath,
            url = url ?: attachments[index].url
        )
        attachments[index] = updated
        return updated
    }

    /**
     * 标记附件为失败。
     */
    fun fail(id: String): MediaWakeAttachment? {
        val index = attachments.indexOfFirst { it.id == id }
        if (index == -1) return null
        val updated = attachments[index].copy(state = MediaWakeState.FAILED)
        attachments[index] = updated
        return updated
    }

    /**
     * 获取所有已完成的附件。
     */
    fun getCompleted(): List<MediaWakeAttachment> =
        attachments.filter { it.state == MediaWakeState.COMPLETED }

    /**
     * 获取所有待处理的附件。
     */
    fun getPending(): List<MediaWakeAttachment> =
        attachments.filter { it.state == MediaWakeState.PENDING }

    /**
     * 获取与特定工具调用关联的附件。
     */
    fun getByToolCallId(toolCallId: String): List<MediaWakeAttachment> =
        attachments.filter { it.toolCallId == toolCallId }

    /**
     * 清除已完成的附件。
     */
    fun clearCompleted() {
        attachments.removeAll { it.state == MediaWakeState.COMPLETED || it.state == MediaWakeState.FAILED }
    }

    /**
     * 检查是否有任何挂起的附件。
     */
    fun hasPending(): Boolean = attachments.any { it.state == MediaWakeState.PENDING }
}
