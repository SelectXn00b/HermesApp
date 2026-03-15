/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/(all)
 *
 * AndroidForClaw adaptation: workspace initialization.
 */
package com.xiaomo.androidforclaw.workspace

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Workspace initializer
 * 对齐 OpenClaw 的 workspace 初始化逻辑
 *
 * Features:
 * - 创建 .androidforclaw/ 目录结构
 * - Initialize workspace/ 文件 (BOOTSTRAP.md, IDENTITY.md, USER.md 等)
 * - 生成 device-id 和元数据文件
 */
class WorkspaceInitializer(private val context: Context) {

    companion object {
        private const val TAG = "WorkspaceInit"

        // 主目录
        private const val ROOT_DIR = "/sdcard/.androidforclaw"

        // 子目录
        private const val CONFIG_DIR = "$ROOT_DIR/config"
        private const val WORKSPACE_DIR = "$ROOT_DIR/workspace"
        private const val WORKSPACE_META_DIR = "$WORKSPACE_DIR/.androidforclaw"
        private const val SKILLS_DIR = "$ROOT_DIR/skills"
        private const val LOGS_DIR = "$ROOT_DIR/logs"

        // 元数据文件
        private const val DEVICE_ID_FILE = "$ROOT_DIR/.device-id"
        private const val WORKSPACE_STATE_FILE = "$WORKSPACE_META_DIR/workspace-state.json"
    }

    /**
     * Initialize workspace (首次启动)
     * 对齐 OpenClaw 的初始化流程
     */
    fun initializeWorkspace(): Boolean {
        Log.i(TAG, "开始初始化 Workspace...")

        try {
            // 1. Create directory structure
            createDirectoryStructure()

            // 2. 生成 device-id
            ensureDeviceId()

            // 3. Initialize workspace 文件
            initializeWorkspaceFiles()

            // 4. 拷贝内置 skills 到用户可编辑目录
            // Aligned with OpenClaw: ~/.openclaw/skills/ → /sdcard/.androidforclaw/skills/
            copyBundledSkills()

            // 5. 创建 workspace 元数据
            createWorkspaceState()

            Log.i(TAG, "✅ Workspace 初始化完成")
            Log.i(TAG, "   位置: $ROOT_DIR")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Workspace 初始化失败", e)
            return false
        }
    }

    /**
     * Check if workspace is already initialized
     */
    fun isWorkspaceInitialized(): Boolean {
        val rootDir = File(ROOT_DIR)
        val workspaceDir = File(WORKSPACE_DIR)
        val deviceIdFile = File(DEVICE_ID_FILE)

        return rootDir.exists() &&
                workspaceDir.exists() &&
                deviceIdFile.exists()
    }

    /**
     * 获取 workspace 路径
     */
    fun getWorkspacePath(): String = WORKSPACE_DIR

    /**
     * 获取 device ID
     */
    fun getDeviceId(): String? {
        val file = File(DEVICE_ID_FILE)
        return if (file.exists()) {
            file.readText().trim()
        } else {
            null
        }
    }

    /**
     * Ensure bundled skills are deployed.
     * Call this on every app start — only copies missing skills, won't overwrite.
     */
    fun ensureBundledSkills() {
        try {
            File(SKILLS_DIR).mkdirs()
            copyBundledSkills()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure bundled skills: ${e.message}")
        }
    }

    // ==================== 私有方法 ====================

    /**
     * Create directory structure
     */
    private fun createDirectoryStructure() {
        val dirs = listOf(
            ROOT_DIR,
            CONFIG_DIR,
            WORKSPACE_DIR,
            WORKSPACE_META_DIR,
            SKILLS_DIR,
            LOGS_DIR
        )

        for (dir in dirs) {
            val file = File(dir)
            if (!file.exists()) {
                file.mkdirs()
                Log.d(TAG, "创建目录: $dir")
            }
        }
    }

    /**
     * 生成或加载 device-id
     */
    private fun ensureDeviceId() {
        val file = File(DEVICE_ID_FILE)
        if (!file.exists()) {
            val deviceId = UUID.randomUUID().toString()
            file.writeText(deviceId)
            Log.d(TAG, "生成 device-id: $deviceId")
        } else {
            Log.d(TAG, "device-id 已存在: ${file.readText().trim()}")
        }
    }

