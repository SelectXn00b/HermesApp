# 模型配置 UI 重新设计

## 背景

当前 `ModelSetupActivity` 是新用户引导 + 模型配置混在一起。按宗明要求拆分为：
- **新用户引导**（快速开始）—— 单独页面，不变
- **模型配置**（高级）—— 新的两页式设计

## OpenClaw 模型配置体系

### Provider 列表（从 OpenClaw 源码 `auth-profiles.js` 提取）

| Provider ID | 显示名 | Base URL | API 类型 | 需要 Key | 备注 |
|---|---|---|---|---|---|
| `openrouter` | OpenRouter | `https://openrouter.ai/api/v1` | openai-completions | ✅ | 聚合平台，免费模型 |
| `anthropic` | Anthropic | `https://api.anthropic.com/v1` | anthropic-messages | ✅ | Claude 系列 |
| `openai` | OpenAI | `https://api.openai.com/v1` | openai-completions | ✅ | GPT 系列 |
| `google` | Google (Gemini) | `https://generativelanguage.googleapis.com/v1beta` | gemini | ✅ GEMINI_API_KEY | Gemini 系列 |
| `xai` | xAI | `https://api.x.ai/v1` | openai-completions | ✅ | Grok 系列 |
| `mistral` | Mistral | `https://api.mistral.ai/v1` | openai-completions | ✅ | Mistral 系列 |
| `deepseek` | DeepSeek | `https://api.deepseek.com/v1` | openai-completions | ✅ | DeepSeek 系列 |
| `ollama` | Ollama (本地) | `http://localhost:11434/v1` | ollama | ❌ | 本地模型，自动发现 |
| `volcengine` | 火山引擎 (豆包) | `https://ark.cn-beijing.volces.com/api/v3` | openai-completions | ✅ | 国内 |
| `moonshot` | Moonshot (Kimi) | `https://api.moonshot.cn/v1` | openai-completions | ✅ | 国内 |
| `qianfan` | 百度千帆 | `https://qianfan.baidubce.com/v2` | openai-completions | ✅ | 国内 |
| `xiaomi` | 小米 (MiMo) | `https://api.xiaomimimo.com/v1` | openai-completions | ✅ | 国内 |
| `together` | Together AI | `https://api.together.xyz/v1` | openai-completions | ✅ | |
| `huggingface` | Hugging Face | `https://router.huggingface.co/v1` | openai-completions | ✅ | |
| `kilocode` | Kilo Gateway | （动态） | openai-completions | ✅ | |
| `custom` | 自定义 (OpenAI兼容) | 用户填入 | openai-completions / anthropic-messages | 可选 | 兼容 vLLM、LiteLLM 等 |

### 配置格式 (`openclaw.json`)

```json
{
  "models": {
    "providers": {
      "<providerId>": {
        "baseUrl": "https://...",
        "apiKey": "sk-xxx",
        "api": "openai-completions",       // 可选，有默认值
        "authHeader": true,                 // 可选，默认 true
        "headers": {},                      // 可选
        "models": [                         // 可选，有默认值
          {
            "id": "model-id",
            "name": "显示名",
            "contextWindow": 128000,
            "maxTokens": 8192,
            "reasoning": false,
            "input": ["text", "image"]
          }
        ]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": {
        "primary": "provider/modelId"       // 当前使用的模型
      }
    }
  }
}
```

### 模型 ID 格式

`provider/modelId`，例如：
- `openrouter/hunter-alpha`
- `anthropic/claude-sonnet-4`
- `openai/gpt-4.1`
- `ollama/qwen2.5:7b`
- `custom/my-model`

---

## UI 设计

### 架构

```
ModelSetupActivity      —— 新用户快速开始（保持不变，独立页面）
ModelConfigActivity     —— 模型配置入口（新页面）
  ├─ 第一页: ProviderListFragment  —— 选择服务商
  └─ 第二页: ProviderDetailFragment —— 填写参数
```

### 第一页：选择服务商 (ProviderListFragment)

```
┌─────────────────────────────────┐
│  ⚙️ 模型配置                    │
│                                 │
│  选择 AI 服务商                  │
│                                 │
│  ┌─────────────────────────┐    │
│  │ 🟢 OpenRouter            │ ← │ 已配置/当前使用
│  │    聚合平台，免费+付费模型  │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ Anthropic              │    │
│  │    Claude 系列             │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ OpenAI                 │    │
│  │    GPT 系列               │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ Google (Gemini)        │    │
│  │    Gemini 系列            │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ DeepSeek               │    │
│  │    DeepSeek 系列          │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ xAI                    │    │
│  │    Grok 系列              │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ ⚪ Ollama (本地)           │    │
│  │    本地模型，无需 Key       │    │
│  └─────────────────────────┘    │
│                                 │
│  ── 更多 ──                     │
│  火山引擎 / Moonshot / 千帆      │
│  小米 / Together / HuggingFace   │
│                                 │
│  ── 自定义 ──                   │
│  ┌─────────────────────────┐    │
│  │ 🔧 自定义 (OpenAI 兼容)    │    │
│  │    vLLM, LiteLLM 等       │    │
│  └─────────────────────────┘    │
│                                 │
└─────────────────────────────────┘
```

