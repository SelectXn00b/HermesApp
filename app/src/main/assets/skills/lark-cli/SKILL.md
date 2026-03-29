---
name: lark-cli
description: |
  Feishu/Lark CLI for platform operations not covered by feishu_* tools:
  calendar, approval, contacts, tasks, attendance, etc.
---

# Lark CLI Skill

Use the `lark_cli` tool to execute lark-cli commands for Feishu/Lark platform operations.

## When to Use

Use `lark_cli` for:
- Calendar operations (list, create, update, delete events)
- Approval workflows (initiate, query, approve/reject)
- Contacts / directory lookup
- Task management (create, assign, update tasks)
- Attendance records
- Any Feishu API not covered by existing feishu_* tools

## When NOT to Use

Use the existing feishu_* tools instead for:
- `feishu_send_message` / `feishu_send_card` — sending chat messages
- `feishu_doc_*` — document operations
- `feishu_wiki_*` — wiki operations
- `feishu_drive_*` — drive/file operations
- `feishu_bitable_*` — bitable/spreadsheet operations
- `feishu_chat_*` — chat/group management

## Authentication

Authentication is **automatic**. Credentials are read from `openclaw.json` (`channels.feishu.appId` / `appSecret`). No manual auth setup needed.

## Usage Examples

```
# Check auth status
lark_cli command: "auth status"

# List upcoming calendar events
lark_cli command: "calendar event list"

# Get user info
lark_cli command: "contact user get --user-id xxx"

# List approval instances
lark_cli command: "approval instance list --approval-code xxx"

# Run with custom timeout
lark_cli command: "calendar event list", timeout_seconds: 60
```

## Notes

- Default timeout is 30 seconds (max 120s)
- Output is truncated at 10000 characters
- Supported architectures: arm64 and x86_64 only
- Run `lark_cli command: "help"` to see all available commands