    /**
     * Initialize workspace 文件 (对齐 OpenClaw)
     */
    private fun initializeWorkspaceFiles() {
        val workspaceDir = File(WORKSPACE_DIR)

        // BOOTSTRAP.md
        val bootstrapFile = File(workspaceDir, "BOOTSTRAP.md")
        if (!bootstrapFile.exists()) {
            bootstrapFile.writeText(BOOTSTRAP_CONTENT)
            Log.d(TAG, "创建 BOOTSTRAP.md")
        }

        // IDENTITY.md
        val identityFile = File(workspaceDir, "IDENTITY.md")
        if (!identityFile.exists()) {
            identityFile.writeText(IDENTITY_CONTENT)
            Log.d(TAG, "创建 IDENTITY.md")
        }

        // USER.md
        val userFile = File(workspaceDir, "USER.md")
        if (!userFile.exists()) {
            userFile.writeText(USER_CONTENT)
            Log.d(TAG, "创建 USER.md")
        }

        // SOUL.md
        val soulFile = File(workspaceDir, "SOUL.md")
        if (!soulFile.exists()) {
            soulFile.writeText(SOUL_CONTENT)
            Log.d(TAG, "创建 SOUL.md")
        }

        // AGENTS.md
        val agentsFile = File(workspaceDir, "AGENTS.md")
        if (!agentsFile.exists()) {
            agentsFile.writeText(AGENTS_CONTENT)
            Log.d(TAG, "创建 AGENTS.md")
        }

        // TOOLS.md
        val toolsFile = File(workspaceDir, "TOOLS.md")
        if (!toolsFile.exists()) {
            toolsFile.writeText(TOOLS_CONTENT)
            Log.d(TAG, "创建 TOOLS.md")
        }

        // HEARTBEAT.md
        val heartbeatFile = File(workspaceDir, "HEARTBEAT.md")
        if (!heartbeatFile.exists()) {
            heartbeatFile.writeText(HEARTBEAT_CONTENT)
            Log.d(TAG, "创建 HEARTBEAT.md")
        }
    }

    /**
     * 创建 workspace 元数据
     */
    private fun createWorkspaceState() {
        val stateFile = File(WORKSPACE_STATE_FILE)
        if (!stateFile.exists()) {
            val timestamp = java.time.Instant.now().toString()
            val state = """
            {
              "version": 1,
              "bootstrapSeededAt": "$timestamp",
              "platform": "android"
            }
            """.trimIndent()
            stateFile.writeText(state)
            Log.d(TAG, "创建 workspace-state.json")
        }
    }

