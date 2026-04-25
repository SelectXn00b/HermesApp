package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 标签项数据类 */
data class TabItem(
    val path: String,
    val title: String,
    val environment: String? = null
)

/** 文件项数据类 */
data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val fullPath: String? = null
)