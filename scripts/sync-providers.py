#!/usr/bin/env python3
"""
从 OpenClaw 源码中提取 provider 定义，生成 providers.json 供 AndroidForClaw 使用。

用法: python3 scripts/sync-providers.py [openclaw_repo_path]
输出: app/src/main/assets/providers.json

提取来源:
- extensions/*/openclaw.plugin.json → provider ID, auth env vars, auth choices
- extensions/*/provider-catalog.ts → base URL, API type, model list
- src/agents/auth-profiles/ → base URL constants
"""

import json
import os
import re
import sys
from pathlib import Path

# AndroidForClaw 支持的 provider (排除 PC-only 如 copilot-proxy, amazon-bedrock 等)
SUPPORTED_PROVIDERS = {
    "openrouter", "anthropic", "openai", "google", "xai",
    "deepseek", "ollama", "volcengine", "moonshot", "qianfan",
    "xiaomi", "mistral", "together", "huggingface", "nvidia",
    "kimi-coding", "byteplus", "chutes", "venice", "synthetic",
    "sglang", "vllm",
}

# Provider 元数据（OpenClaw 插件 JSON 中没有的信息，需要补充）
PROVIDER_META = {
    "openrouter": {
        "name": "OpenRouter",
        "description": "聚合平台，多种模型",
        "group": "primary",
        "order": 10,
        "keyRequired": False,
        "keyHint": "OpenRouter API Key (sk-or-v1-...)",
        "tutorialUrl": "https://openrouter.ai/keys",
        "tutorialSteps": ["打开 openrouter.ai/keys", "登录或注册账号", "点击 \"Create Key\"", "复制 API Key"],
        "supportsDiscovery": True,
    },
    "anthropic": {
        "name": "Anthropic",
        "description": "Claude 系列",
        "group": "primary",
        "order": 20,
        "keyRequired": True,
        "keyHint": "Anthropic API Key (sk-ant-...)",
        "tutorialUrl": "https://console.anthropic.com/settings/keys",
        "tutorialSteps": ["打开 console.anthropic.com", "登录或注册账号", "进入 API Keys 页面", "点击 \"Create Key\" 并复制"],
        "supportsDiscovery": False,
    },
    "openai": {
        "name": "OpenAI",
        "description": "GPT 系列",
        "group": "primary",
        "order": 30,
        "keyRequired": True,
        "keyHint": "OpenAI API Key (sk-...)",
        "tutorialUrl": "https://platform.openai.com/api-keys",
        "tutorialSteps": ["打开 platform.openai.com", "登录或注册账号", "进入 API Keys 页面", "点击 \"Create new secret key\" 并复制"],
        "supportsDiscovery": True,
    },
    "google": {
        "name": "Google (Gemini)",
        "description": "Gemini 系列",
        "group": "primary",
        "order": 40,
        "keyRequired": True,
        "keyHint": "Gemini API Key",
        "tutorialUrl": "https://aistudio.google.com/apikey",
        "tutorialSteps": ["打开 aistudio.google.com", "登录 Google 账号", "点击 \"Get API key\"", "创建或选择项目并复制 Key"],
        "supportsDiscovery": False,
    },
    "deepseek": {
        "name": "DeepSeek",
        "description": "DeepSeek 系列",
        "group": "primary",
        "order": 50,
        "keyRequired": True,
        "keyHint": "DeepSeek API Key",
        "tutorialUrl": "https://platform.deepseek.com/api_keys",
        "tutorialSteps": ["打开 platform.deepseek.com", "登录或注册账号", "进入 API Keys 页面", "创建并复制 API Key"],
        "supportsDiscovery": True,
    },
    "xai": {
        "name": "xAI",
        "description": "Grok 系列",
        "group": "primary",
        "order": 60,
        "keyRequired": True,
        "keyHint": "xAI API Key",
        "tutorialUrl": "https://console.x.ai/",
        "tutorialSteps": ["打开 console.x.ai", "登录 X/xAI 账号", "创建 API Key 并复制"],
        "supportsDiscovery": True,
    },
    "ollama": {
        "name": "Ollama (本地)",
        "description": "本地模型，无需 API Key",
        "group": "primary",
        "order": 70,
        "keyRequired": False,
        "keyHint": "API Key (可选)",
        "tutorialUrl": "https://ollama.com/download",
        "tutorialSteps": ["安装 Ollama: ollama.com/download", "运行模型: ollama run qwen2.5:7b", "确保 Ollama 在同一局域网内运行"],
        "supportsDiscovery": True,
        "discoveryEndpoint": "/api/tags",
    },
    "volcengine": {
        "name": "火山引擎 (豆包)",
        "description": "字节跳动大模型平台",
        "group": "more",
        "order": 110,
        "keyRequired": True,
        "keyHint": "火山引擎 API Key",
        "tutorialUrl": "https://console.volcengine.com/ark",
        "tutorialSteps": ["打开 console.volcengine.com", "进入 AI 大模型平台", "创建 API Key 并复制"],
        "supportsDiscovery": True,
    },
    "moonshot": {
        "name": "Moonshot (Kimi)",
        "description": "月之暗面 Kimi 大模型",
        "group": "more",
        "order": 120,
        "keyRequired": True,
        "keyHint": "Moonshot API Key",
        "tutorialUrl": "https://platform.moonshot.cn/console/api-keys",
        "tutorialSteps": ["打开 platform.moonshot.cn", "登录或注册账号", "进入 API 管理页面", "创建并复制 API Key"],
        "supportsDiscovery": True,
    },
    "kimi-coding": {
        "name": "Kimi for Coding",
        "description": "Kimi 编程专用 API（Anthropic 兼容）",
        "group": "more",
        "order": 125,
        "keyRequired": True,
        "keyHint": "Kimi API Key",
        "tutorialUrl": "https://kimi.com/coding",
        "tutorialSteps": ["打开 kimi.com/coding", "注册或登录 Kimi Coding 账号", "获取 API Key"],
        "supportsDiscovery": False,
    },
    "qianfan": {
        "name": "百度千帆",
        "description": "百度文心大模型平台",
        "group": "more",
        "order": 130,
        "keyRequired": True,
        "keyHint": "千帆 API Key",
        "tutorialUrl": "https://qianfan.cloud.baidu.com/",
        "tutorialSteps": ["打开 qianfan.cloud.baidu.com", "登录百度账号", "进入 API Key 管理", "创建并复制 API Key"],
        "supportsDiscovery": True,
    },
    "xiaomi": {
        "name": "小米 (MiMo)",
        "description": "小米 MiMo 大模型",
        "group": "more",
        "order": 140,
        "keyRequired": True,
        "keyHint": "小米 API Key",
        "tutorialUrl": "https://api.xiaomimimo.com/",
        "tutorialSteps": ["打开 api.xiaomimimo.com", "登录小米账号", "获取 API Key"],
        "supportsDiscovery": False,
    },
    "mistral": {
        "name": "Mistral",
        "description": "Mistral AI 系列",
        "group": "more",
        "order": 150,
        "keyRequired": True,
        "keyHint": "Mistral API Key",
        "tutorialUrl": "https://console.mistral.ai/api-keys",
        "tutorialSteps": ["打开 console.mistral.ai", "登录或注册账号", "进入 API Keys 页面", "创建并复制 API Key"],
        "supportsDiscovery": True,
    },
    "together": {
        "name": "Together AI",
        "description": "开源模型云平台",
        "group": "more",
        "order": 160,
        "keyRequired": True,
        "keyHint": "Together API Key",
        "tutorialUrl": "https://api.together.ai/settings/api-keys",
        "tutorialSteps": ["打开 api.together.ai", "登录或注册账号", "进入 Settings → API Keys", "复制 API Key"],
        "supportsDiscovery": True,
    },
    "huggingface": {
        "name": "Hugging Face",
        "description": "开源模型社区推理 API",
        "group": "more",
        "order": 170,
        "keyRequired": True,
        "keyHint": "Hugging Face Token (hf_...)",
        "tutorialUrl": "https://huggingface.co/settings/tokens",
        "tutorialSteps": ["打开 huggingface.co/settings/tokens", "登录或注册账号", "创建 Access Token (Read scope)", "复制 Token"],
        "supportsDiscovery": True,
    },
    "nvidia": {
        "name": "NVIDIA NIM",
        "description": "NVIDIA 推理微服务",
        "group": "more",
        "order": 180,
        "keyRequired": True,
        "keyHint": "NVIDIA API Key",
        "tutorialUrl": "https://build.nvidia.com/",
        "tutorialSteps": ["打开 build.nvidia.com", "登录 NVIDIA 账号", "获取 API Key"],
        "supportsDiscovery": True,
    },
    "byteplus": {
        "name": "BytePlus",
        "description": "字节海外 AI 平台",
        "group": "more",
        "order": 185,
        "keyRequired": True,
        "keyHint": "BytePlus API Key",
        "tutorialUrl": "https://console.byteplus.com/",
        "tutorialSteps": ["打开 console.byteplus.com", "登录账号", "获取 API Key"],
        "supportsDiscovery": True,
    },
    "chutes": {
        "name": "Chutes",
        "description": "开源模型推理平台",
        "group": "more",
        "order": 190,
        "keyRequired": True,
        "keyHint": "Chutes API Key",
        "tutorialUrl": "https://chutes.ai/",
        "tutorialSteps": ["打开 chutes.ai", "注册或登录", "获取 API Key"],
        "supportsDiscovery": True,
    },
    "venice": {
        "name": "Venice AI",
        "description": "隐私优先 AI 平台",
        "group": "more",
        "order": 195,
        "keyRequired": True,
        "keyHint": "Venice API Key",
        "tutorialUrl": "https://venice.ai/",
        "tutorialSteps": ["打开 venice.ai", "注册或登录", "获取 API Key"],
        "supportsDiscovery": True,
    },
    "synthetic": {
        "name": "Synthetic",
        "description": "Anthropic 兼容代理",
        "group": "more",
        "order": 200,
        "keyRequired": True,
        "keyHint": "Synthetic API Key",
        "tutorialUrl": "https://synthetic.new/",
        "tutorialSteps": ["打开 synthetic.new", "注册或登录", "获取 API Key"],
        "supportsDiscovery": False,
    },
    "sglang": {
        "name": "SGLang",
        "description": "自托管推理引擎",
        "group": "more",
        "order": 210,
        "keyRequired": False,
        "keyHint": "API Key (可选)",
        "tutorialUrl": "",
        "tutorialSteps": ["安装 SGLang", "启动服务", "填入 Base URL"],
        "supportsDiscovery": True,
    },
    "vllm": {
        "name": "vLLM",
        "description": "自托管推理引擎",
        "group": "more",
        "order": 215,
        "keyRequired": False,
        "keyHint": "API Key (可选)",
        "tutorialUrl": "",
        "tutorialSteps": ["安装 vLLM", "启动服务", "填入 Base URL"],
        "supportsDiscovery": True,
    },
}

