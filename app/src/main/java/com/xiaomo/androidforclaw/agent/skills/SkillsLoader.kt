package com.xiaomo.androidforclaw.agent.skills

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/skills/(all)
 *
 * AndroidForClaw adaptation: bundled/managed/workspace skill discovery and cache.
 */


import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import java.io.File

/**
 * Skills Loader — unified skill loading with full OpenClaw alignment.
 *
 * Loading priority (higher overrides lower, by name dedup):
 * 1. extraDirs (lowest) — skills.extraDirs config
 * 2. Bundled Skills — assets/skills/
 * 3. Managed Skills — /sdcard/.androidforclaw/skills/
 * 4. Plugin Skills — enabled plugin skill directories
 * 5. Workspace Skills (highest) — /sdcard/.androidforclaw/workspace/skills/
 *
 * Features aligned with OpenClaw:
 * - extraDirs support (skills.extraDirs)
 * - Plugin skills (plugins.entries.<name>.skills dirs)
 * - Environment injection (skills.entries.<key>.env / apiKey)
 * - Hot reload with debounce (skills.watch / skills.watchDebounceMs)
 * - Managed + Workspace directory monitoring
 * - Unified SkillParser (no duplicate parsers)
 * - Consistent managed path (skills/ not .skills/)
 */
class SkillsLoader(private val context: Context) {
    companion object {
        private const val TAG = "SkillsLoader"

        // Three-tier Skills directories (aligns with OpenClaw architecture)
        private const val BUNDLED_SKILLS_PATH = "skills"  // assets path
        private const val MANAGED_SKILLS_DIR = "/sdcard/.androidforclaw/skills"  // aligns with ~/.openclaw/skills/
        private const val WORKSPACE_SKILLS_DIR = "/sdcard/.androidforclaw/workspace/skills"  // aligns with ~/.openclaw/workspace/

        // Skill file name
        private const val SKILL_FILE_NAME = "SKILL.md"
    }

    // Skills cache
    private val skillsCache = mutableMapOf<String, SkillDocument>()
    private var cacheValid = false

    // Config reference
    private val configLoader = ConfigLoader(context)

    // File monitoring (hot reload with debounce)
    private val fileObservers = mutableListOf<FileObserver>()
    private var hotReloadEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingReload: Runnable? = null

    /**
     * Load all Skills
     * Priority override: Workspace > Managed > Bundled > extraDirs
     *
     * @return Map<name, SkillDocument>
     */
    fun loadSkills(): Map<String, SkillDocument> {
        // Return cached if valid
        if (cacheValid && skillsCache.isNotEmpty()) {
            Log.d(TAG, "返回缓存的 Skills (${skillsCache.size} 个)")
            return skillsCache.toMap()
        }

        Log.d(TAG, "开始加载 Skills...")
        skillsCache.clear()

        val config = configLoader.loadOpenClawConfig()

        // Load by priority (lowest first, higher overrides)
        val extraCount = loadExtraDirsSkills(skillsCache, config.skills.extraDirs)
        val bundledCount = loadBundledSkills(skillsCache)
        val managedCount = loadManagedSkills(skillsCache)
        val pluginCount = loadPluginSkills(skillsCache, config)
        val workspaceCount = loadWorkspaceSkills(skillsCache)

        cacheValid = true

        Log.i(TAG, "Skills 加载完成: 总计 ${skillsCache.size} 个")
        Log.i(TAG, "  - extraDirs: $extraCount")
        Log.i(TAG, "  - Bundled: $bundledCount")
        Log.i(TAG, "  - Managed: $managedCount (覆盖)")
        Log.i(TAG, "  - Plugin: $pluginCount (覆盖)")
        Log.i(TAG, "  - Workspace: $workspaceCount (覆盖)")

        return skillsCache.toMap()
    }

    /**
     * Reload Skills (clear cache)
     */
    fun reload() {
        Log.i(TAG, "重新加载 Skills...")
        cacheValid = false
        loadSkills()
    }

