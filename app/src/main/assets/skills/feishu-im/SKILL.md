---
name: feishu-im
description: |
  飞书即时通讯工具。Activate when user wants to send messages, read chat history, search messages, or download IM resources.
---

# Feishu IM Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_im_user_message — 飞书用户身份 IM 消息工具

```json
{ "action": "send", "receive_id": "ou_xxx", "receive_id_type": "open_id", "msg_type": "text", "content": "{\"text\": \"Hello!\"}" }
{ "action": "reply", "message_id": "om_xxx", "msg_type": "text", "content": "{\"text\": \"Reply here\"}" }
```

**Actions:**
- `send` — Send a message to a user or chat (supports text, post, image, interactive, etc.)
- `reply` — Reply to a specific message

### feishu_im_user_get_messages — 获取群聊或单聊的历史消息

```json
{ "container_id": "oc_xxx", "container_id_type": "chat", "start_time": "1680000000", "end_time": "1680086400", "page_size": 20 }
```

Get historical messages from a chat or conversation.

### feishu_im_user_get_thread_messages — 获取话题内的消息列表

```json
{ "thread_id": "omt_xxx", "page_size": 20 }
```

Get messages within a thread (topic/reply chain).

### feishu_im_user_search_messages — 跨会话搜索飞书消息

```json
{ "query": "project update", "from_ids": ["ou_xxx"], "chat_ids": ["oc_xxx"], "start_time": "1680000000", "end_time": "1680086400" }
```

Search messages across conversations with keyword, sender, chat, and time filters.

### feishu_im_user_fetch_resource — 下载飞书 IM 消息中的文件或图片资源

```json
{ "message_id": "om_xxx", "file_key": "file_xxx", "type": "file", "output_path": "/path/to/save" }
```

Download a file or image resource from an IM message.

### feishu_im_bot_image — 以机器人身份下载飞书 IM 消息中的图片或文件资源

```json
{ "image_key": "img_xxx", "output_path": "/path/to/save.png" }
```

Download image or file resource as bot identity.

## Permissions

Required: `im:message`, `im:message:readonly`, `im:resource`