# Base URL 常量（从 OpenClaw auth-profiles 和 provider-catalog 提取）
BASE_URLS = {
    "openrouter": "https://openrouter.ai/api/v1",
    "anthropic": "https://api.anthropic.com",
    "openai": "https://api.openai.com/v1",
    "google": "https://generativelanguage.googleapis.com/v1beta",
    "xai": "https://api.x.ai/v1",
    "deepseek": "https://api.deepseek.com/v1",
    "ollama": "http://127.0.0.1:11434",
    "volcengine": "https://ark.cn-beijing.volces.com/api/v3",
    "moonshot": "https://api.moonshot.ai/v1",
    "qianfan": "https://qianfan.baidubce.com/v2",
    "xiaomi": "https://api.xiaomimimo.com/v1",
    "mistral": "https://api.mistral.ai/v1",
    "together": "https://api.together.xyz/v1",
    "huggingface": "https://router.huggingface.co/v1",
    "nvidia": "https://integrate.api.nvidia.com/v1",
    "kimi-coding": "https://api.kimi.com/coding/",
    "byteplus": "https://ark.ap-southeast.bytepluses.com/api/v3",
    "chutes": "https://llm.chutes.ai/v1",
    "venice": "https://api.venice.ai/api/v1",
    "synthetic": "https://api.synthetic.new/anthropic",
    "sglang": "http://127.0.0.1:30000",
    "vllm": "http://127.0.0.1:8000/v1",
}