    /**
     * Enable hot reload with debounce.
     * Monitors Workspace + Managed directories.
     * Aligns with OpenClaw: skills.watch + skills.watchDebounceMs
     */
    fun enableHotReload() {
        if (hotReloadEnabled) {
            Log.d(TAG, "热重载已启用")
            return
        }

        val config = configLoader.loadOpenClawConfig()
        if (!config.skills.watch) {
            Log.d(TAG, "热重载已在配置中禁用 (skills.watch=false)")
            return
        }

        val debounceMs = config.skills.watchDebounceMs

        // Monitor both Workspace and Managed directories
        val dirsToWatch = mutableListOf<File>()
        File(WORKSPACE_SKILLS_DIR).let { if (it.exists()) dirsToWatch.add(it) }
        File(MANAGED_SKILLS_DIR).let { if (it.exists()) dirsToWatch.add(it) }

        // Also monitor extraDirs
        config.skills.extraDirs.forEach { dir ->
            File(dir).let { if (it.exists()) dirsToWatch.add(it) }
        }

        if (dirsToWatch.isEmpty()) {
            Log.w(TAG, "没有可监控的 Skills 目录")
            return
        }

        for (dir in dirsToWatch) {
            try {
                val observer = object : FileObserver(dir, CREATE or MODIFY or DELETE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path != null && path.endsWith(SKILL_FILE_NAME)) {
                            Log.i(TAG, "检测到 Skill 文件变化: ${dir.name}/$path")
                            scheduleReload(debounceMs)
                        }
                    }
                }
                observer.startWatching()
                fileObservers.add(observer)
                Log.i(TAG, "✅ 监控: ${dir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "启用热重载失败: ${dir.absolutePath}", e)
            }
        }

