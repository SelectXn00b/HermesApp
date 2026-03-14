---
name: feishu-task
description: |
  Feishu task management operations. Activate when user mentions tasks, todos, task lists.
---

# Feishu Task Tool

Tool `feishu_task` for task management operations.

## Actions

### List Tasks

```json
{ "action": "list", "page_size": 20 }
```

Returns: user's tasks with details.

### Get Task Details

```json
{ "action": "get", "task_id": "task_xxx" }
```

Returns: complete task information including title, description, status, assignee, due date.

### Create Task

```json
{
  "action": "create",
  "title": "New Task",
  "description": "Task description",
  "due_date": "2024-12-31",
  "assignee_id": "ou_xxx"
}
```

Creates a new task.

### Update Task

```json
{
  "action": "update",
  "task_id": "task_xxx",
  "title": "Updated Title",
  "status": "completed"
}
```

Updates an existing task.

## Task Status

| Status | Description |
|--------|-------------|
| `todo` | Not started |
| `in_progress` | In progress |
| `completed` | Completed |
| `canceled` | Canceled |

## AndroidForClaw Implementation

**Tool Class**: `FeishuTaskTools.kt`

**Available Tools**:
- `feishu_task_list` - List tasks
- `feishu_task_get` - Get task details
- `feishu_task_create` - Create task
- `feishu_task_update` - Update task

**Example Usage**:
```kotlin
// List tasks
val result = feishuTaskTools.listTasks(pageSize = 20)

// Create task
val result = feishuTaskTools.createTask(
    title = "New Task",
    description = "Description",
    dueDate = "2024-12-31",
    assigneeId = "ou_xxx"
)

// Update task status
val result = feishuTaskTools.updateTask(
    taskId = "task_xxx",
    status = "completed"
)
```

## Use Cases

- **Task Management**: Create, update, list tasks
- **Project Tracking**: Track task status and progress
- **Team Collaboration**: Assign tasks to team members
- **Reminders**: Set due dates and reminders

## Permissions

Required: `task:task`, `task:task:readonly`
