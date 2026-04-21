package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityDeviceInfoToolExecutor

/** 调试级别的设备信息工具，继承无障碍版本 */
open class DebuggerDeviceInfoToolExecutor(context: Context) :
        AccessibilityDeviceInfoToolExecutor(context) {
    // 当前阶段不添加新功能，仅继承无障碍实现
}
