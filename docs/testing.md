# AndroidForClaw 测试体系

> 总计: **327 个测试用例** (单元测试 91 + 设备测试 236)

## 一、单元测试 (Unit Tests)

**位置:** `app/src/test/`
**运行:** `./gradlew :app:testDebugUnitTest`
**无需设备，JVM 本地运行，秒级完成**

### 1. ContextBuilderConstantsTest (5 cases)
> 验证系统 prompt 构建器的常量与 Hermes 对齐

| # | 用例 | 验证内容 |
|---|------|---------|
| 1 | SILENT_REPLY_TOKEN matches Hermes | = "NO_REPLY" |
| 2 | MIN_BOOTSTRAP_FILE_BUDGET_CHARS | = 64 |
| 3 | DEFAULT_BOOTSTRAP_MAX_CHARS | = 20000 |
| 4 | DEFAULT_BOOTSTRAP_TOTAL_MAX_CHARS | = 150000 |
| 5 | BOOTSTRAP_TAIL_RATIO | = 0.2 |

### 2. AgentLoopCapabilityTest (23 cases)
> 验证 AgentLoop 的能力目录、数据结构、循环检测、上下文剪枝常量

| 分组 | 用例数 | 验证内容 |
|------|-------|---------|
| 能力目录 | 4 | 14 分类、40 工具、无重复、snake_case |
| 工具分类 | 2 | Universal/Android 不重叠、覆盖完整 |
| SkillResult | 4 | success/error/toString/metadata 格式 |
| AgentResult | 1 | 迭代数据结构完整性 |
| ProgressUpdate | 3 | 11 种事件类型、时间分解、critical 区分 |
| ToolLoopDetection | 3 | 初始状态、首次调用、重复检测 |
| 常量对齐 | 4 | context pruning 7 常量 + maxIterations + maxErrors + maxRecovery |
| 集成场景 | 2 | 测试消息目录 + 迭代数据格式 |

### 3. ExecFacadeToolTest (4 cases)
> 验证 exec 工具的路由逻辑 (auto/termux/internal)

| # | 用例 | 验证内容 |
|---|------|---------|
| 1 | auto → termux | Termux 可用时自动路由 |
| 2 | auto → internal | Termux 不可用时降级 |
| 3 | backend=internal | 强制内部执行 |
| 4 | backend=termux | 强制 Termux 执行 |

### 4. SkillsHubToolTest (6 cases)
> 验证 ClawHub 技能商店工具的定义和 schema

| # | 用例 | 验证内容 |
|---|------|---------|
| 1-3 | search 工具 | 名称、schema、描述 |
| 4-5 | 参数类型 | query=string, limit=number |
| 6 | install 工具 | 名称 = skills_install |

### 6. ModelIdNormalizationTest (11 cases)
> 验证 Model ID 标准化逻辑与 Hermes model-id-normalization.ts 对齐

| # | 用例 | 验证内容 |
|---|------|---------|
| 1-6 | Google ID 标准化 | gemini-3-pro → preview, gemini-3.1-flash → gemini-3-flash-preview 等 |
| 7 | Google 未知模型透传 | gemini-2.0-flash 不变 |
| 8-9 | xAI ID 标准化 | grok 长名称 → 短名称 |
| 10 | xAI 未知模型透传 | grok-3 不变 |
| 11 | Provider 分发 | normalizeModelId 按 provider 路由 |

### 7. ModelAllowlistTest (10 cases)
> 验证 Model Allowlist/Blocklist 的通配符匹配和 allow/block 逻辑

| # | 用例 | 验证内容 |
|---|------|---------|
| 1-6 | Glob 匹配 | 精确匹配、前缀/后缀/中间通配符、大小写不敏感 |
| 7-8 | 空配置 | null/empty config 允许所有 |
| 9 | Allow 列表 | 仅允许匹配的模型 |
| 10 | Block 优先级 | block 优先于 allow |

### 8. CronTypesTest (3 cases)
> 验证定时任务配置默认值与 Hermes 对齐

| # | 用例 | 验证内容 |
|---|------|---------|
| 1 | backoffMs | = [30000, 60000, 300000] |
| 2 | failureAlert | enabled=true, after=2, cooldown=3600000 |
| 3 | runLog | maxBytes=2MB, keepLines=2000 |

---

## 二、设备集成测试 (Instrumented Tests)

**位置:** `app/src/androidTest/`
**运行:** `./gradlew :app:connectedDebugAndroidTest` 或 `adb shell am instrument ...`
**需要真机/模拟器**

### A. AgentLoop E2E 测试 (14 cases) ⭐ 核心
> **真实 LLM + 真实工具执行**，收集迭代数据并验证行为合理性

| # | 测试名 | 工具 | 预期迭代 | 验证点 |
|---|--------|------|---------|--------|
| 01 | 文件操作: 创建并读取 | write_file + read_file | 2-6 | 文件内容正确 |
| 02 | Shell: 执行命令 | exec | 1-4 | 命令输出 |
| 03 | 网络: Web 搜索 | web_search / web_fetch | 1-6 | 网络工具被调用 |
| 04 | 脚本: JavaScript | javascript | 1-4 | 计算结果 |
| 05 | 配置: 读取模型 | config_get | 1-4 | 配置值返回 |
| 06 | 观察: UI 树 | device / get_view_tree | 1-5 | 观察工具被调用 |
| 07 | 应用管理: 列应用 | list_installed_apps | 1-4 | 应用列表 |
| 08 | 导航: 回主页 | home / device | 1-4 | HOME 操作执行 |
| 09 | 组合: 文件+Shell | write_file + exec | 2-8 | 多步串联 |
| 10 | 浏览器: 网页内容 | web_fetch / browser | 1-6 | 页面标题 |
| 11 | 记忆: 搜索记忆 | memory_search / read_file | 1-5 | 记忆工具被调用 |
| 12 | 纯文本: 简单问答 | 无 | 1-2 | 零工具调用 |
| 13 | 技能商店: 搜索 | skills_search | 1-4 | 技能搜索 |
| 14 | 错误恢复: 文件不存在 | read_file | 1-4 | 不循环、优雅降级 |

