---
name: feishu-chat
description: |
  飞书群聊管理。Activate when user wants to search groups, get chat info, or list members.
---

# Feishu Chat Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_chat — 飞书群聊管理工具

```json
{ "action": "search", "query": "project team" }
{ "action": "get", "chat_id": "oc_xxx" }
```

**Actions:**
- `search` — Search group chats by keyword
- `get` — Get chat details (name, description, owner, member count, chat type)

### feishu_chat_members — 获取指定群组的成员列表

```json
{ "chat_id": "oc_xxx" }
```

Returns all members in the specified chat with their user info (open_id, name, tenant_key).

## Permissions

Required: `im:chat`, `im:chat:readonly`
