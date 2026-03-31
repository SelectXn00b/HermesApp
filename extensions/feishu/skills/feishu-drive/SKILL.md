---
name: feishu-drive
description: |
  飞书云空间文件管理。Activate when user mentions cloud space, folders, drive, file operations.
---

# Feishu Drive Tool

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_drive_file — 飞书云空间文件管理工具

```json
{ "action": "list", "folder_token": "fldcnXXX" }
{ "action": "get_meta", "file_token": "ABC123", "file_type": "docx" }
{ "action": "copy", "file_token": "ABC123", "file_type": "docx", "dst_folder_token": "fldcnXXX" }
{ "action": "move", "file_token": "ABC123", "file_type": "docx", "dst_folder_token": "fldcnXXX" }
{ "action": "delete", "file_token": "ABC123", "file_type": "docx" }
{ "action": "upload", "file_name": "report.pdf", "file_path": "/path/to/file", "parent_token": "fldcnXXX" }
{ "action": "download", "file_token": "ABC123", "output_path": "/path/to/save" }
```

**Actions:**
- `list` — List folder contents (root if no `folder_token`)
- `get_meta` — Get file metadata (name, type, size, timestamps)
- `copy` — Copy file to destination folder
- `move` — Move file to destination folder
- `delete` — Delete a file
- `upload` — Upload a local file to cloud storage
- `download` — Download a cloud file to local path

**File Types:** `doc`, `docx`, `sheet`, `bitable`, `folder`, `file`, `mindnote`, `shortcut`, `slides`

## Permissions

- `drive:drive` — Full access (list, copy, move, delete, upload, download)
- `drive:drive:readonly` — Read only (list, get_meta, download)