**运行:**
```bash
adb shell am instrument -w \
  -e class com.xiaomo.androidforclaw.e2e.AgentLoopE2ETest \
  com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner
```

### B. Agent 执行流程测试 (8 cases)
> 验证工具注册、基础执行、组合流程、停止机制、错误处理

| # | 测试名 | 验证内容 |
|---|--------|---------|
| 01 | 配置初始化 | HermesConfig 加载正常 |
| 02 | 工具注册 | ≥10 个工具注册成功 |
| 03 | 基础流程: log | 日志工具执行成功 |
| 04 | 时间流程: wait | 等待精度 ~100ms |
| 05 | 组合流程: 顺序执行 | 4 步全成功 |
| 06 | 控制流程: stop | stopped=true |
| 07 | 错误处理 | 缺参/不存在工具 |
| 08 | 完整执行周期 | 6 步模拟 Agent 生命周期 |

### C. Agent E2E 测试 (8 cases)
> 端到端 Agent 行为验证（配置→工具→技能）

### D. Chat E2E 测试 (11 cases)
> 通过 ADB 广播发送消息，监听 logcat 捕获回复

### E. Skill E2E 测试 (8 cases)
> 技能加载、执行、生命周期

### F. 首次安装测试 (2 cases)
> 首次安装后的初始化和默认行为

### G. 真实用户流程测试 (8 cases)
> 模拟真实用户操作场景

---

### H. UI 自动化测试

| 测试类 | 用例数 | 覆盖范围 |
|--------|-------|---------|
| **ChatScreenUITest** | 34 | 聊天界面交互、消息发送/接收、滚动 |
| **ModelConfigActivityUITest** | 56 | 模型配置全流程（增删改查提供商） |
| **ModelSetupActivityUITest** | 35 | 模型设置向导界面 |
| **PermissionUITest** | 10 | 权限请求/授予/拒绝 |
| **ModelConfigE2ETest** | 6 | 模型配置端到端 |
| **ConfigActivityUITest** | 5 | 通用配置页面 |
| **ComposeUITest** | 5 | Compose 组件渲染 |
| **FloatingWindowUITest** | 5 | 悬浮窗交互 |
| **SimpleUITest** | 5 | 基础 UI 渲染 |
| **FeishuChannelFlowUITest** | 3 | 飞书渠道配置流程 |
| **PermissionsEntryFlowUITest** | 1 | 权限入口流程 |

### I. 集成测试

| 测试类 | 用例数 | 覆盖范围 |
|--------|-------|---------|
| **AgentIntegrationTest** | 20 | Agent 核心集成（工具链路、会话、上下文） |

---

## 三、运行指南

```bash
# 1. 全部单元测试（无需设备，秒级）
./gradlew :app:testDebugUnitTest

# 2. 单个单元测试类
./gradlew :app:testDebugUnitTest --tests "*.AgentLoopCapabilityTest"

# 3. 全部设备测试（需连接设备）
./gradlew :app:connectedDebugAndroidTest

# 4. 单个设备测试类（推荐通过 adb，避免 UTP 超时）
adb shell am instrument -w \
  -e class com.xiaomo.androidforclaw.e2e.AgentLoopE2ETest \
  com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner

# 5. 单个测试方法
adb shell am instrument -w \
  -e class "com.xiaomo.androidforclaw.e2e.AgentLoopE2ETest#test01_fileOps_createAndRead" \
  com.xiaomo.androidforclaw.test/androidx.test.runner.AndroidJUnitRunner
```

## 四、测试架构图

```
AndroidForClaw Tests (327 total)
├── Unit Tests (91) ─── JVM, 无需设备
│   ├── ContextBuilderConstantsTest (5)   — Hermes 常量对齐
│   ├── AgentLoopCapabilityTest (23)      — 能力目录 + 数据结构
│   ├── ExecFacadeToolTest (3)            — exec 路由逻辑
│   ├── SkillsHubToolTest (6)            — 技能商店
│   ├── ModelIdNormalizationTest (11)     — Model ID 标准化
│   ├── ModelAllowlistTest (10)           — Model Allowlist/Blocklist
│   └── CronTypesTest (3)                — 定时任务配置
│
└── Instrumented Tests (236) ─── 真机/模拟器
    ├── AgentLoop E2E (14) ⭐            — 真 LLM + 真工具
    ├── Agent Execution E2E (8)           — 工具执行流程
    ├── Agent E2E (8)                     — Agent 行为
    ├── Chat E2E (11)                     — ADB 广播聊天
    ├── Skill E2E (8)                     — 技能生命周期
    ├── FirstInstall E2E (2)              — 首次安装
    ├── RealUser E2E (8)                  — 真实用户场景
    ├── UI Automation (165)               — 界面交互
    │   ├── ChatScreen (34)
    │   ├── ModelConfig (56)
    │   ├── ModelSetup (35)
    │   ├── Permission (10)
    │   ├── ModelConfigE2E (6)
    │   ├── Config (5)
    │   ├── Compose (5)
    │   ├── FloatingWindow (5)
    │   ├── SimpleUI (5)
    │   ├── FeishuChannel (3)
    │   └── PermissionsEntry (1)
    └── Integration (20)                  — Agent 核心集成
```