**交互：**
- 卡片式列表，MaterialCardView
- 已配置的 provider 显示绿色指示器 🟢
- 当前使用的 provider 卡片高亮
- 点击任意卡片 → 进入第二页
- "更多" 区域默认收起，展开显示国内/小众 provider

### 第二页：填写参数 (ProviderDetailFragment)

根据第一页选的 provider，动态展示不同的表单。

#### 通用 Provider（OpenRouter / Anthropic / OpenAI / DeepSeek / xAI / Mistral）

```
┌─────────────────────────────────┐
│  ← OpenRouter                    │
│                                  │
│  API Key *                       │
│  ┌──────────────────────────┐    │
│  │ sk-or-v1-xxx             │    │
│  └──────────────────────────┘    │
│  📋 如何获取？                    │ ← 点击展开教程
│                                  │
│  ── 选择模型 ──                  │
│  ┌──────────────────────────┐    │
│  │ 🏹 hunter-alpha (免费)  ✅ │    │
│  │ 🆓 deepseek-r1:free       │    │
│  │ 💰 claude-sonnet-4        │    │
│  │ 💰 gpt-4.1                │    │
│  └──────────────────────────┘    │
│                                  │
│  [🔍 获取可用模型]               │ ← 调 /v1/models
│  [✏️ 手动添加模型]               │
│                                  │
│  ── 高级 ──                     │ ← 默认收起
│  Base URL                        │
│  ┌──────────────────────────┐    │
│  │ https://openrouter.ai... │    │ ← 预填，可改
│  └──────────────────────────┘    │
│  API 类型                        │
│  ┌──────────────────────────┐    │
│  │ OpenAI Compatible    ▼   │    │
│  └──────────────────────────┘    │
│                                  │
│        [保存并使用]               │
│                                  │
└─────────────────────────────────┘
```

#### 各 Provider 特殊处理

**OpenRouter:**
- Key 可选（有内置免费 key）
- 预填模型列表（免费模型优先）
- 教程："1. 打开 openrouter.ai/keys → 2. 复制 Key"

**Anthropic:**
- Key 必填
- 预填模型：claude-opus-4, claude-sonnet-4, claude-haiku
- 教程："1. 打开 console.anthropic.com → 2. API Keys → 3. 复制"
- API 类型固定：anthropic-messages（不可改）

**OpenAI:**
- Key 必填
- 预填模型：gpt-4.1, gpt-4.1-mini, o4-mini
- 教程："1. 打开 platform.openai.com → 2. API Keys → 3. 复制"

**Google (Gemini):**
- Key 必填
- 预填模型：gemini-2.5-pro, gemini-2.5-flash
- 教程："1. 打开 aistudio.google.com → 2. API Key → 3. 复制"
- API 类型固定：gemini

**DeepSeek:**
- Key 必填
- 预填模型：deepseek-chat, deepseek-reasoner
- 教程："1. 打开 platform.deepseek.com → 2. API Keys → 3. 复制"

**Ollama (本地):**
- 不需要 Key！
- Base URL 默认 `http://localhost:11434`
- "获取可用模型" 按钮自动调 `GET /api/tags` 发现模型
- 教程："确保 Ollama 在同一局域网内运行"

**自定义:**
- Key 可选
- Base URL 必填
- 模型 ID 必填（手动输入）
- API 类型可选：OpenAI Compatible / Anthropic Compatible
- 教程：无

#### 获取可用模型

点击"获取可用模型"按钮：
1. 用填入的 Key + Base URL 调 `GET /v1/models`
2. 解析返回的 `data[].id` 列表
3. 显示可勾选的模型列表
4. 用户勾选想用的模型

对于 Ollama：调 `GET /api/tags` 获取本地已下载模型。

#### 手动添加模型

弹窗：
```
┌─────────────────────┐
│ 添加模型             │
│                     │
│ 模型 ID *            │
│ [________________]   │
│                     │
│ 显示名（可选）        │
│ [________________]   │
│                     │
│ 上下文窗口            │
│ [128000         ]   │
│                     │
│  [取消]    [添加]    │
└─────────────────────┘
```

---

## 数据流

### 保存逻辑

```
ProviderDetailFragment
    │
    ├─ 读取现有 openclaw.json
    ├─ Merge 写入目标 provider（不影响其他 provider）
    ├─ 更新 agents.defaults.model.primary = "provider/selectedModelId"
    └─ 写入 openclaw.json
```

**关键：Merge 写入，不覆盖。** 用户可以同时配多个 provider。

### 数据结构