        hotReloadEnabled = true
        Log.i(TAG, "✅ 热重载已启用 (debounce=${debounceMs}ms, 监控 ${dirsToWatch.size} 个目录)")
    }

    /**
     * Schedule a debounced reload
     */
    private fun scheduleReload(debounceMs: Long) {
        // Cancel any pending reload
        pendingReload?.let { handler.removeCallbacks(it) }

        // Schedule new reload
        val runnable = Runnable {
            Log.i(TAG, "Debounce 完成，执行重新加载...")
            reload()
        }
        pendingReload = runnable
        handler.postDelayed(runnable, debounceMs)
    }

    /**
     * Disable hot reload
     */
    fun disableHotReload() {
        pendingReload?.let { handler.removeCallbacks(it) }
        pendingReload = null
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()
        hotReloadEnabled = false
        Log.i(TAG, "热重载已禁用")
    }

    /**
     * Check if hot reload is enabled
     */
    fun isHotReloadEnabled(): Boolean = hotReloadEnabled

    /**
     * Get all loaded skills
     */
    fun getAllSkills(): List<SkillDocument> {
        return loadSkills().values.toList()
    }

    /**
     * Get Always Skills (always-loaded skills)
     * These skills are loaded into system prompt at startup
     */
    fun getAlwaysSkills(): List<SkillDocument> {
        val allSkills = loadSkills()
        val alwaysSkills = allSkills.values.filter { it.metadata.always }
        Log.d(TAG, "Always Skills: ${alwaysSkills.size} 个")
        return alwaysSkills
    }

    /**
     * Select relevant Skills based on user goal
     *
     * @param userGoal User goal/instruction
     * @param excludeAlways Whether to exclude always skills (avoid duplication)
     * @return List of relevant Skills
     */
    fun selectRelevantSkills(
        userGoal: String,
        excludeAlways: Boolean = true
    ): List<SkillDocument> {
        val allSkills = loadSkills()
        val keywords = userGoal.lowercase()

        // 1. Use task type identification
        val recommendedSkillNames = identifyTaskType(userGoal)

        // 2. Keyword matching
        val relevant = allSkills.values.filter { skill ->
            // Exclude always skills (avoid duplicate injection)
            if (excludeAlways && skill.metadata.always) {
                return@filter false
            }

            // Prioritize task type recommendations
            if (recommendedSkillNames.contains(skill.name)) {
                return@filter true
            }

            // Then try keyword matching
            keywords.contains(skill.name.lowercase()) ||
                    keywords.contains(skill.description.lowercase()) ||
                    matchesKeywords(skill, keywords)
        }

        Log.d(TAG, "选择相关 Skills: ${relevant.size} 个")
        for (skill in relevant) {
            Log.d(TAG, "  - ${skill.name} (${skill.description})")
        }

        return relevant
    }

    /**
     * Resolve environment variables for a skill from config entries.
     *
     * Aligns with OpenClaw: skills.entries.<key>.env and skills.entries.<key>.apiKey.
     * Returns a map of env vars to inject. Only includes vars not already set.
     *
     * @param skill The skill to resolve env for
     * @return Map of environment variable name -> value to inject
     */
    fun resolveSkillEnv(skill: SkillDocument): Map<String, String> {
        val config = configLoader.loadOpenClawConfig()
        val skillKey = skill.effectiveSkillKey()
        val skillConfig = config.skills.entries[skillKey] ?: return emptyMap()

        val result = mutableMapOf<String, String>()

        // 1. Apply env map (only if not already set in system env)
        skillConfig.env?.forEach { (key, value) ->
            if (System.getenv(key).isNullOrEmpty()) {
                result[key] = value
            }
        }

        // 2. Apply apiKey convenience (maps to primaryEnv)
        val primaryEnv = skill.metadata.primaryEnv
        val apiKeyValue = skillConfig.resolveApiKey()
        if (primaryEnv != null && apiKeyValue != null && System.getenv(primaryEnv).isNullOrEmpty()) {
            result[primaryEnv] = apiKeyValue
        }

        if (result.isNotEmpty()) {
            Log.d(TAG, "Skill '$skillKey' env injection: ${result.keys.joinToString()}")
        }

        return result
    }

    /**
     * Apply environment variables for a skill into the given env map.
     * Call this before launching an agent run.
     *
     * @param skill The skill
     * @param targetEnv The mutable environment map to inject into
     */
    fun applySkillEnv(skill: SkillDocument, targetEnv: MutableMap<String, String>) {
        val envVars = resolveSkillEnv(skill)
        targetEnv.putAll(envVars)
    }

    /**
     * Resolve and apply env vars for ALL loaded skills into a target env map.
     * Useful before starting an agent session.
     */
    fun applyAllSkillsEnv(targetEnv: MutableMap<String, String>) {
        val allSkills = loadSkills()
        allSkills.values.forEach { skill ->
            applySkillEnv(skill, targetEnv)
        }
    }

    /**
     * Check if Skill's dependency requirements are met
     */
    fun checkRequirements(skill: SkillDocument): RequirementsCheckResult {
        val requires = skill.metadata.requires
            ?: return RequirementsCheckResult.Satisfied

        if (!requires.hasRequirements()) {
            return RequirementsCheckResult.Satisfied
        }

        val missingBins = requires.bins.filter { !isBinaryAvailable(it) }
        val missingEnv = requires.env.filter { System.getenv(it) == null }
        val missingConfig = requires.config.filter { !isConfigAvailable(it) }

        // anyBins: at least one must be available
        val anyBinsMissing = if (requires.anyBins.isNotEmpty()) {
            requires.anyBins.none { isBinaryAvailable(it) }
        } else {
            false
        }

        if (missingBins.isEmpty() && missingEnv.isEmpty() && missingConfig.isEmpty() && !anyBinsMissing) {
            return RequirementsCheckResult.Satisfied
        }

        return RequirementsCheckResult.Unsatisfied(
            missingBins = missingBins,
            missingAnyBins = if (anyBinsMissing) requires.anyBins else emptyList(),
            missingEnv = missingEnv,
            missingConfig = missingConfig
        )
    }

    /**
     * Get Skill statistics
     */
    fun getStatistics(): SkillsStatistics {
        val skills = loadSkills()
        val alwaysSkills = skills.values.count { it.metadata.always }
        val onDemandSkills = skills.size - alwaysSkills
        val totalTokens = skills.values.sumOf { it.estimateTokens() }
        val alwaysTokens = skills.values.filter { it.metadata.always }.sumOf { it.estimateTokens() }

        return SkillsStatistics(
            totalSkills = skills.size,
            alwaysSkills = alwaysSkills,
            onDemandSkills = onDemandSkills,
            totalTokens = totalTokens,
            alwaysTokens = alwaysTokens
        )
    }

    // ==================== Private: Loading ====================

    /**
     * Load skills from extraDirs (lowest priority)
     * Aligns with OpenClaw: skills.load.extraDirs
     */
    private fun loadExtraDirsSkills(
        skills: MutableMap<String, SkillDocument>,
        extraDirs: List<String>
    ): Int {
        var count = 0
        for (dirPath in extraDirs) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) {
                Log.w(TAG, "extraDirs 目录不存在: $dirPath")
                continue
            }
            count += loadSkillsFromDirectory(dir, SkillSource.EXTRA, skills)
        }
        return count
    }

    /**
     * Load bundled Skills from assets/skills/
     */
    private fun loadBundledSkills(skills: MutableMap<String, SkillDocument>): Int {
        var count = 0

        try {
            val skillDirs = context.assets.list(BUNDLED_SKILLS_PATH) ?: emptyArray()
            Log.d(TAG, "扫描 Bundled Skills: ${skillDirs.size} 个目录")

            for (dir in skillDirs) {
                val skillPath = "$BUNDLED_SKILLS_PATH/$dir/$SKILL_FILE_NAME"
                try {
                    val content = context.assets.open(skillPath)
                        .bufferedReader().use { it.readText() }

                    val skill = SkillParser.parse(content, "assets://$skillPath")
                        .copy(source = SkillSource.BUNDLED, filePath = "assets://$skillPath")
                    skills[skill.name] = skill
                    count++
                } catch (e: Exception) {
                    Log.w(TAG, "❌ 加载 Bundled Skill 失败: $dir - ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描 Bundled Skills 失败", e)
        }

        return count
    }

    /**
     * Load managed Skills from /sdcard/.androidforclaw/skills/
     */
    private fun loadManagedSkills(skills: MutableMap<String, SkillDocument>): Int {
        val managedDir = File(MANAGED_SKILLS_DIR)
        if (!managedDir.exists()) {
            Log.d(TAG, "Managed Skills 目录不存在: $MANAGED_SKILLS_DIR")
            return 0
        }
        return loadSkillsFromDirectory(managedDir, SkillSource.MANAGED, skills)
    }

    /**
     * Load plugin Skills from enabled plugins.
     *
     * Aligns with OpenClaw: plugins can ship skills by declaring `skills` dirs
     * in openclaw.plugin.json. On Android, we read plugins.entries from config
     * and scan each enabled plugin's skill directories.
     *
     * Plugin skill directories are resolved relative to the extensions base path
     * (assets://extensions/<pluginName>/ for bundled, or filesystem for installed).
     */
    private fun loadPluginSkills(
        skills: MutableMap<String, SkillDocument>,
        config: com.xiaomo.androidforclaw.config.OpenClawConfig
    ): Int {
        var count = 0

        for ((pluginName, pluginEntry) in config.plugins.entries) {
            if (!pluginEntry.enabled) continue

            // Determine skill dirs for this plugin
            val skillDirs = pluginEntry.skills.ifEmpty { listOf("skills") }

            for (skillDir in skillDirs) {
                // Try bundled assets first (assets://extensions/<plugin>/<dir>/)
                val assetsPath = "extensions/$pluginName/$skillDir"
                try {
                    val assetDirs = context.assets.list(assetsPath)
                    if (assetDirs != null && assetDirs.isNotEmpty()) {
                        for (dir in assetDirs) {
                            val skillMdPath = "$assetsPath/$dir/$SKILL_FILE_NAME"
                            try {
                                val content = context.assets.open(skillMdPath)
                                    .bufferedReader().use { it.readText() }
                                val skill = SkillParser.parse(content, "assets://$skillMdPath")
                                    .copy(source = SkillSource.PLUGIN, filePath = "assets://$skillMdPath")

                                val isOverride = skills.containsKey(skill.name)
                                skills[skill.name] = skill
                                count++

                                val action = if (isOverride) "覆盖" else "新增"
                                Log.d(TAG, "✅ Plugin/$pluginName ($action): ${skill.name}")
                            } catch (e: Exception) {
                                // Not a valid skill dir, skip
                            }
                        }
                        continue // Found in assets, don't check filesystem
                    }
                } catch (e: Exception) {
                    // Not in assets, try filesystem
                }

                // Try filesystem (installed plugins)
                val fsPath = File("/sdcard/.androidforclaw/extensions/$pluginName/$skillDir")
                if (fsPath.exists() && fsPath.isDirectory) {
                    count += loadSkillsFromDirectory(fsPath, SkillSource.PLUGIN, skills)
                }
            }
        }

        if (count > 0) {
            Log.i(TAG, "Plugin Skills: $count 个加载完成")
        }

        return count
    }

    /**
     * Load workspace Skills from /sdcard/.androidforclaw/workspace/skills/
     */
    private fun loadWorkspaceSkills(skills: MutableMap<String, SkillDocument>): Int {
        val workspaceDir = File(WORKSPACE_SKILLS_DIR)
        if (!workspaceDir.exists()) {
            Log.d(TAG, "Workspace Skills 目录不存在: $WORKSPACE_SKILLS_DIR")
            return 0
        }
        return loadSkillsFromDirectory(workspaceDir, SkillSource.WORKSPACE, skills)
    }

    /**
     * Generic: Load skills from a filesystem directory
     */
    private fun loadSkillsFromDirectory(
        dir: File,
        source: SkillSource,
        skills: MutableMap<String, SkillDocument>
    ): Int {
        var count = 0
        val skillDirs = dir.listFiles { file -> file.isDirectory } ?: emptyArray()
        Log.d(TAG, "扫描 ${source.displayName} Skills: ${skillDirs.size} 个目录 (${dir.absolutePath})")

        for (skillDir in skillDirs) {
            val skillFile = File(skillDir, SKILL_FILE_NAME)
            if (!skillFile.exists()) continue

            try {
                val content = skillFile.readText()
                val skill = SkillParser.parse(content, skillFile.absolutePath)
                    .copy(source = source, filePath = skillFile.absolutePath)

                val isOverride = skills.containsKey(skill.name)
                skills[skill.name] = skill
                count++

                val action = if (isOverride) "覆盖" else "新增"
                Log.d(TAG, "✅ ${source.displayName} ($action): ${skill.name}")
            } catch (e: Exception) {
                Log.w(TAG, "❌ 加载 ${source.displayName} Skill 失败: ${skillDir.name} - ${e.message}")
            }
        }

        return count
    }

    // ==================== Private: Keyword Matching ====================

    /**
     * Keyword matching for skill selection
     */
    private fun matchesKeywords(skill: SkillDocument, keywords: String): Boolean {
        val matched = when (skill.name) {
            "app-testing" -> {
                keywords.contains("测试") || keywords.contains("test") ||
                keywords.contains("检查") || keywords.contains("验证") ||
                keywords.contains("功能") || keywords.contains("用例")
            }
            "debugging" -> {
                keywords.contains("调试") || keywords.contains("debug") ||
                keywords.contains("bug") || keywords.contains("错误") ||
                keywords.contains("问题") || keywords.contains("异常") ||
                keywords.contains("崩溃")
            }
            "accessibility" -> {
                keywords.contains("无障碍") || keywords.contains("accessibility") ||
                keywords.contains("wcag") || keywords.contains("适配") ||
                keywords.contains("可读性") || keywords.contains("对比度")
            }
            "performance" -> {
                keywords.contains("性能") || keywords.contains("performance") ||
                keywords.contains("优化") || keywords.contains("卡顿") ||
                keywords.contains("流畅") || keywords.contains("启动") ||
                keywords.contains("加载") || keywords.contains("慢")
            }
            "ui-validation" -> {
                keywords.contains("ui") || keywords.contains("界面") ||
                keywords.contains("布局") || keywords.contains("显示") ||
                keywords.contains("页面") || keywords.contains("视觉")
            }
            "network-testing" -> {
                keywords.contains("网络") || keywords.contains("network") ||
                keywords.contains("联网") || keywords.contains("在线") ||
                keywords.contains("离线") || keywords.contains("断网") ||
                keywords.contains("api") || keywords.contains("请求")
            }
            "feishu", "feishu-doc" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("文档") || keywords.contains("doc")
            }
            "feishu-wiki" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("知识库") || keywords.contains("wiki")
            }
            "feishu-drive" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("云空间") || keywords.contains("drive") ||
                keywords.contains("文件夹") || keywords.contains("云文档")
            }
            "feishu-bitable" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("多维表格") || keywords.contains("bitable") ||
                keywords.contains("表格")
            }
            "feishu-task" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("任务") || keywords.contains("task") ||
                keywords.contains("待办")
            }
            "feishu-chat" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("群聊") || keywords.contains("chat") ||
                keywords.contains("群组")
            }
            "feishu-perm" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("权限") || keywords.contains("perm") ||
                keywords.contains("分享") || keywords.contains("协作")
            }
            "feishu-urgent" -> {
                keywords.contains("飞书") || keywords.contains("feishu") ||
                keywords.contains("加急") || keywords.contains("urgent") ||
                keywords.contains("提醒")
            }
            else -> false
        }

        if (matched) return true

        // Feishu URL pattern matching
        if (skill.name.startsWith("feishu") &&
            (keywords.contains("feishu.cn/") || keywords.contains("飞书"))) {
            return true
        }

        // Generic fallback: match skill name tokens in user goal
        val nameTokens = skill.name.lowercase().split("-", "_")
        return nameTokens.any { token -> token.length >= 3 && keywords.contains(token) }
    }

    /**
     * Task type identification
     */
    private fun identifyTaskType(userGoal: String): List<String> {
        val keywords = userGoal.lowercase()
        val recommendedSkills = mutableListOf<String>()

        if (keywords.contains("测试") || keywords.contains("test") ||
            keywords.contains("验证") || keywords.contains("检查")) {
            recommendedSkills.add("app-testing")
        }

        if (keywords.contains("调试") || keywords.contains("debug") ||
            keywords.contains("bug") || keywords.contains("问题") ||
            keywords.contains("错误") || keywords.contains("崩溃")) {
            recommendedSkills.add("debugging")
        }

        if (keywords.contains("界面") || keywords.contains("ui") ||
            keywords.contains("布局") || keywords.contains("显示") ||
            keywords.contains("页面")) {
            recommendedSkills.add("ui-validation")
        }

        if (keywords.contains("性能") || keywords.contains("卡顿") ||
            keywords.contains("慢") || keywords.contains("优化") ||
            keywords.contains("启动") || keywords.contains("流畅")) {
            recommendedSkills.add("performance")
        }

        if (keywords.contains("无障碍") || keywords.contains("accessibility") ||
            keywords.contains("适配") || keywords.contains("可读性")) {
            recommendedSkills.add("accessibility")
        }

        if (keywords.contains("网络") || keywords.contains("联网") ||
            keywords.contains("离线") || keywords.contains("断网") ||
            keywords.contains("api")) {
            recommendedSkills.add("network-testing")
        }

        if (keywords.contains("飞书") || keywords.contains("feishu")) {
            if (keywords.contains("文档") || keywords.contains("doc") || keywords.contains("docx")) {
                recommendedSkills.add("feishu-doc")
            }
            if (keywords.contains("知识库") || keywords.contains("wiki")) {
                recommendedSkills.add("feishu-wiki")
            }
            if (keywords.contains("表格") || keywords.contains("bitable") || keywords.contains("多维")) {
                recommendedSkills.add("feishu-bitable")
            }
            if (keywords.contains("任务") || keywords.contains("task") || keywords.contains("待办")) {
                recommendedSkills.add("feishu-task")
            }
            if (keywords.contains("云空间") || keywords.contains("drive") || keywords.contains("文件")) {
                recommendedSkills.add("feishu-drive")
            }
            if (keywords.contains("权限") || keywords.contains("perm") || keywords.contains("分享")) {
                recommendedSkills.add("feishu-perm")
            }
            if (keywords.contains("群") || keywords.contains("chat")) {
                recommendedSkills.add("feishu-chat")
            }
            if (keywords.contains("加急") || keywords.contains("urgent") || keywords.contains("提醒")) {
                recommendedSkills.add("feishu-urgent")
            }
            if (recommendedSkills.none { it.startsWith("feishu-") }) {
                recommendedSkills.add("feishu")
                recommendedSkills.add("feishu-doc")
            }
        }

        return recommendedSkills
    }

    // ==================== Private: Requirements Checking ====================

    /**
     * Check if binary tool is available
     */
    private fun isBinaryAvailable(bin: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which $bin")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if config item is available
     */
    private fun isConfigAvailable(configKey: String): Boolean {
        return try {
            val config = configLoader.loadOpenClawConfig()
            // Use dot-path resolution
            val parts = configKey.split(".")
            when {
                parts.size >= 2 && parts[0] in listOf("gateway", "channels") -> {
                    when (parts.getOrNull(1)) {
                        "enabled" -> true
                        "feishu" -> config.channels.feishu.enabled
                        "discord" -> config.channels.discord?.enabled ?: false
                        else -> false
                    }
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Requirements check result
 */
sealed class RequirementsCheckResult {
    object Satisfied : RequirementsCheckResult()

    data class Unsatisfied(
        val missingBins: List<String>,
        val missingAnyBins: List<String> = emptyList(),
        val missingEnv: List<String>,
        val missingConfig: List<String>
    ) : RequirementsCheckResult() {
        fun getErrorMessage(): String {
            val parts = mutableListOf<String>()
            if (missingBins.isNotEmpty()) {
                parts.add("缺少二进制工具: ${missingBins.joinToString()}")
            }
            if (missingAnyBins.isNotEmpty()) {
                parts.add("至少需要一个: ${missingAnyBins.joinToString()}")
            }
            if (missingEnv.isNotEmpty()) {
                parts.add("缺少环境变量: ${missingEnv.joinToString()}")
            }
            if (missingConfig.isNotEmpty()) {
                parts.add("缺少配置项: ${missingConfig.joinToString()}")
            }
            return parts.joinToString("; ")
        }
    }
}

/**
 * Skills statistics
 */
data class SkillsStatistics(
    val totalSkills: Int,
    val alwaysSkills: Int,
    val onDemandSkills: Int,
    val totalTokens: Int,
    val alwaysTokens: Int
) {
    fun getReport(): String {
        return """
Skills 统计:
  - 总计: $totalSkills 个
  - Always: $alwaysSkills 个
  - On-Demand: $onDemandSkills 个
  - Token 总量: $totalTokens tokens
  - Always Token: $alwaysTokens tokens
        """.trimIndent()
    }
}
