---
name: feishu-calendar
description: |
  飞书日历与日程管理。Activate when user mentions calendar, events, meetings, schedules, freebusy.
---

# Feishu Calendar Tools

Aligned with `@larksuite/openclaw-lark` official plugin.

## Tools

### feishu_calendar_calendar — 飞书日历管理工具

```json
{ "action": "list" }
{ "action": "get", "calendar_id": "cal_xxx" }
{ "action": "primary" }
```

**Actions:**
- `list` — List all accessible calendars
- `get` — Get calendar details
- `primary` — Get the user's primary calendar

### feishu_calendar_event — 飞书日程管理工具

```json
{ "action": "create", "calendar_id": "cal_xxx", "summary": "Team Meeting", "start_time": "2026-04-01T10:00:00+08:00", "end_time": "2026-04-01T11:00:00+08:00" }
{ "action": "list", "calendar_id": "cal_xxx", "start_time": "2026-04-01T00:00:00+08:00", "end_time": "2026-04-30T23:59:59+08:00" }
{ "action": "get", "calendar_id": "cal_xxx", "event_id": "evt_xxx" }
{ "action": "patch", "calendar_id": "cal_xxx", "event_id": "evt_xxx", "summary": "Updated Meeting" }
{ "action": "delete", "calendar_id": "cal_xxx", "event_id": "evt_xxx" }
{ "action": "search", "query": "team meeting" }
{ "action": "reply", "calendar_id": "cal_xxx", "event_id": "evt_xxx", "rsvp_status": "accept" }
{ "action": "instances", "calendar_id": "cal_xxx", "event_id": "evt_xxx" }
{ "action": "instance_view", "calendar_id": "cal_xxx", "start_time": "2026-04-01T00:00:00+08:00", "end_time": "2026-04-07T23:59:59+08:00" }
```

**Actions:** `create`, `list`, `get`, `patch`, `delete`, `search`, `reply`, `instances`, `instance_view`

### feishu_calendar_event_attendee — 飞书日程参会人管理工具

```json
{ "action": "create", "calendar_id": "cal_xxx", "event_id": "evt_xxx", "attendees": [{"type": "user", "user_id": "ou_xxx"}] }
{ "action": "list", "calendar_id": "cal_xxx", "event_id": "evt_xxx" }
```

**Actions:** `create` (add attendees), `list` (list attendees)

### feishu_calendar_freebusy — 飞书日历忙闲查询工具

```json
{ "user_ids": ["ou_xxx", "ou_yyy"], "start_time": "2026-04-01T00:00:00+08:00", "end_time": "2026-04-01T23:59:59+08:00" }
```

Query free/busy status for one or more users within a time range.

## Permissions

Required: `calendar:calendar`, `calendar:calendar:readonly`
