package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/install.ts
 * - src/hooks/installs.ts
 * - src/hooks/update.ts
 *
 * Hook install, uninstall, and update operations.
 *
 * Android adaptation: simplified from the TS version since Android
 * doesn't use npm/git installations. Instead focuses on local
 * directory-based installs and manifest tracking.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "hooks:install"

// ============================================================================
// Install types (aligned with OpenClaw install.ts)
// ============================================================================

/**
 * Result of a hook install operation.
 * Aligned with OpenClaw InstallHooksResult.
 */
sealed class InstallHooksResult {
    data class Success(
        val hookPackId: String,
        val hooks: List<String>,
        val targetDir: String,
        val version: String? = null
    ) : InstallHooksResult()

    data class Failure(val error: String) : InstallHooksResult()
}

/**
 * Logger for hook install operations.
 */
interface HookInstallLogger {
    fun info(message: String) {}
    fun warn(message: String) {}
}

// ============================================================================
// Hook ID validation (aligned with OpenClaw validateHookId)
// ============================================================================

/**
 * Validate a hook ID for filesystem safety.
 * Returns error message or null if valid.
 */
fun validateHookId(hookId: String): String? {
    if (hookId.isBlank()) return "invalid hook name: missing"
    if (hookId == "." || hookId == "..") return "invalid hook name: reserved path segment"
    if (hookId.contains('/') || hookId.contains('\\')) return "invalid hook name: path separators not allowed"
    return null
}

/**
 * Resolve the install directory for a hook.
 * Aligned with OpenClaw resolveHookInstallDir.
 */
fun resolveHookInstallDir(hookId: String, hooksDir: String? = null): String {
    val hooksBase = hooksDir ?: "${System.getProperty("user.home") ?: "/data"}/.openclaw/hooks"
    val error = validateHookId(hookId)
    if (error != null) throw IllegalArgumentException(error)
    return File(hooksBase, hookId).absolutePath
}

// ============================================================================
// Install from directory (aligned with OpenClaw installHookFromDir)
// ============================================================================

/**
 * Install a hook from a local directory.
 */
suspend fun installHookFromDir(
    hookDir: String,
    hooksDir: String? = null,
    logger: HookInstallLogger? = null,
    mode: String = "install",
    dryRun: Boolean = false
): InstallHooksResult {
    val dir = File(hookDir)
    if (!dir.exists() || !dir.isDirectory) {
        return InstallHooksResult.Failure("Hook directory does not exist: $hookDir")
    }

    // Validate HOOK.md exists
    val hookMdFile = File(dir, "HOOK.md")
    if (!hookMdFile.exists()) {
        return InstallHooksResult.Failure("HOOK.md missing in $hookDir")
    }

    // Validate handler exists
    val handlerCandidates = listOf("handler.ts", "handler.js", "handler.kt", "index.ts", "index.js", "index.kt")
    val hasHandler = handlerCandidates.any { File(dir, it).exists() }
    if (!hasHandler) {
        return InstallHooksResult.Failure("handler file missing in $hookDir")
    }

    // Resolve hook name
    val content = hookMdFile.readText(Charsets.UTF_8)
    val frontmatter = parseFrontmatter(content)
    val hookName = frontmatter["name"]?.ifBlank { null } ?: dir.name

    val idError = validateHookId(hookName)
    if (idError != null) {
        return InstallHooksResult.Failure(idError)
    }

    val targetDir = resolveHookInstallDir(hookName, hooksDir)

    // Check if already exists
    val targetFile = File(targetDir)
    if (mode == "install" && targetFile.exists()) {
        return InstallHooksResult.Failure("hook already exists: $targetDir (delete it first)")
    }

    if (dryRun) {
        return InstallHooksResult.Success(
            hookPackId = hookName,
            hooks = listOf(hookName),
            targetDir = targetDir
        )
    }

    // Copy hook directory to target
    return try {
        dir.copyRecursively(targetFile, overwrite = mode == "update")
        logger?.info("Installed hook '$hookName' to $targetDir")
        InstallHooksResult.Success(
            hookPackId = hookName,
            hooks = listOf(hookName),
            targetDir = targetDir
        )
    } catch (e: Exception) {
        InstallHooksResult.Failure("failed to copy hook: ${e.message}")
    }
}

/**
 * Uninstall a hook by removing its directory.
 */
suspend fun uninstallHook(
    hookId: String,
    hooksDir: String? = null,
    logger: HookInstallLogger? = null
): Boolean {
    val targetDir = resolveHookInstallDir(hookId, hooksDir)
    val targetFile = File(targetDir)
    if (!targetFile.exists()) {
        logger?.warn("Hook '$hookId' not found at $targetDir")
        return false
    }
    return try {
        targetFile.deleteRecursively()
        logger?.info("Uninstalled hook '$hookId' from $targetDir")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to uninstall hook '$hookId': ${e.message}")
        false
    }
}

