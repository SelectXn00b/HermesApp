---
name: feishu-common
description: |
  飞书用户信息与搜索。Activate when user wants to look up user info or search for employees.
---

# Feishu Common Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_get_user — 获取用户信息

```json
{ "user_id": "ou_xxx", "user_id_type": "open_id" }
```

Get user details by ID. Supports `open_id`, `union_id`, `user_id` as `user_id_type`.

Returns: name, en_name, email, mobile, avatar, department_ids, status, etc.

### feishu_search_user — 搜索员工信息

```json
{ "query": "张三", "page_size": 20 }
```

Search employees by name or keyword.

**Parameters:**
- `query` (required) — Search keyword (name, email, etc.)
- `page_size` — Number of results per page (default 20)
- `page_token` — Pagination token

Returns: list of matching users with open_id, name, department, etc.

## Permissions

Required: `contact:user.base:readonly`, `contact:user.employee_id:readonly`