# API 类型映射
API_TYPES = {
    "openrouter": "openai-completions",
    "anthropic": "anthropic-messages",
    "openai": "openai-completions",
    "google": "google-generative-ai",
    "xai": "openai-completions",
    "deepseek": "openai-completions",
    "ollama": "ollama",
    "volcengine": "openai-completions",
    "moonshot": "openai-completions",
    "qianfan": "openai-completions",
    "xiaomi": "openai-completions",
    "mistral": "openai-completions",
    "together": "openai-completions",
    "huggingface": "openai-completions",
    "nvidia": "openai-completions",
    "kimi-coding": "anthropic-messages",
    "byteplus": "openai-completions",
    "chutes": "openai-completions",
    "venice": "openai-completions",
    "synthetic": "anthropic-messages",
    "sglang": "openai-completions",
    "vllm": "openai-completions",
}

# authHeader 特殊值 (默认 true，列出 false 的)
AUTH_HEADER_FALSE = {"anthropic", "kimi-coding", "synthetic"}


def extract_models_from_catalog(openclaw_dir: Path, provider_id: str) -> list:
    """尝试从 provider-catalog.ts 中提取模型定义"""
    catalog_file = openclaw_dir / "extensions" / provider_id / "provider-catalog.ts"
    if not catalog_file.exists():
        return []

    content = catalog_file.read_text()

    models = []
    # 简单正则匹配模型定义块
    model_blocks = re.findall(r'\{[^{}]*?id:\s*["\']([^"\']+)["\'][^{}]*?\}', content, re.DOTALL)

    for block_match in re.finditer(r'\{\s*\n(?:[^{}]*\n)*?\s*\}', content):
        block = block_match.group()
        id_match = re.search(r'id:\s*(?:([A-Z_]+)|["\']([^"\']+)["\'])', block)
        if not id_match:
            continue

        model_id = id_match.group(2) or id_match.group(1)
        if not model_id or model_id.startswith("OPENROUTER_") or "_" in model_id and model_id.isupper():
            continue

        name_match = re.search(r'name:\s*["\']([^"\']+)["\']', block)
        reasoning_match = re.search(r'reasoning:\s*(true|false)', block)
        ctx_match = re.search(r'contextWindow:\s*(\d+)', block)
        max_match = re.search(r'maxTokens:\s*(\d+)', block)
        input_match = re.search(r'input:\s*\[([^\]]+)\]', block)

        model = {
            "id": model_id,
            "name": name_match.group(1) if name_match else model_id,
        }
        if reasoning_match:
            model["reasoning"] = reasoning_match.group(1) == "true"
        if ctx_match:
            model["contextWindow"] = int(ctx_match.group(1))
        if max_match:
            model["maxTokens"] = int(max_match.group(1))
        if input_match:
            inputs = re.findall(r'["\']([^"\']+)["\']', input_match.group(1))
            if inputs:
                model["input"] = inputs

        models.append(model)

    return models


