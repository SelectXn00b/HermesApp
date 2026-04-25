package com.ai.assistance.operit.util

import android.content.Context
import android.os.Environment
import java.io.File

object OperitPaths {

    private const val OPERIT_DIR_NAME = "Hermes"
    private const val CLEAN_ON_EXIT_DIR_NAME = "cleanOnExit"
    private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"
    private const val BRIDGE_DIR_NAME = "bridge"
    private const val EXPORTS_DIR_NAME = "exports"
    private const val WORKSPACE_DIR_NAME = "workspace"
    private const val TEST_DIR_NAME = "test"
    private const val WEBSESSION_DIR_NAME = "websession"
    private const val USERSCRIPTS_DIR_NAME = "userscripts"

    const val SHERPA_NCNN_MODELS_DIR_NAME = ".sherpa_ncnn_models"
    const val VECTOR_INDEX_DIR_NAME = ".vector_index"

    const val IMAGE_POOL_DIR_NAME = "image_pool"
    const val MEDIA_POOL_DIR_NAME = "media_pool"
    const val SKILL_REPO_ZIP_POOL_DIR_NAME = "skill_repo_zip_pool"

    fun downloadsDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun operitRootDir(): File {
        return ensureDir(File(downloadsDir(), OPERIT_DIR_NAME))
    }

    fun cleanOnExitDir(): File {
        return ensureDir(File(operitRootDir(), CLEAN_ON_EXIT_DIR_NAME))
    }

    fun cleanOnExitInternalDir(context: Context): File {
        return ensureDir(File(ensureDir(File(context.cacheDir, OPERIT_DIR_NAME)), CLEAN_ON_EXIT_DIR_NAME))
    }

    fun mcpPluginsDir(): File {
        return ensureDir(File(operitRootDir(), MCP_PLUGINS_DIR_NAME))
    }

    fun bridgeDir(): File {
        return ensureDir(File(operitRootDir(), BRIDGE_DIR_NAME))
    }

    fun exportsDir(): File {
        return ensureDir(File(operitRootDir(), EXPORTS_DIR_NAME))
    }

    fun workspaceDir(): File {
        return ensureDir(File(operitRootDir(), WORKSPACE_DIR_NAME))
    }

    fun testDir(): File {
        return ensureDir(File(operitRootDir(), TEST_DIR_NAME))
    }

    fun webSessionDir(): File {
        return ensureDir(File(operitRootDir(), WEBSESSION_DIR_NAME))
    }

    fun webSessionUserscriptsDir(): File {
        return ensureDir(File(webSessionDir(), USERSCRIPTS_DIR_NAME))
    }

    fun sherpaNcnnModelsDir(context: Context): File {
        return ensureDir(File(context.filesDir, SHERPA_NCNN_MODELS_DIR_NAME))
    }

    fun vectorIndexDir(context: Context): File {
        return ensureDir(File(context.filesDir, VECTOR_INDEX_DIR_NAME))
    }

    fun imagePoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, IMAGE_POOL_DIR_NAME))
    }

    fun mediaPoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, MEDIA_POOL_DIR_NAME))
    }

    fun skillRepoZipPoolDir(baseDir: File): File {
        return ensureDir(File(baseDir, SKILL_REPO_ZIP_POOL_DIR_NAME))
    }

    fun rawSnapshotExcludedFilesTopLevelDirNames(): Set<String> {
        return setOf(
            SHERPA_NCNN_MODELS_DIR_NAME,
            VECTOR_INDEX_DIR_NAME,
            IMAGE_POOL_DIR_NAME,
            MEDIA_POOL_DIR_NAME,
            SKILL_REPO_ZIP_POOL_DIR_NAME
        )
    }

    fun operitRootPathSdcard(): String {
        return "/sdcard/Download/$OPERIT_DIR_NAME"
    }

    fun cleanOnExitPathSdcard(): String {
        return "${operitRootPathSdcard()}/$CLEAN_ON_EXIT_DIR_NAME"
    }

    fun bridgePathSdcard(): String {
        return "${operitRootPathSdcard()}/$BRIDGE_DIR_NAME"
    }

    fun exportsPathSdcard(): String {
        return "${operitRootPathSdcard()}/$EXPORTS_DIR_NAME"
    }

    fun workspacePathSdcard(chatId: String): String {
        return "${operitRootPathSdcard()}/$WORKSPACE_DIR_NAME/$chatId"
    }

    fun testPathSdcard(): String {
        return "${operitRootPathSdcard()}/$TEST_DIR_NAME"
    }

    fun webSessionUserscriptsPathSdcard(): String {
        return "${operitRootPathSdcard()}/$WEBSESSION_DIR_NAME/$USERSCRIPTS_DIR_NAME"
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
