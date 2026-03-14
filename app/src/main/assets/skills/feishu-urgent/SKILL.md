---
name: feishu-urgent
description: |
  Feishu urgent notification tool. Activate when user wants to send urgent messages or notifications.
---

# Feishu Urgent Tool

Tool `feishu_urgent` for sending urgent notifications.

## Actions

### Send Urgent Message

```json
{
  "action": "send",
  "user_id": "ou_xxx",
  "title": "Urgent: Production Issue",
  "content": "Service is down, please check immediately!"
}
```

Sends an urgent notification to a user with push notification.

### Send Urgent to Multiple Users

```json
{
  "action": "send_batch",
  "user_ids": ["ou_xxx", "ou_yyy"],
  "title": "Critical Alert",
  "content": "System maintenance starting in 5 minutes"
}
```

Sends urgent notifications to multiple users.

## Notification Features

- **Push Notification**: Sends system push notification to user's devices
- **SMS Fallback**: Can trigger SMS if user is offline (requires config)
- **Priority Display**: Shows as urgent in Feishu app
- **Sound Alert**: Plays notification sound even in DND mode

## AndroidForClaw Implementation

**Tool Class**: `FeishuUrgentTools.kt`

**Available Tools**:
- `feishu_urgent_send` - Send urgent message to user
- `feishu_urgent_batch` - Send urgent message to multiple users

**Example Usage**:
```kotlin
// Send urgent notification
val result = feishuUrgentTools.sendUrgent(
    userId = "ou_xxx",
    title = "Production Alert",
    content = "Database connection failed!"
)

// Send to multiple users
val result = feishuUrgentTools.sendUrgentBatch(
    userIds = listOf("ou_xxx", "ou_yyy"),
    title = "System Maintenance",
    content = "Starting in 5 minutes"
)
```

## Use Cases

- **Production Alerts**: Notify on-call engineers
- **Critical Issues**: Alert team about urgent problems
- **Time-Sensitive Notifications**: Meeting reminders, deadlines
- **Emergency Broadcasts**: Company-wide urgent announcements

## Best Practices

1. **Use Sparingly**: Only for truly urgent matters
2. **Clear Titles**: Make urgency clear in title
3. **Action Items**: Include what needs to be done
4. **Avoid Spam**: Don't overuse to maintain effectiveness

## Permissions

Required: `im:message:send_as_bot`, `im:message`

## Notes

- **Rate Limiting**: Urgent notifications may have stricter rate limits
- **User Settings**: Users can configure urgent notification preferences
- **Do Not Disturb**: Urgent messages can bypass DND settings (use responsibly)
