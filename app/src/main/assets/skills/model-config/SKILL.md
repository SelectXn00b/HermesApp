---
name: model-config
description: Configure model providers and default model selection in AndroidForClaw. Use when the user asks to switch models, set a default model, add or edit provider configs, or configure OpenAI-compatible/custom model endpoints. For AndroidForClaw, model settings live in /sdcard/.androidforclaw/openclaw.json under agent.defaultModel and models.providers.
---

# Model Config

For AndroidForClaw, model configuration is stored in:

- `/sdcard/.androidforclaw/openclaw.json`

Relevant paths:

- `agent.defaultModel`
- `models.providers.<providerName>.baseUrl`
- `models.providers.<providerName>.apiKey`
- `models.providers.<providerName>.api`
- `models.providers.<providerName>.authHeader`
- `models.providers.<providerName>.models`

## Workflow

1. Read `/sdcard/.androidforclaw/openclaw.json` first.
2. Check current `agent.defaultModel` and existing providers under `models.providers`.
3. If the target provider already exists, update only the necessary fields.
4. If the target provider does not exist, create a provider entry under `models.providers`.
5. For OpenAI-compatible or custom endpoints, use:
   - `api: "openai-completions"`
   - provider-specific `baseUrl`
   - `apiKey`
   - one or more model entries in `models`
6. Update `agent.defaultModel` to the requested model.
7. Preserve unrelated providers and settings.
8. If asked to verify, send a simple test prompt after config is saved.

## Notes

- Do not overwrite the full config if only one provider/model needs to change.
- If the user asks for GPT-5.4 and provides a compatible endpoint/key, it can be configured via a custom/OpenAI-compatible provider.
- Keep model IDs exact.