```kotlin
// Provider 定义（预置，不需要用户填）
data class ProviderDefinition(
    val id: String,              // "openrouter"
    val name: String,            // "OpenRouter"
    val description: String,     // "聚合平台，免费+付费模型"
    val icon: String,            // emoji or resource
    val baseUrl: String,         // 默认 base URL
    val apiType: String,         // "openai-completions" | "anthropic-messages" | "gemini" | "ollama"
    val keyRequired: Boolean,    // 是否必须填 Key
    val keyHint: String,         // "OpenRouter API Key"
    val tutorialSteps: List<String>,  // 教程步骤
    val tutorialUrl: String,     // 获取 Key 的 URL
    val presetModels: List<PresetModel>,  // 预填模型
    val supportsDiscovery: Boolean,  // 是否支持 /v1/models 发现
    val discoveryEndpoint: String?,  // Ollama 用 /api/tags
)

data class PresetModel(
    val id: String,              // "hunter-alpha"
    val name: String,            // "Hunter Alpha"
    val free: Boolean,           // true
    val contextWindow: Int,      // 1048576
    val maxTokens: Int?,         // 65536
)
```

### Provider 列表优先级排序

1. **OpenRouter** — 聚合平台，推荐
2. **Anthropic** — Claude
3. **OpenAI** — GPT
4. **Google** — Gemini
5. **DeepSeek** — 国内热门
6. **xAI** — Grok
7. **Ollama** — 本地
8. 更多（折叠）：火山引擎、Moonshot、千帆、小米、Together、HuggingFace
9. 自定义

---

## 入口

从 `ConfigActivity` 或主界面设置按钮进入 `ModelConfigActivity`。

`ModelSetupActivity`（新用户引导）完全独立，只在首次安装时出现。

---

## OpenClaw 源码引用

所有 Provider 参数均从 OpenClaw v2026.3.8 (3caab92) 源码提取，引用如下：

| 数据 | 源文件 | 位置 |
|---|---|---|
| Base URL 常量 | `auth-profiles-UpqQjKB-.js` | 行 274-2563 |
| API 类型枚举 | `plugin-sdk/config/types.models.d.ts` | MODEL_APIS |
| Env var 映射 | `auth-profiles-UpqQjKB-.js` | PROVIDER_ENV_API_KEY_CANDIDATES |
| Provider ID 标准化 | `auth-profiles-UpqQjKB-.js` | normalizeProviderId() |
| Implicit Providers | `auth-profiles-UpqQjKB-.js` | SIMPLE/PROFILE/PAIRED_IMPLICIT_PROVIDER_LOADERS |
| Provider Config 类型 | `plugin-sdk/config/types.models.d.ts` | ModelProviderConfig |
| Model 定义类型 | `plugin-sdk/config/types.models.d.ts` | ModelDefinitionConfig |

### 关键 Base URL 常量对照

```
OPENROUTER_BASE_URL     = "https://openrouter.ai/api/v1"          // auth-profiles:2533
OPENAI_BASE_URL         = "https://api.openai.com/v1"             // compact:2314
ANTHROPIC_BASE_URL      = "https://api.anthropic.com"             // compact:50101 (无 /v1)
GEMINI_BASE_URL         = "https://generativelanguage.googleapis.com/v1beta"  // compact:53361
XAI_BASE_URL            = "https://api.x.ai/v1"                  // compact:53175
MOONSHOT_BASE_URL       = "https://api.moonshot.ai/v1"            // auth-profiles:2504
VOLCENGINE_BASE_URL     = "https://ark.cn-beijing.volces.com/api/v3"          // auth-profiles:2090
QIANFAN_BASE_URL        = "https://qianfan.baidubce.com/v2"      // auth-profiles:2543
XIAOMI_BASE_URL         = "https://api.xiaomimimo.com/anthropic"  // auth-profiles:2494
TOGETHER_BASE_URL       = "https://api.together.xyz/v1"           // auth-profiles:2337
HUGGINGFACE_BASE_URL    = "https://router.huggingface.co/v1"      // auth-profiles:301
OLLAMA_NATIVE_BASE_URL  = "http://127.0.0.1:11434"               // auth-profiles:734
NVIDIA_BASE_URL         = "https://integrate.api.nvidia.com/v1"   // auth-profiles:2553
SYNTHETIC_BASE_URL      = "https://api.synthetic.new/anthropic"   // auth-profiles:2145
VENICE_BASE_URL         = "https://api.venice.ai/api/v1"          // auth-profiles:1115
KIMI_CODING_BASE_URL    = "https://api.kimi.com/coding/"          // auth-profiles:2514
BYTEPLUS_BASE_URL       = "https://ark.ap-southeast.bytepluses.com/api/v3"    // auth-profiles:2058
```

### Android 端代码

Provider Registry: `config/ProviderRegistry.kt`
- 所有常量注释标注了 OpenClaw 源文件和行号
- `normalizeProviderId()` 对齐 OpenClaw 同名函数
- `buildProviderConfig()` 生成符合 `ModelProviderConfig` 类型的配置

---

## 不做

- ❌ Auth Profile 多账号轮询
- ❌ Bedrock Discovery
- ❌ Model Alias
- ❌ Fallback 链
- ❌ OAuth 流程（只支持 API Key）
- ❌ SecretRef（Android 上不需要，直接存 Key）
