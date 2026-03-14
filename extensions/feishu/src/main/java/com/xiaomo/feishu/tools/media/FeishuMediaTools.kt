package com.xiaomo.feishu.tools.media

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu channel tool definitions.
 */


import com.xiaomo.feishu.FeishuClient
import com.xiaomo.feishu.FeishuConfig
import com.xiaomo.feishu.tools.FeishuToolBase

/**
 * 飞书媒体工具集
 */
class FeishuMediaTools(
    private val config: FeishuConfig,
    private val client: FeishuClient
) {
    private val imageUploadTool = FeishuImageUploadTool(config, client)

    /**
     * 获取所有媒体工具
     */
    fun getAllTools(): List<FeishuToolBase> {
        return listOf(
            imageUploadTool
        )
    }
}
