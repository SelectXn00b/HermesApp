package com.ai.assistance.operit.core.tools.defaultTool.root

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.admin.AdminDeviceInfoToolExecutor

/** Root级别的设备信息工具，继承管理员版本 */
open class RootDeviceInfoToolExecutor(context: Context) : AdminDeviceInfoToolExecutor(context) {
    // 当前阶段不添加新功能，仅继承管理员实现
}
