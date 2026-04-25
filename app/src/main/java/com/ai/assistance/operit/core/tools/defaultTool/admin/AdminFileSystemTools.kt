package com.ai.assistance.operit.core.tools.defaultTool.admin

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.debugger.DebuggerFileSystemTools

/** 管理员级别的文件系统工具，继承调试者级别 */
open class AdminFileSystemTools(context: Context) : DebuggerFileSystemTools(context) {
    // 当前阶段不添加新功能，仅继承调试者级别实现
}
