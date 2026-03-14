---
name: feishu-chat
description: |
  Feishu chat operations. Activate when user wants to create groups, manage chat members, or get chat info.
---

# Feishu Chat Tool

Tool `feishu_chat` for chat management operations.

## Actions

### Get Chat Info

```json
{ "action": "get", "chat_id": "oc_xxx" }
```

Returns: chat name, description, owner, member count, chat type (group/p2p).

### List Chat Members

```json
{ "action": "members", "chat_id": "oc_xxx" }
```

Returns: all members in the chat with their user info.

### Create Group Chat

```json
{
  "action": "create",
  "name": "Project Team",
  "description": "Project discussion group",
  "member_ids": ["ou_xxx", "ou_yyy"]
}
```

Creates a new group chat.

### Add Members

```json
{
  "action": "add_members",
  "chat_id": "oc_xxx",
  "member_ids": ["ou_zzz"]
}
```

Adds members to a group chat.

### Remove Members

```json
{
  "action": "remove_members",
  "chat_id": "oc_xxx",
  "member_ids": ["ou_zzz"]
}
```

Removes members from a group chat (requires admin permissions).

## Chat Types

| Type | Description |
|------|-------------|
| `p2p` | Private chat between two users |
| `group` | Group chat with multiple members |

## AndroidForClaw Implementation

**Tool Class**: `FeishuChatTools.kt`

**Available Tools**:
- `feishu_chat_get` - Get chat info
- `feishu_chat_members` - List chat members
- `feishu_chat_create` - Create group chat
- `feishu_chat_add_members` - Add members

**Example Usage**:
```kotlin
// Get chat info
val result = feishuChatTools.getChatInfo(chatId = "oc_xxx")

// Create group chat
val result = feishuChatTools.createGroupChat(
    name = "Project Team",
    description = "Project discussion",
    memberIds = listOf("ou_xxx", "ou_yyy")
)

// Add members
val result = feishuChatTools.addMembers(
    chatId = "oc_xxx",
    memberIds = listOf("ou_zzz")
)
```

## Use Cases

- **Group Creation**: Create project/team groups
- **Member Management**: Add/remove team members
- **Chat Discovery**: Get chat info and member lists
- **Bot Integration**: Manage bot access to chats

## Permissions

Required: `im:chat`, `im:chat:readonly`

## Notes

- **Bot Limitations**: Bots can only manage chats they are members of
- **Admin Actions**: Some actions (remove members, update settings) require admin permissions
- **Member IDs**: Use `open_id` format for member IDs (`ou_xxx`)