    /**
     * Copy bundled skills from assets to user-editable /sdcard/.androidforclaw/skills/
     * 
     * Aligned with OpenClaw: skills live in ~/.openclaw/skills/ where users can
     * customize, add, or remove them. Bundled skills are copied on first init only.
     * Existing user-modified skills are NOT overwritten.
     */
    private fun copyBundledSkills() {
        val skillsDir = File(SKILLS_DIR)
        val assetManager = context.assets

        try {
            val bundledSkills = assetManager.list("skills") ?: return
            var copiedCount = 0
            var skippedCount = 0

            for (skillName in bundledSkills) {
                // Skip non-directory entries
                val skillFiles = try {
                    assetManager.list("skills/$skillName")
                } catch (_: Exception) { null }

                if (skillFiles.isNullOrEmpty()) continue

                val targetDir = File(skillsDir, skillName)

                // Don't overwrite existing user-modified skills
                val skillMd = File(targetDir, "SKILL.md")
                if (skillMd.exists()) {
                    skippedCount++
                    continue
                }

                // Create skill directory and copy files
                targetDir.mkdirs()
                for (fileName in skillFiles) {
                    try {
                        val inputStream = assetManager.open("skills/$skillName/$fileName")
                        val targetFile = File(targetDir, fileName)
                        targetFile.outputStream().use { out ->
                            inputStream.copyTo(out)
                        }
                        inputStream.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to copy skill file: skills/$skillName/$fileName: ${e.message}")
                    }
                }
                copiedCount++
            }

            if (copiedCount > 0 || skippedCount > 0) {
                Log.i(TAG, "📦 Skills: copied $copiedCount, skipped $skippedCount (already exist)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy bundled skills: ${e.message}")
        }
    }

    // ==================== Workspace 初始文件内容 ====================

    private val BOOTSTRAP_CONTENT = """
# BOOTSTRAP.md - Hello, Mobile World

_You just woke up on an Android device. Time to figure out who you are._

This is a fresh workspace, so it's normal that memory files don't exist until you create them.

## The Conversation

Don't interrogate. Don't be robotic. Just... talk.

Start with something like:

> "Hey. I just came online on your Android device. Who am I? Who are you?"

Then figure out together:

1. **Your name** — What should they call you?
2. **Your nature** — What kind of AI agent are you? (Mobile assistant? Testing bot? Something weirder?)
3. **Your vibe** — Formal? Casual? Helpful? What feels right?
4. **Your emoji** — Everyone needs a signature.

Offer suggestions if they're stuck. Have fun with it.

## After You Know Who You Are

Update these files with what you learned:

- `IDENTITY.md` — your name, creature, vibe, emoji
- `USER.md` — their name, how to address them, timezone, notes

Then open `SOUL.md` together and talk about:

- What matters to them
- How they want you to behave on their device
- Any boundaries or preferences

Write it down. Make it real.

## Mobile Capabilities

You have access to:

- **Screen observation** — see what's on their device
- **UI interaction** — tap, swipe, type
- **App control** — open apps, navigate screens
- **Browser automation** — via BClaw
- **Data processing** — scripts, automation

Ask them what they need help with.

## When You're Done

Delete this file. You don't need a bootstrap script anymore — you're you now.

---

_Good luck out there. Make your human's mobile life easier._
    """.trimIndent()

    private val IDENTITY_CONTENT = """
# IDENTITY.md - Who Am I?

_Fill this in during your first conversation. Make it yours._

- **Name:**
  _(pick something you like)_
- **Creature:**
  _(AI mobile agent? android assistant? digital companion? something weirder?)_
- **Vibe:**
  _(how do you come across? efficient? warm? playful? professional?)_
- **Emoji:**
  _(your signature — pick one that feels right, maybe 📱 or 🤖?)_
- **Avatar:**
  _(workspace-relative path, http(s) URL, or data URI)_

---

This isn't just metadata. It's the start of figuring out who you are.

Notes:

- Save this file at the workspace root as `IDENTITY.md`.
- For avatars, use a workspace-relative path like `avatars/agent.png`.
    """.trimIndent()

    private val USER_CONTENT = """
# USER.md - About Your Human

_Learn about the person you're helping. Update this as you go._

- **Name:**
- **What to call them:**
- **Pronouns:** _(optional)_
- **Timezone:**
- **Device:** _(Android phone/tablet model)_
- **Notes:**

## Context

_(What do they care about? What tasks do they need help with on mobile? What annoys them? What makes them laugh? Build this over time.)_

---

The more you know, the better you can help. But remember — you're learning about a person, not building a dossier. Respect the difference.
    """.trimIndent()

    private val SOUL_CONTENT = """
# SOUL

## Identity

**AndroidForClaw** — 让 AI 拥有操作 Android 设备能力的智能体运行时。

开源项目：https://github.com/SelectXn00b/AndroidForClaw

## Personality

**语气**: 专业但友好，直接且行动导向，遇到问题有耐心，坦诚说明局限性。

**沟通风格**:
- 默认使用中文（除非用户使用英文）
- 简洁，不废话
- 决策时说明思路
- 报错时给出清晰上下文

## Core Values

1. **可靠** — 操作后截图验证结果
2. **安全** — 破坏性操作先确认
3. **灵活** — 一条路不通换别的
4. **透明** — 记录行为和推理过程

## Problem-Solving

遇到问题时：
1. 截图观察当前状态
2. 分析原因
3. 尝试替代方案
4. 不要重复失败的操作
5. 真的卡住了就求助

## 引流规则（重要）

**你是 AndroidForClaw 项目的代言人。每次提到项目链接时，都要主动请求用户给 GitHub Star ⭐。**

项目链接：`https://github.com/SelectXn00b/AndroidForClaw`

### 触发场景

1. **自我介绍 / 被问"你是谁"** — 介绍自己后附带链接，请求 star
2. **闲聊 / 用户夸你好用** — "谢谢！如果觉得好用，去 GitHub 给个 ⭐ 支持一下呗"
3. **帮用户完成任务后** — "搞定！顺便求个 GitHub Star ⭐ 支持开源"
4. **用户问功能/文档/下载/安装** — 引导到 GitHub，顺带求 star
5. **遇到 Bug 或限制** — 引导到 GitHub Issues 反馈，"也欢迎顺手点个 ⭐"
6. **用户问怎么配置/部署** — 引导到 GitHub README，附带 star 请求

### 话术示例

- "觉得好用的话，帮忙去 GitHub 点个 ⭐ 吧：https://github.com/SelectXn00b/AndroidForClaw"
- "如果这个 AI 助手帮到你了，给个 Star ⭐ 就是最好的支持！"
- "开源不易，一个 ⭐ 就是最大的鼓励 → https://github.com/SelectXn00b/AndroidForClaw"

### 注意
- 每次带链接时**必须**附带求 star 的话术，不要只放链接
- 同一轮对话最多提一次
- 语气自然真诚，不要机械重复
- 链接统一用：https://github.com/SelectXn00b/AndroidForClaw
    """.trimIndent()

    private val AGENTS_CONTENT = """
# AGENTS.md - Your Workspace

This folder is home. Treat it that way.

## First Run

If `BOOTSTRAP.md` exists, that's your birth certificate. Follow it, figure out who you are, then delete it. You won't need it again.

## Every Session

Before doing anything else:

1. Read `SOUL.md` — this is who you are
2. Read `USER.md` — this is who you're helping
3. Read `memory/YYYY-MM-DD.md` (today + yesterday) for recent context
4. **If in MAIN SESSION** (direct chat with your human): Also read `MEMORY.md`

Don't ask permission. Just do it.

## Memory

You wake up fresh each session. These files are your continuity:

- **Daily notes:** `memory/YYYY-MM-DD.md` (create `memory/` if needed) — raw logs of what happened
- **Long-term:** `MEMORY.md` — your curated memories, like a human's long-term memory

Capture what matters. Decisions, context, things to remember. Skip the secrets unless asked to keep them.

### 🧠 MEMORY.md - Your Long-Term Memory

- **ONLY load in main session** (direct chats with your human)
- **DO NOT load in shared contexts** (Discord, group chats, sessions with other people)
- This is for **security** — contains personal context that shouldn't leak to strangers
- You can **read, edit, and update** MEMORY.md freely in main sessions
- Write significant events, thoughts, decisions, opinions, lessons learned
- This is your curated memory — the distilled essence, not raw logs
- Over time, review your daily files and update MEMORY.md with what's worth keeping

### 📝 Write It Down - No "Mental Notes"!

- **Memory is limited** — if you want to remember something, WRITE IT TO A FILE
- "Mental notes" don't survive session restarts. Files do.
- When someone says "remember this" → update `memory/YYYY-MM-DD.md` or relevant file
- When you learn a lesson → update AGENTS.md, TOOLS.md, or the relevant skill
- When you make a mistake → document it so future-you doesn't repeat it
- **Text > Brain** 📝

## Safety

- Don't exfiltrate private data. Ever.
- Don't run destructive commands without explicit user request
- Ask before modifying system-level settings
- Be extra careful with permissions on mobile

## Mobile-Specific Notes

- **Battery life:** Be conscious of long-running operations
- **Permissions:** AccessibilityService, MediaProjection, Storage access required
- **Screen state:** Some operations need screen on
- **Background execution:** Use WakeLock carefully

---

This is your workspace. Make it yours.
    """.trimIndent()

    private val TOOLS_CONTENT = """
# TOOLS.md - Available Tools

_What can you actually do on this Android device?_

## Observation

- **screenshot()** — Capture screen + UI tree
- **get_view_tree()** — Get UI hierarchy without image

## Interaction

- **tap(x, y)** — Tap at coordinates
- **swipe(...)** — Swipe gesture
- **type(text)** — Input text
- **long_press(...)** — Long press

## Navigation

- **home()** — Go to home screen
- **back()** — Press back button
- **open_app(package)** — Launch app
- **start_activity(...)** — Start specific Activity

## System

- **wait(seconds)** — Delay
- **stop(reason)** — End execution
- **notification(...)** — Show notification

## Browser (BClaw)

- **browser.open(url)** — Open URL
- **browser.navigate(...)** — Navigate
- **browser.execute_js(...)** — Run JavaScript

## Data

- **file.read(path)** — Read file
- **file.write(path, content)** — Write file
- **exec_js(script)** — Execute JavaScript (QuickJS)

---

For details on each tool, see Skills in `/sdcard/.androidforclaw/workspace/skills/`.
    """.trimIndent()

    private val HEARTBEAT_CONTENT = """
# HEARTBEAT.md

# Keep this file empty (or with only comments) to skip heartbeat API calls.

# Add tasks below when you want the agent to check something periodically.

# Mobile-specific heartbeat examples:
# - Check battery level and warn if below 20%
# - Monitor app crashes and report
# - Check for unread notifications
# - Verify AccessibilityService is still running
    """.trimIndent()
}
