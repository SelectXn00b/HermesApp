---
name: feishu-wiki
description: |
  Feishu knowledge base navigation. Activate when user mentions knowledge base, wiki, or wiki links.
---

# Feishu Wiki Tool

Single tool `feishu_wiki` for knowledge base operations.

## Token Extraction

From URL `https://xxx.feishu.cn/wiki/ABC123def` → `token` = `ABC123def`

## Actions

### List Knowledge Spaces

```json
{ "action": "spaces" }
```

Returns all accessible wiki spaces.

### List Nodes

```json
{ "action": "nodes", "space_id": "7xxx" }
```

With parent:

```json
{ "action": "nodes", "space_id": "7xxx", "parent_node_token": "wikcnXXX" }
```

### Get Node Details

```json
{ "action": "get", "token": "ABC123def" }
```

Returns: `node_token`, `obj_token`, `obj_type`, etc. Use `obj_token` with `feishu_doc` to read/write the document.

### Create Node

```json
{ "action": "create", "space_id": "7xxx", "title": "New Page" }
```

With type and parent:

```json
{
  "action": "create",
  "space_id": "7xxx",
  "title": "Sheet",
  "obj_type": "sheet",
  "parent_node_token": "wikcnXXX"
}
```

`obj_type`: `docx` (default), `sheet`, `bitable`, `mindnote`, `file`, `doc`, `slides`

## Wiki-Doc Workflow

To edit a wiki page:

1. Get node: `{ "action": "get", "token": "wiki_token" }` → returns `obj_token`
2. Read doc: `feishu_doc { "action": "read", "doc_token": "obj_token" }`
3. Write doc: `feishu_doc { "action": "write", "doc_token": "obj_token", "content": "..." }`

## AndroidForClaw Implementation

**Tool Class**: `FeishuWikiTools.kt`

**Available Tools**:
- `feishu_wiki_spaces` - List knowledge spaces
- `feishu_wiki_nodes` - List nodes in space
- `feishu_wiki_get` - Get node details
- `feishu_wiki_create` - Create new node

**Example Usage**:
```kotlin
// List spaces
val result = feishuWikiTools.listSpaces()

// Create wiki page
val result = feishuWikiTools.createNode(
    spaceId = "7xxx",
    title = "New Page",
    objType = "docx"
)
```

**Dependency:** This tool requires `feishu_doc` to be enabled. Wiki pages are documents - use `feishu_wiki` to navigate, then `feishu_doc` to read/edit content.

## Permissions

Required: `wiki:wiki` or `wiki:wiki:readonly`
