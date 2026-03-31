---
name: feishu-search
description: |
  飞书文档与 Wiki 统一搜索。Activate when user wants to search docs, wiki pages, or find content across Feishu.
---

# Feishu Search Tool

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_search_doc_wiki — 飞书文档与 Wiki 统一搜索工具

```json
{ "query": "project plan", "count": 20 }
{ "query": "quarterly report", "docs_types": ["docx", "sheet"], "owner_ids": ["ou_xxx"] }
```

**Parameters:**
- `query` (required) — Search keyword
- `count` — Number of results to return (default 20)
- `docs_types` — Filter by document type: `doc`, `docx`, `sheet`, `bitable`, `mindnote`, `wiki`, `file`, `slides`
- `owner_ids` — Filter by document owner (open_id list)
- `chat_ids` — Filter by associated chat
- `page_token` — Pagination token for next page

Returns: matched documents with title, url, type, owner, create_time, update_time, and highlight snippets.

## Permissions

Required: `search:docs`, `search:docs:readonly`
