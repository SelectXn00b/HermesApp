---
name: browser-automation
description: "Navigate URLs, click elements, fill forms, extract page content, take screenshots, execute JavaScript, and manage cookies via the BrowserForClaw HTTP API on Android. Use when the user asks to automate a browser, scrape a website, interact with web pages, search the web, or perform browser-based testing."
metadata: { "openclaw": { "always": false, "emoji": "🌐" } }
---

# Browser Automation Skill

Automate web interactions on Android using BrowserForClaw's HTTP API — navigation, scraping, form-filling, testing, and JavaScript execution.

## Core Pattern

```
Navigate → Wait → Interact → Extract
```

## Tools Overview

| Tool | Parameters | Purpose |
|------|-----------|---------|
| `browser_navigate(url)` | `url` (required), `waitMs` (optional, default 500ms) | Open a URL |
| `browser_click(selector)` | `selector` (CSS, required) | Click an element |
| `browser_type(selector, text, submit)` | `selector`, `text` (required), `submit` (optional) | Type into input field |
| `browser_get_content(format)` | `format`: "text", "html", or "markdown" | Extract page content |
| `browser_wait(...)` | `selector` / `text` / `url` / `timeMs` (at least one) | Wait for a condition |
| `browser_scroll(direction)` | `direction`: "up", "down", "top", "bottom" | Scroll the page |
| `browser_execute(script)` | `script` (required, must return a value) | Run JavaScript |
| `browser_press(key)` | `key`: Enter, Escape, Tab, Backspace, Arrow* | Press keyboard key |
| `browser_screenshot(fullPage)` | `fullPage` (optional, default false) | Capture page image |
| `browser_hover(selector)` | `selector` (CSS, required) | Trigger hover states |
| `browser_select(selector, values)` | `selector`, `values` (array) | Select dropdown option |
| `browser_get_cookies()` | — | Get cookies for current domain |
| `browser_set_cookies(cookies)` | `cookies` (array of strings) | Set cookies |

## Common Patterns

### Google Search

```
browser_navigate("https://google.com")
browser_wait({"selector": "input[name='q']"})
browser_type("input[name='q']", "search query")
browser_press("Enter")
browser_wait({"url": "search"})
browser_get_content("text")
```

### Form Filling

```
browser_navigate("https://example.com/form")
browser_wait({"selector": "#email"})
browser_type("#email", "user@example.com")
browser_type("#password", "password123")
browser_click("button[type='submit']")
browser_wait({"text": "Success"})
```

### Data Extraction

```
browser_navigate("https://news.site.com")
browser_wait({"selector": ".article-title"})
browser_execute("return Array.from(document.querySelectorAll('.article-title')).map(el => el.innerText)")
browser_screenshot(false)
```

### Pagination Scraping

```
Loop:
  browser_get_content("text")
  browser_execute("return document.querySelector('.next-page') !== null")  # check next exists
  browser_click(".next-page")
  browser_wait({"selector": ".content-loaded"})
```

## Critical Rules

1. **Always wait after navigate** — elements are not immediately available
   ```
   browser_navigate("https://example.com")
   browser_wait({"selector": "#button"})   # wait for element
   browser_click("#button")
   ```

2. **JavaScript must return values** — `"return document.title"` not `"console.log(document.title)"`

3. **Verify after important actions** — use `browser_get_content` or `browser_wait({"text": "..."})` to confirm success

4. **Error recovery** — if element not found: try longer wait → alternative selector → `browser_execute("return document.body.innerHTML")` to inspect page

## Setup

- Requires EinkBro APK with BrowserForClaw HTTP server running on port 8765
- Port forwarding: `adb forward tcp:8765 tcp:8765`
- See [TOOLS_REFERENCE.md](./TOOLS_REFERENCE.md) for full API details, [QUICK_START.md](./QUICK_START.md) for setup, and [PHONEFORCLAW_HTTP_CLIENT.md](./PHONEFORCLAW_HTTP_CLIENT.md) for HTTP client implementation
