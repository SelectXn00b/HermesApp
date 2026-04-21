---
name: browser
description: "Navigate web pages, click elements, fill forms, extract page content, take screenshots, and execute JavaScript via the browserforclaw HTTP API. Use when the user asks to search the web, scrape a website, automate browser interactions, fill out web forms, or extract data from web pages."
metadata:
  {
    "openclaw": {
      "always": false,
      "emoji": "🌐",
      "version": "2.0.0",
      "category": "automation"
    }
  }
---

# BrowserForClaw Integration

Control the browserforclaw browser for web automation — searching, scraping, form-filling, and testing.

**IMPORTANT**: When user says "用浏览器搜索" or "open browser and search":
- Use the `browser` tool directly — do NOT use `open_app` to launch Chrome
- browserforclaw is already running in the background

## Core Pattern

```
Navigate → Wait → Interact → Extract
```

All browser operations use a single `browser` tool with an `operation` parameter.

## Operations Reference

| Operation | Key Parameters | Purpose |
|-----------|---------------|---------|
| `navigate` | `url`, `waitMs` | Open a URL |
| `click` | `selector`, `index` | Click an element |
| `type` | `selector`, `text`, `clear`, `submit` | Type into an input field |
| `get_content` | `format` (text/html/markdown), `selector` | Extract page content |
| `wait` | `timeMs` / `selector` / `text` / `url` | Wait for condition |
| `scroll` | `direction` (up/down/top/bottom) or `x`,`y` | Scroll the page |
| `execute` | `script` | Run JavaScript (must `return` a value) |
| `press` | `key` | Press a key (Enter, Escape, Tab, etc.) |
| `screenshot` | `fullPage`, `format` | Capture page image |
| `get_cookies` / `set_cookies` | `cookies` | Manage cookies |
| `hover` | `selector` | Hover over element |
| `select` | `selector`, `values` | Select dropdown option |

## Workflows

### Baidu Search (百度搜索)

```json
{"operation": "navigate", "url": "https://www.baidu.com", "waitMs": 2000}
{"operation": "wait", "selector": "#kw", "timeout": 5000}
{"operation": "type", "selector": "#kw", "text": "openclaw", "submit": true}
{"operation": "wait", "timeMs": 2000}
{"operation": "get_content", "format": "text"}
```

### Form Submission

```json
{"operation": "navigate", "url": "https://example.com/form", "waitMs": 2000}
{"operation": "wait", "selector": "form", "timeout": 5000}
{"operation": "type", "selector": "input[name='email']", "text": "user@example.com"}
{"operation": "type", "selector": "input[name='password']", "text": "pass123"}
{"operation": "click", "selector": "button[type='submit']"}
{"operation": "wait", "text": "Success", "timeout": 5000}
```

### Data Extraction

```json
{"operation": "navigate", "url": "https://news.site.com", "waitMs": 2000}
{"operation": "wait", "selector": ".article-title", "timeout": 5000}
{"operation": "execute", "script": "return Array.from(document.querySelectorAll('.article-title')).map(el => el.innerText)"}
```

## Critical Rules

1. **Always wait after navigate** — elements are not available immediately:
   ```json
   {"operation": "navigate", "url": "https://example.com", "waitMs": 2000}
   {"operation": "wait", "selector": "#target", "timeout": 5000}
   {"operation": "click", "selector": "#target"}
   ```

2. **JavaScript must return values** — `"return document.title"` not `"console.log(document.title)"`

3. **Verify after important actions** — use `get_content` or `screenshot` to confirm success

4. **Error recovery** — if element not found, try: longer wait → alternative selector → screenshot to diagnose

## Integration

browserforclaw runs as a separate Android app communicating via HTTP API on localhost:8080.
