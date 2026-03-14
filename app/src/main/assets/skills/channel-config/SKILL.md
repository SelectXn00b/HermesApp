---
name: channel-config
description: Configure messaging channels in AndroidForClaw, including Feishu and Discord. Use when the user asks to configure, enable, disable, inspect, or fix channel settings such as tokens, appId/appSecret, dmPolicy, groupPolicy, requireMention, or connection status. For AndroidForClaw, these settings live in /sdcard/.androidforclaw/openclaw.json under channels.*.
---

# Channel Config

For AndroidForClaw, channel settings are stored in:

- `/sdcard/.androidforclaw/openclaw.json`

Relevant paths:

- `channels.feishu.enabled`
- `channels.feishu.appId`
- `channels.feishu.appSecret`
- `channels.feishu.domain`
- `channels.feishu.connectionMode`
- `channels.feishu.dmPolicy`
- `channels.feishu.groupPolicy`
- `channels.feishu.requireMention`
- `channels.discord.enabled`
- `channels.discord.token`
- `channels.discord.dmPolicy`
- `channels.discord.groupPolicy`
- `channels.discord.requireMention`
- `channels.discord.replyToMode`

## Workflow

1. Read `/sdcard/.androidforclaw/openclaw.json` first.
2. Update only the requested channel subtree under `channels.*`.
3. Preserve unrelated config.
4. If the user provides credentials, write them exactly.
5. Enable the channel when the user asks to configure it unless they explicitly say not to.
6. After writing config, tell the user what changed.
7. If asked to verify, check the app logs / connection status after config is saved.

## Notes

- Prefer structured config edits over unrelated file changes.
- Do not change model settings in this skill unless the user explicitly asks.
- For Feishu, common required fields are `enabled`, `appId`, `appSecret`.
- For Discord, common required fields are `enabled`, `token`.
