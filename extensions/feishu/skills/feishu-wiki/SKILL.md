---
name: feishu-wiki
description: |
  飞书知识库管理。Activate when user mentions knowledge base, wiki, or wiki links.
---

# Feishu Wiki Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_wiki_space — 飞书知识空间管理工具

```json
{ "action": "list" }
{ "action": "get", "space_id": "7xxx" }
{ "action": "create", "name": "New Space", "description": "Space description" }
```

**Actions:**
- `list` — List all accessible wiki spaces
- `get` — Get space details by `space_id`
- `create` — Create a new wiki space

### feishu_wiki_space_node — 飞书知识库节点管理工具

```json
{ "action": "list", "space_id": "7xxx" }
{ "action": "get", "token": "wikcnXXX" }
{ "action": "create", "space_id": "7xxx", "title": "New Page", "obj_type": "docx" }
{ "action": "move", "space_id": "7xxx", "node_token": "wikcnXXX", "target_parent_token": "wikcnYYY" }
{ "action": "copy", "space_id": "7xxx", "node_token": "wikcnXXX", "target_space_id": "7yyy" }
```

**Actions:**
- `list` — List nodes in a space (optional `parent_node_token` for subtree)
- `get` — Get node details by `token`; returns `obj_token` for use with `feishu_fetch_doc`
- `create` — Create new node; `obj_type`: `docx` (default), `sheet`, `bitable`, `mindnote`, `file`, `doc`, `slides`
- `move` — Move node to a different parent within the same space
- `copy` — Copy node to another space

## Wiki-Doc Workflow

1. Get node: `feishu_wiki_space_node { "action": "get", "token": "..." }` -> returns `obj_token`
2. Read doc: `feishu_fetch_doc { "doc_id": "obj_token" }`
3. Edit doc: `feishu_update_doc { "doc_id": "obj_token", "mode": "append", "markdown": "..." }`

## Permissions

Required: `wiki:wiki`, `wiki:wiki:readonly`
