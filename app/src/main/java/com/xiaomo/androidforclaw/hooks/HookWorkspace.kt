package com.xiaomo.androidforclaw.hooks

/**
 * OpenClaw Source Reference:
 * - src/hooks/workspace.ts
 *
 * Directory-based hook discovery: scan bundled, managed, workspace, plugin dirs
 * for hook subdirectories containing HOOK.md + handler files.
 *
 * Android adaptation: uses java.io.File instead of node:fs,
 * paths resolve relative to the app's data directory.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig
import com.xiaomo.androidforclaw.logging.Log
import java.io.File

private const val TAG = "hooks/workspace"

// ============================================================================
// Core discovery
// ============================================================================

/**
 * Load a single hook from a directory containing HOOK.md.
 * Aligned with OpenClaw loadHookFromDir.
 */
private fun loadHookFromDir(
    hookDir: File,
    source: HookSource,
    pluginId: String? = null,
    nameHint: String? = null
): LoadedHook? {
    val hookMdFile = File(hookDir, "HOOK.md")
    if (!hookMdFile.exists() || !hookMdFile.isFile) return null

    return try {
        val content = hookMdFile.readText(Charsets.UTF_8)
        val frontmatter = parseFrontmatter(content)

        val name = frontmatter["name"]?.ifBlank { null }
            ?: nameHint
            ?: hookDir.name

        val description = frontmatter["description"] ?: ""

        // Find handler file
        val handlerCandidates = listOf("handler.kt", "handler.ts", "handler.js", "index.kt", "index.ts", "index.js")
        var handlerPath: String? = null
        for (candidate in handlerCandidates) {
            val candidateFile = File(hookDir, candidate)
            if (candidateFile.exists() && candidateFile.isFile) {
                handlerPath = candidateFile.absolutePath
                break
            }
        }

        if (handlerPath == null) {
            Log.w(TAG, "Hook \"$name\" has HOOK.md but no handler file in ${hookDir.absolutePath}")
            return null
        }

        val baseDir = try {
            hookDir.canonicalPath
        } catch (_: Exception) {
            hookDir.absolutePath
        }

        LoadedHook(
            hook = Hook(
                name = name,
                description = description,
                source = source,
                pluginId = pluginId,
                filePath = hookMdFile.absolutePath,
                baseDir = baseDir,
                handlerPath = handlerPath
            ),
            frontmatter = frontmatter
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to load hook from ${hookDir.absolutePath}: ${e.stackTraceToString()}")
        null
    }
}

/**
 * Scan a directory for hooks (subdirectories containing HOOK.md).
 * Aligned with OpenClaw loadHooksFromDir.
 */
private fun loadHooksFromDir(
    dir: File,
    source: HookSource,
    pluginId: String? = null
): List<LoadedHook> {
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    val hooks = mutableListOf<LoadedHook>()
    val entries = dir.listFiles()?.filter { it.isDirectory } ?: return emptyList()

    for (entry in entries) {
        // Check for package.json with openclaw.hooks
        val packageHooks = readHookPackageManifest(entry)
        if (packageHooks.isNotEmpty()) {
            for (hookPath in packageHooks) {
                val resolvedHookDir = resolveContainedDir(entry, hookPath)
                if (resolvedHookDir == null) {
                    Log.w(TAG, "Ignoring out-of-package hook path \"$hookPath\" in ${entry.absolutePath}")
                    continue
                }
                val hook = loadHookFromDir(File(resolvedHookDir), source, pluginId, File(resolvedHookDir).name)
                if (hook != null) hooks.add(hook)
            }
            continue
        }

        // Try direct hook directory
        val hook = loadHookFromDir(entry, source, pluginId, entry.name)
        if (hook != null) hooks.add(hook)
    }

    return hooks
}

/**
 * Load hook entries from a directory, resolving metadata and invocation policy.
 * Aligned with OpenClaw loadHookEntriesFromDir.
 */
fun loadHookEntriesFromDir(
    dir: File,
    source: HookSource,
    pluginId: String? = null
): List<HookEntry> {
    val hooks = loadHooksFromDir(dir, source, pluginId)
    return hooks.map { (hook, frontmatter) ->
        HookEntry(
            hook = hook.copy(source = source, pluginId = pluginId),
            frontmatter = frontmatter,
            metadata = resolveOpenClawMetadata(frontmatter),
            invocation = resolveHookInvocationPolicy(frontmatter)
        )
    }
}

// ============================================================================
// Workspace discovery
// ============================================================================

/**
 * Discover all hook entries from bundled, managed, workspace, plugin dirs.
 * Aligned with OpenClaw discoverWorkspaceHookEntries.
 */
fun discoverWorkspaceHookEntries(
    workspaceDir: String,
    config: OpenClawConfig? = null,
    managedHooksDir: String? = null,
    bundledHooksDir: String? = null
): List<HookEntry> {
    val managedDir = managedHooksDir?.let { File(it) }
        ?: File(workspaceDir, ".openclaw/hooks")
    val workspaceHooksDir = File(workspaceDir, "hooks")
    val bundledDir = bundledHooksDir?.let { File(it) }

    val bundledHooks = if (bundledDir != null) {
        loadHookEntriesFromDir(bundledDir, HookSource.OPENCLAW_BUNDLED)
    } else {
        emptyList()
    }

    val managedHooks = loadHookEntriesFromDir(managedDir, HookSource.OPENCLAW_MANAGED)
    val workspaceHooks = loadHookEntriesFromDir(workspaceHooksDir, HookSource.OPENCLAW_WORKSPACE)

    return bundledHooks + managedHooks + workspaceHooks
}

/**
 * Build a hook snapshot for agent bootstrap.
 * Aligned with OpenClaw buildWorkspaceHookSnapshot.
 */
fun buildWorkspaceHookSnapshot(
    workspaceDir: String,
    config: OpenClawConfig? = null,
    managedHooksDir: String? = null,
    bundledHooksDir: String? = null,
    entries: List<HookEntry>? = null,
    eligibility: HookEligibilityContext? = null,
    snapshotVersion: Int? = null
): HookSnapshot {
    val hookEntries = entries ?: loadWorkspaceHookEntries(workspaceDir, config, managedHooksDir, bundledHooksDir)
    val eligible = hookEntries.filter { shouldIncludeHook(it, config, eligibility) }

    return HookSnapshot(
        hooks = eligible.map { entry ->
            HookSnapshot.HookSnapshotEntry(
                name = entry.hook.name,
                events = entry.metadata?.events ?: emptyList()
            )
        },
        resolvedHooks = eligible.map { it.hook },
        version = snapshotVersion
    )
}

/**
 * Load and resolve workspace hook entries.
 * Aligned with OpenClaw loadWorkspaceHookEntries.
 */
fun loadWorkspaceHookEntries(
    workspaceDir: String,
    config: OpenClawConfig? = null,
    managedHooksDir: String? = null,
    bundledHooksDir: String? = null,
    entries: List<HookEntry>? = null
): List<HookEntry> {
    return resolveHookEntries(
        entries ?: discoverWorkspaceHookEntries(workspaceDir, config, managedHooksDir, bundledHooksDir)
    ) { collision ->
        Log.w(
            TAG,
            "Ignoring ${collision.ignored.hook.source.value} hook \"${collision.name}\" " +
                "because it cannot override ${collision.kept.hook.source.value} hook code"
        )
    }
}

// ============================================================================
// Internal helpers
// ============================================================================

private data class LoadedHook(
    val hook: Hook,
    val frontmatter: ParsedHookFrontmatter
)

/**
 * Read package.json in a hook directory and extract openclaw.hooks list.
 */
private fun readHookPackageManifest(dir: File): List<String> {
    val manifestFile = File(dir, "package.json")
    if (!manifestFile.exists() || !manifestFile.isFile) return emptyList()

    return try {
        val content = manifestFile.readText(Charsets.UTF_8)
        // Simple JSON parsing for openclaw.hooks array
        val openclawIdx = content.indexOf("\"openclaw\"")
        if (openclawIdx < 0) return emptyList()
        val hooksIdx = content.indexOf("\"hooks\"", openclawIdx)
        if (hooksIdx < 0) return emptyList()
        val arrayStart = content.indexOf('[', hooksIdx)
        if (arrayStart < 0) return emptyList()
        val arrayEnd = content.indexOf(']', arrayStart)
        if (arrayEnd < 0) return emptyList()
        val arrayContent = content.substring(arrayStart + 1, arrayEnd)
        arrayContent.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
            .filter { it.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Resolve a path that must be contained within a base directory.
 * Returns canonical path or null if it escapes the base.
 */
private fun resolveContainedDir(baseDir: File, targetPath: String): String? {
    val resolved = File(baseDir, targetPath)
    return try {
        val baseCanonical = baseDir.canonicalPath
        val resolvedCanonical = resolved.canonicalPath
        if (resolvedCanonical.startsWith(baseCanonical + File.separator) || resolvedCanonical == baseCanonical) {
            resolvedCanonical
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
