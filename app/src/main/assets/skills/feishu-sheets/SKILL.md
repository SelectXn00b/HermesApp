---
name: feishu-sheets
description: |
  飞书电子表格操作。Activate when user mentions spreadsheets, sheets, or sheet links.
---

# Feishu Sheets Tool

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_sheet — 飞书电子表格工具

```json
{ "action": "info", "spreadsheet_token": "shtcnXXX" }
{ "action": "read", "spreadsheet_token": "shtcnXXX", "range": "Sheet1!A1:D10" }
{ "action": "write", "spreadsheet_token": "shtcnXXX", "range": "Sheet1!A1:B2", "values": [["Name", "Score"], ["Alice", 95]] }
{ "action": "append", "spreadsheet_token": "shtcnXXX", "range": "Sheet1!A:B", "values": [["Bob", 88]] }
{ "action": "find", "spreadsheet_token": "shtcnXXX", "sheet_id": "Sheet1", "find": "keyword" }
{ "action": "create", "title": "New Sheet", "folder_token": "fldcnXXX" }
{ "action": "export", "spreadsheet_token": "shtcnXXX", "output_path": "/path/to/save.xlsx" }
```

**Actions:**
- `info` — Get spreadsheet metadata (title, sheets list, row/column counts)
- `read` — Read cell data from a range (A1 notation)
- `write` — Write data to a range (overwrites existing cells)
- `append` — Append rows to the end of a range
- `find` — Search for a keyword in a sheet
- `create` — Create a new spreadsheet
- `export` — Export spreadsheet to local file

## Token Extraction

From URL `https://xxx.feishu.cn/sheets/shtcnXXX` -> `spreadsheet_token` = `shtcnXXX`

## Permissions

Required: `sheets:spreadsheet`, `sheets:spreadsheet:readonly`
