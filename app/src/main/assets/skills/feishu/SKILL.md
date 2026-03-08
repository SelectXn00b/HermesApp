---
name: feishu
description: Feishu messaging and media operations for sending text and images
metadata: { "openclaw": { "always": false, "emoji": "🐦" } }
---

# Feishu Messaging Skill

This skill provides guidance on how to send messages and images through Feishu.

## 📸 Sending Images

When you need to send an image (like a screenshot) to Feishu:

### Current Behavior

The system **automatically detects** screenshot paths in your response and uploads them to Feishu:

1. When you use the `screenshot` tool, it returns a path like:
   ```
   路径: content://com.xiaomo.androidforclaw.accessibility.fileprovider/screenshots/screenshot_xxx.png
   ```

2. **Simply include this path in your response** - the system will:
   - Detect the screenshot path automatically
   - Upload the image to Feishu
   - Send the image message
   - Send your text message (with the path removed)

### Example Usage

```
User: "截张图发给我"

Agent:
1. Calls screenshot() tool
2. Receives: "路径: content://...../screenshot_123.png"
3. Responds with the path included:
   "好的！我已经截图了。

   路径: content://com.xiaomo.androidforclaw.accessibility.fileprovider/screenshots/screenshot_123.png

   分辨率: 1156x2510"

Result: The system automatically uploads and sends the image to Feishu.
```

### Important Notes

- ✅ **DO** include the full "路径: ..." line in your response
- ✅ **DO** use the exact path format from the screenshot tool
- ❌ **DON'T** try to manually call upload or send image functions
- ❌ **DON'T** modify or remove the screenshot path
- ℹ️ The path will be automatically removed from the text message after the image is sent

### Path Formats Supported

The system recognizes these path formats:
- File paths: `路径: /storage/emulated/0/...`
- Content URIs: `路径: content://com.xiaomo.androidforclaw.accessibility.fileprovider/...`

## 📝 Sending Text Only

For text-only messages, simply respond normally. Your response is automatically sent to Feishu.

### Markdown Support

Feishu automatically renders:
- Code blocks (```)
- Tables (|...|)
- Bold, italic, links, etc.

Use proper Markdown formatting for better readability.

## 🔄 Message Flow

```
User Message (Feishu)
    ↓
Agent Processing
    ↓
Agent Response
    ↓
Automatic Detection:
  - Screenshot paths → Upload & Send Image
  - Remaining text → Send as Text
    ↓
User Sees (Feishu)
```

## ⚠️ Troubleshooting

If image fails to send:
1. Check screenshot path is included in response
2. Verify path format starts with "路径: "
3. Check logs for upload errors
4. Ensure image file exists at the path

## 🎯 Best Practices

1. **Always include the full path line** when you have a screenshot
2. **Add context** around the screenshot (what it shows, why it's useful)
3. **Keep text clean** - system removes the path automatically
4. **Trust the automation** - you don't need to manually handle image uploads
