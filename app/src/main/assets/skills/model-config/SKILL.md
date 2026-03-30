---
name: model-config
description: Configure model providers and default model selection in AndroidForClaw. Use when the user asks to switch models, set a default model, add or edit provider configs, or configure OpenAI-compatible/custom model endpoints.
---

# Model Config

## IMPORTANT: Use `provider_setup` Tool

For known providers (xiaomi, openrouter, deepseek, etc.), **always use the `provider_setup` tool** instead of manually writing config with `config_set`. This ensures correct baseUrl, model IDs, and other settings from the built-in provider catalog.

```
provider_setup(providerId="xiaomi", apiKey="sk-xxx")
provider_setup(providerId="openrouter", apiKey="sk-or-xxx", modelId="xiaomi/mimo-v2-pro")
provider_setup(providerId="deepseek", apiKey="sk-xxx", modelId="deepseek-chat")
```

**Do NOT** use `config_set` to write `models.providers.*` paths — it will be rejected. The `provider_setup` tool handles baseUrl, api format, model definitions, and default model automatically.

## When to Use `config_set`

Only use `config_set` for non-provider settings:

- `agent.maxIterations` — change max agent loop iterations
- `channels.feishu.enabled` — enable/disable channels
- Other non-provider config paths

## Workflow

1. Ask the user which provider and model they want.
2. Call `provider_setup(providerId=..., apiKey=..., modelId=...)`.
3. The tool auto-fills baseUrl, api format, and model list from the built-in catalog.
4. If `setAsDefault` is not "false", the model is set as the default automatically.
5. If asked to verify, send a simple test prompt after config is saved.

## Custom/Unknown Providers

For truly custom OpenAI-compatible endpoints not in the provider catalog, use `config_set` with the full provider path structure as a last resort. But always check `provider_setup` first — it covers 30+ providers.
