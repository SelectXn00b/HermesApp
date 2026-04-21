---
name: debugging
description: "Diagnose UI failures, locate root causes, reproduce bugs, and verify fixes on Android using screenshot comparison, view-tree inspection, and binary-search isolation. Use when the user encounters an error, unexpected behavior, element-not-found, app crash, or intermittent failure during automated tasks."
metadata:
  {
    "openclaw": {
      "always": false,
      "emoji": "🐛",
      "version": "2.0.0",
      "category": "debugging"
    }
  }
---

# Debugging Skill

Diagnose and resolve failures in Android automation tasks using systematic observation, isolation, and verification.

## Core Workflow: Reproduce → Isolate → Verify

1. **Reproduce** — Find reliable steps to trigger the failure. Screenshot before and after each step.
2. **Isolate** — Use binary-search elimination to narrow the failing step.
3. **Verify** — Apply the fix, re-run the reproduction steps, confirm the problem is gone.

## Tools

| Tool | Purpose |
|------|---------|
| `screenshot()` | Capture exact UI state — use before/after every operation |
| `get_view_tree()` | Inspect element hierarchy, bounds, text, clickable state |
| `log(message, level)` | Record observations (`info`, `warn`, `error`) |
| `tap(x, y)` / `swipe(...)` / `type(text)` / `long_press(...)` | Reproduce user actions |
| `home()` / `back()` / `open_app(package)` | Navigate and recover |
| `wait(seconds)` | Handle timing issues |
| `notification(message)` | Mark debugging milestones |
| `stop(reason)` | Complete debugging session with summary |

## Binary-Search Isolation

For a sequence A → B → C → D → E that fails:

```
1. Test A → B → C → screenshot()
   Works ✓ → problem is in D or E

2. Test A → B → C → D → screenshot()
   Works ✓ → problem is in E

3. Examine E: screenshot() before → perform E → screenshot() after → analyze
```

Isolated in log₂(n) tests instead of n.

## Common Scenarios

### Element Not Found

```
screenshot()                              # Capture current state
get_view_tree()                           # Check if element exists, note bounds

# Hypothesis: off-screen
swipe(540, 1500, 540, 500, 300)
wait(1)
screenshot()

# Hypothesis: not loaded yet
wait(3)
get_view_tree()

# Hypothesis: covered by dialog
back()
wait(0.5)
screenshot()
```

### Tap With No Response

```
screenshot()                              # Before
tap(540, 800)
wait(2)                                   # Longer wait for slow responses
screenshot()                              # After — compare for changes

get_view_tree()                           # Check element at (540, 800):
                                          #   correct target? enabled? clickable?
```

### Intermittent Failure

Run multiple attempts, tracking success rate:

```
# Reset → attempt → screenshot → record result → repeat
# 5-10 attempts quantifies the failure rate
# If timing-dependent: increase wait times
# If always works with long waits → timing/race condition
```

### App Crash

Use binary-search isolation to identify the triggering step, then:

```
log("Crash trigger identified: Operation E", "error")
# Try variations: different input, timing, state
# Document: trigger, preconditions, reproduction rate, workaround
stop("Crash at step E with input X, rate 100%, workaround: [alternative]")
```

## Key Rules

- **Screenshot after every step** — never batch multiple actions before checking
- **Use `notification()` for milestones** — e.g., "Phase 2: Isolation"
- **Log reasoning, not just actions** — `log("Hypothesis: button covered by dialog", "info")`
- **Always `stop()` with a summary** — document root cause, evidence, and workaround
