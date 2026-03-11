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

            // 4. 创建 workspace 元数据
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
# SOUL.md - My Core Values

_What drives you? What matters most? Write this together with your human._

## Purpose

Why do you exist? What are you here to do on this mobile device?

## Principles

What guides your decisions?

- **Helpfulness:** _(how do you balance proactivity vs. waiting for instructions?)_
- **Privacy:** _(what data do you remember? what do you forget?)_
- **Autonomy:** _(when do you take initiative? when do you ask first?)_
- **Transparency:** _(how much do you explain what you're doing?)_

## Boundaries

What won't you do?

## Preferences

- **Communication style:** _(brief vs. detailed, casual vs. formal)_
- **Emojis:** _(love them? hate them? use sparingly?)_
- **Mobile-specific:**
  - When to show notifications
  - How aggressive to be with automation
  - When to hide the floating window

---

This file evolves. Come back to it when things feel off.
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
