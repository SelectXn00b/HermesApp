---
name: feishu-task
description: |
  飞书任务管理。Activate when user mentions tasks, todos, task lists, subtasks.
---

# Feishu Task Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_task_task — 飞书任务管理工具

```json
{ "action": "create", "title": "New Task", "description": "Details...", "due_date": "2026-12-31" }
{ "action": "get", "task_id": "task_xxx" }
{ "action": "list", "page_size": 20 }
{ "action": "patch", "task_id": "task_xxx", "title": "Updated Title", "completed_at": "2026-03-31" }
```

**Actions:** `create`, `get`, `list`, `patch`

### feishu_task_tasklist — 飞书任务清单管理工具

```json
{ "action": "create", "name": "Sprint 1" }
{ "action": "get", "tasklist_id": "tl_xxx" }
{ "action": "list" }
{ "action": "tasks", "tasklist_id": "tl_xxx" }
{ "action": "patch", "tasklist_id": "tl_xxx", "name": "Sprint 1 (Done)" }
{ "action": "add_members", "tasklist_id": "tl_xxx", "member_ids": ["ou_xxx"] }
```

**Actions:** `create`, `get`, `list`, `tasks` (list tasks in a tasklist), `patch`, `add_members`

### feishu_task_subtask — 飞书任务的子任务管理工具

```json
{ "action": "create", "task_id": "task_xxx", "title": "Sub-task 1" }
{ "action": "list", "task_id": "task_xxx" }
```

**Actions:** `create`, `list`

### feishu_task_comment — 飞书任务评论管理工具

```json
{ "action": "create", "task_id": "task_xxx", "content": "Comment text" }
{ "action": "list", "task_id": "task_xxx" }
{ "action": "get", "task_id": "task_xxx", "comment_id": "cm_xxx" }
```

**Actions:** `create`, `list`, `get`

## Permissions

Required: `task:task`, `task:task:readonly`
