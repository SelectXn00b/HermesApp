---
name: feishu-bitable
description: |
  飞书多维表格管理。Activate when user mentions bitable, multi-dimensional tables, or bitable links.
---

# Feishu Bitable Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Token Extraction

From URL `https://xxx.feishu.cn/base/ABC123def` -> `app_token` = `ABC123def`

## Tools

### feishu_bitable_app — 飞书多维表格应用管理工具

```json
{ "action": "create", "name": "New App", "folder_token": "fldcnXXX" }
{ "action": "get", "app_token": "ABC123def" }
{ "action": "list" }
{ "action": "patch", "app_token": "ABC123def", "name": "Updated Name" }
{ "action": "copy", "app_token": "ABC123def", "name": "Copy of App" }
```

**Actions:** `create`, `get`, `list`, `patch`, `copy`

### feishu_bitable_app_table — 飞书多维表格数据表管理工具

```json
{ "action": "create", "app_token": "ABC123def", "name": "New Table" }
{ "action": "list", "app_token": "ABC123def" }
{ "action": "patch", "app_token": "ABC123def", "table_id": "tblXXX", "name": "Renamed" }
{ "action": "batch_create", "app_token": "ABC123def", "names": ["Table A", "Table B"] }
```

**Actions:** `create`, `list`, `patch`, `batch_create`

### feishu_bitable_app_table_field — 飞书多维表格字段（列）管理工具

```json
{ "action": "create", "app_token": "ABC123def", "table_id": "tblXXX", "field_name": "Status", "type": 3 }
{ "action": "list", "app_token": "ABC123def", "table_id": "tblXXX" }
{ "action": "update", "app_token": "ABC123def", "table_id": "tblXXX", "field_id": "fldXXX", "field_name": "New Name" }
{ "action": "delete", "app_token": "ABC123def", "table_id": "tblXXX", "field_id": "fldXXX" }
```

**Actions:** `create`, `list`, `update`, `delete`

### feishu_bitable_app_table_record — 飞书多维表格记录（行）管理工具

```json
{ "action": "create", "app_token": "ABC123def", "table_id": "tblXXX", "fields": {"Name": "Task 1"} }
{ "action": "list", "app_token": "ABC123def", "table_id": "tblXXX", "filter": "Status=\"Active\"" }
{ "action": "update", "app_token": "ABC123def", "table_id": "tblXXX", "record_id": "recXXX", "fields": {"Status": "Done"} }
{ "action": "delete", "app_token": "ABC123def", "table_id": "tblXXX", "record_id": "recXXX" }
{ "action": "batch_create", "app_token": "ABC123def", "table_id": "tblXXX", "records": [{"fields": {"Name": "A"}}, {"fields": {"Name": "B"}}] }
{ "action": "batch_update", "app_token": "ABC123def", "table_id": "tblXXX", "records": [{"record_id": "recXXX", "fields": {"Status": "Done"}}] }
{ "action": "batch_delete", "app_token": "ABC123def", "table_id": "tblXXX", "record_ids": ["recXXX", "recYYY"] }
```

**Actions:** `create`, `list`, `update`, `delete`, `batch_create`, `batch_update`, `batch_delete`

### feishu_bitable_app_table_view — 飞书多维表格视图管理工具

```json
{ "action": "create", "app_token": "ABC123def", "table_id": "tblXXX", "view_name": "My View", "view_type": "grid" }
{ "action": "get", "app_token": "ABC123def", "table_id": "tblXXX", "view_id": "viwXXX" }
{ "action": "list", "app_token": "ABC123def", "table_id": "tblXXX" }
{ "action": "patch", "app_token": "ABC123def", "table_id": "tblXXX", "view_id": "viwXXX", "view_name": "Renamed" }
```

**Actions:** `create`, `get`, `list`, `patch`

## Permissions

Required: `bitable:app`, `bitable:app:readonly`
