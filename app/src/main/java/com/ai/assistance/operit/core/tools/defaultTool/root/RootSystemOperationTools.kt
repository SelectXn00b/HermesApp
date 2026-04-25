package com.ai.assistance.operit.core.tools.defaultTool.root

import android.content.Context
import com.ai.assistance.operit.core.tools.defaultTool.admin.AdminSystemOperationTools

/** Root级别的系统操作工具，继承管理员版本 */
open class RootSystemOperationTools(context: Context) : AdminSystemOperationTools(context) {
    // 当前阶段不添加新功能，仅继承管理员实现
}