// ============================================================================
// Install record tracking (aligned with OpenClaw installs.ts)
// ============================================================================

/**
 * Hook install record for config tracking.
 * Aligned with OpenClaw HookInstallRecord + HookInstallUpdate.
 */
data class HookInstallRecord(
    val hookId: String,
    val source: String,
    val spec: String? = null,
    val installPath: String? = null,
    val version: String? = null,
    val resolvedName: String? = null,
    val resolvedSpec: String? = null,
    val integrity: String? = null,
    val hooks: List<String>? = null,
    val installedAt: String? = null
)

// ============================================================================
// Hook status (aligned with OpenClaw hooks-status.ts)
// ============================================================================

/**
 * Hook status entry with eligibility and requirements info.
 * Aligned with OpenClaw HookStatusEntry.
 */
data class HookStatusEntry(
    val name: String,
    val description: String,
    val source: String,
    val pluginId: String? = null,
    val filePath: String,
    val baseDir: String,
    val handlerPath: String,
    val hookKey: String,
    val emoji: String? = null,
    val homepage: String? = null,
    val events: List<String>,
    val always: Boolean,
    val enabledByConfig: Boolean,
    val requirementsSatisfied: Boolean,
    val loadable: Boolean,
    val blockedReason: String? = null,
    val managedByPlugin: Boolean
)

/**
 * Hook status report for a workspace.
 * Aligned with OpenClaw HookStatusReport.
 */
data class HookStatusReport(
    val workspaceDir: String,
    val managedHooksDir: String,
    val hooks: List<HookStatusEntry>
)

/**
 * Build a hook status report for the workspace.
 * Aligned with OpenClaw buildWorkspaceHookStatus.
 */
fun buildWorkspaceHookStatus(
    workspaceDir: String,
    config: OpenClawConfig? = null,
    managedHooksDir: String? = null,
    entries: List<HookEntry>? = null,
    @Suppress("UNUSED_PARAMETER") eligibility: HookEligibilityContext? = null
): HookStatusReport {
    val managedDir = managedHooksDir ?: "$workspaceDir/.openclaw/hooks"
    val hookEntries = resolveHookEntries(
        entries ?: loadWorkspaceHookEntries(workspaceDir, config)
    )

    return HookStatusReport(
        workspaceDir = workspaceDir,
        managedHooksDir = managedDir,
        hooks = hookEntries.map { entry -> buildHookStatus(entry, config) }
    )
}

private fun buildHookStatus(
    entry: HookEntry,
    config: OpenClawConfig? = null
): HookStatusEntry {
    val hookKey = entry.metadata?.hookKey ?: entry.hook.name
    val hookConfig = resolveHookConfig(config, hookKey)
    val managedByPlugin = entry.hook.source == HookSource.OPENCLAW_PLUGIN
    val enableState = resolveHookEnableState(entry, config, hookConfig)
    val always = entry.metadata?.always == true
    val events = entry.metadata?.events ?: emptyList()

    val requirementsSatisfied = evaluateRequirements(entry)
    val enabledByConfig = enableState.enabled
    val loadable = enabledByConfig && requirementsSatisfied

    val blockedReason = when {
        enableState.reason != null -> enableState.reason.value
        !requirementsSatisfied -> "missing requirements"
        else -> null
    }

    return HookStatusEntry(
        name = entry.hook.name,
        description = entry.hook.description,
        source = entry.hook.source.value,
        pluginId = entry.hook.pluginId,
        filePath = entry.hook.filePath,
        baseDir = entry.hook.baseDir,
        handlerPath = entry.hook.handlerPath,
        hookKey = hookKey,
        emoji = entry.metadata?.emoji,
        homepage = entry.metadata?.homepage,
        events = events,
        always = always,
        enabledByConfig = enabledByConfig,
        requirementsSatisfied = requirementsSatisfied,
        loadable = loadable,
        blockedReason = blockedReason,
        managedByPlugin = managedByPlugin
    )
}

private fun evaluateRequirements(entry: HookEntry): Boolean {
    val requires = entry.metadata?.requires ?: return true
    if (requires.bins != null && requires.bins.isNotEmpty()) {
        for (bin in requires.bins) {
            if (!hasBinary(bin)) return false
        }
    }
    if (requires.anyBins != null && requires.anyBins.isNotEmpty()) {
        if (!requires.anyBins.any { hasBinary(it) }) return false
    }
    return true
}

// ============================================================================
// Fire and forget (aligned with OpenClaw fire-and-forget.ts)
// ============================================================================

/**
 * Fire a hook task without waiting for completion.
 * Aligned with OpenClaw fireAndForgetHook.
 */
fun fireAndForgetHook(
    label: String,
    logger: ((String) -> Unit)? = null,
    task: suspend () -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            task()
        } catch (e: Exception) {
            val message = "$label: ${e.message}"
            logger?.invoke(message) ?: Log.d(TAG, message)
        }
    }
}
