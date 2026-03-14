---
name: feishu-drive
description: |
  Feishu cloud storage file management. Activate when user mentions cloud space, folders, drive.
---

# Feishu Drive Tool

Single tool `feishu_drive` for cloud storage operations.

## Token Extraction

From URL `https://xxx.feishu.cn/drive/folder/ABC123` → `folder_token` = `ABC123`

## Actions

### List Folder Contents

```json
{ "action": "list" }
```

Root directory (no folder_token).

```json
{ "action": "list", "folder_token": "fldcnXXX" }
```

Returns: files with token, name, type, url, timestamps.

### Create Folder

```json
{ "action": "create_folder", "name": "New Folder" }
```

In parent folder:

```json
{ "action": "create_folder", "name": "New Folder", "folder_token": "fldcnXXX" }
```

### Move File

```json
{ "action": "move", "file_token": "ABC123", "type": "docx", "folder_token": "fldcnXXX" }
```

### Delete File

```json
{ "action": "delete", "file_token": "ABC123", "type": "docx" }
```

## File Types

| Type       | Description             |
| ---------- | ----------------------- |
| `doc`      | Old format document     |
| `docx`     | New format document     |
| `sheet`    | Spreadsheet             |
| `bitable`  | Multi-dimensional table |
| `folder`   | Folder                  |
| `file`     | Uploaded file           |
| `mindnote` | Mind map                |
| `shortcut` | Shortcut                |

## AndroidForClaw Implementation

**Tool Class**: `FeishuDriveTools.kt`

**Available Tools**:
- `feishu_drive_list` - List folder contents
- `feishu_drive_create_folder` - Create new folder
- `feishu_drive_move` - Move file/folder
- `feishu_drive_delete` - Delete file/folder

**Example Usage**:
```kotlin
// List root folder
val result = feishuDriveTools.listFolder()

// Create folder
val result = feishuDriveTools.createFolder(
    name = "New Folder",
    parentFolderToken = "fldcnXXX"
)
```

## Permissions

- `drive:drive` - Full access (create, move, delete)
- `drive:drive:readonly` - Read only (list)

## Known Limitations

- **Bots have no root folder**: Feishu bots use `tenant_access_token` and don't have their own "My Space". Bot can only access files/folders that have been **shared with it**.