def extract_env_vars(openclaw_dir: Path, provider_id: str) -> list:
    """从 openclaw.plugin.json 中提取环境变量"""
    plugin_file = openclaw_dir / "extensions" / provider_id / "openclaw.plugin.json"
    if not plugin_file.exists():
        return []

    data = json.loads(plugin_file.read_text())
    env_vars = data.get("providerAuthEnvVars", {})

    all_vars = []
    for vars_list in env_vars.values():
        all_vars.extend(vars_list)
    return all_vars


def build_provider_entry(openclaw_dir: Path, provider_id: str) -> dict | None:
    meta = PROVIDER_META.get(provider_id)
    if not meta:
        return None

    entry = {
        "id": provider_id,
        "name": meta["name"],
        "description": meta["description"],
        "baseUrl": BASE_URLS.get(provider_id, ""),
        "api": API_TYPES.get(provider_id, "openai-completions"),
        "authHeader": provider_id not in AUTH_HEADER_FALSE,
        "keyRequired": meta["keyRequired"],
        "keyHint": meta["keyHint"],
        "group": meta["group"],
        "order": meta["order"],
        "supportsDiscovery": meta.get("supportsDiscovery", False),
    }

    if "discoveryEndpoint" in meta:
        entry["discoveryEndpoint"] = meta["discoveryEndpoint"]

    if meta.get("tutorialUrl"):
        entry["tutorialUrl"] = meta["tutorialUrl"]
    if meta.get("tutorialSteps"):
        entry["tutorialSteps"] = meta["tutorialSteps"]

    # Extract env vars from plugin manifest
    env_vars = extract_env_vars(openclaw_dir, provider_id)
    if env_vars:
        entry["envVars"] = env_vars

    # Extract models from provider-catalog.ts
    catalog_models = extract_models_from_catalog(openclaw_dir, provider_id)
    if catalog_models:
        entry["models"] = catalog_models

    return entry


def main():
    openclaw_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        os.path.expanduser("~/.openclaw/workspace-dev-androidclaw/openclaw")
    )

    if not openclaw_dir.exists():
        print(f"Error: OpenClaw dir not found: {openclaw_dir}", file=sys.stderr)
        sys.exit(1)

    providers = []
    for pid in sorted(SUPPORTED_PROVIDERS):
        entry = build_provider_entry(openclaw_dir, pid)
        if entry:
            providers.append(entry)

    # Sort by order
    providers.sort(key=lambda p: p["order"])

    manifest = {
        "_comment": "Auto-generated from OpenClaw source. Do not edit manually.",
        "_source": str(openclaw_dir),
        "_generated": __import__("datetime").datetime.now().isoformat(),
        "providers": providers,
    }

    # Output to assets
    script_dir = Path(__file__).parent.parent
    output_path = script_dir / "app" / "src" / "main" / "assets" / "providers.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"✅ Generated {output_path} with {len(providers)} providers")

    # Print summary
    for p in providers:
        model_count = len(p.get("models", []))
        print(f"  {p['id']:20s} {p['name']:20s} models={model_count} group={p['group']}")


if __name__ == "__main__":
    main()
