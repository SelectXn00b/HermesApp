---
name: context-security
description: |
  Group chat vs DM security policies. Activate when handling messages from shared contexts
  (group chats, channels, threads) to enforce privacy boundaries.
always: true
---

# Context Security

Security policies for group chat vs private (DM) contexts. Aligned with OpenClaw multi-layer defense.

## Core Rules

### Group Chat / Shared Context (chatType = "group" / "channel" / "thread")

1. **MEMORY.md is NOT loaded** — personal memory is blocked in shared contexts (code-enforced)
2. **Memory tools are RESTRICTED** — `memory_search` and `memory_get` are not available
3. **Config tools are RESTRICTED** — `config_get` and `config_set` are not available (may expose API keys)
4. **Outbound redaction is ACTIVE** — secrets (API keys, tokens, passwords) are automatically masked before replies

### Private Chat / DM (chatType = "p2p" / "direct" / null)

- All tools available
- MEMORY.md loaded normally
- No outbound redaction (trusted 1:1 context)

## What Gets Redacted

The following patterns are automatically detected and masked in group chat outbound messages:

- API keys and tokens (env vars, JSON fields, CLI flags)
- Bearer tokens and Authorization headers
- PEM private keys
- Provider-specific: OpenAI `sk-*`, GitHub `ghp_*`/`github_pat_*`, Slack `xox*`/`xapp-*`
- Service keys: Groq `gsk_*`, Google AI `AIza*`, Perplexity `pplx-*`, npm `npm_*`
- Telegram bot tokens

## Adding New Restricted Tools

Edit `ToolPolicyResolver.GROUP_RESTRICTED_TOOLS` in:
`app/.../agent/context/ToolPolicy.kt`

## Adding New Redaction Patterns

Edit `SensitiveTextRedactor.REDACT_PATTERNS` in:
`app/.../logging/SensitiveTextRedactor.kt`
