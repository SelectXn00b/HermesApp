---
name: model-usage
description: "Track and analyze LLM token consumption, generate cost reports by model and time period, compare provider pricing, and manage budget alerts. Use when the user asks about API usage, token counts, costs, spending optimization, or budget status."
metadata: { "openclaw": { "emoji": "📊", "always": false } }
---

# Model Usage

Track LLM token usage and costs to help users understand and optimize their AI spending.

## Workflow

1. **Read usage logs** — `read_file(path: "/sdcard/.androidforclaw/logs/usage/usage_YYYY-MM.json")`
2. **Parse and analyze** — use `javascript_exec` to compute aggregations
3. **Generate report** — present daily/monthly summary with per-model breakdown
4. **Compare against budget** — check configured limits in `openclaw.json`

## Usage Data Location

```
/sdcard/.androidforclaw/
├── config/openclaw.json          # Model config and pricing
└── logs/usage/
    ├── usage_2026-03.json        # Current month
    └── usage_2026-02.json        # Previous months
```

## Log Format

Each monthly file contains sessions with token counts and costs:

```json
{
  "month": "2026-03",
  "sessions": [{
    "sessionId": "session_20260308_143022",
    "model": "claude-opus-4-6",
    "provider": "anthropic",
    "usage": { "promptTokens": 1250, "completionTokens": 850, "totalTokens": 2100, "cachedTokens": 200 },
    "cost": { "inputCost": 0.0375, "outputCost": 0.0425, "totalCost": 0.0800 }
  }],
  "summary": {
    "totalSessions": 145,
    "totalTokens": 304500,
    "totalCost": 11.62,
    "byModel": {
      "claude-opus-4-6": { "sessions": 120, "tokens": 280000, "cost": 10.50 },
      "gpt-4o": { "sessions": 25, "tokens": 24500, "cost": 1.12 }
    }
  }
}
```

## Analyzing Usage

```javascript
javascript_exec(code: `
  const usage = JSON.parse(read_file("/sdcard/.androidforclaw/logs/usage/usage_2026-03.json"));
  const days = new Date().getDate();
  const dailyAvg = usage.summary.totalCost / days;
  const models = Object.entries(usage.summary.byModel)
    .sort((a, b) => b[1].cost - a[1].cost);
  JSON.stringify({
    monthlyTotal: usage.summary.totalCost,
    dailyAverage: dailyAvg.toFixed(2),
    projectedMonthly: (dailyAvg * 30).toFixed(2),
    topModels: models
  });
`)
```

## Cost Optimization

**Model selection by task complexity:**
- Complex reasoning/code → Claude Opus 4.6 (highest quality, highest cost)
- Text processing/extraction → Claude Sonnet 4.6 (balanced)
- Quick queries/simple ops → Claude Haiku 4.5 (fast, lowest cost)

**Reduce token usage:**
- Keep system prompts concise
- Remove unnecessary skills from context
- Use memory tools instead of long context windows
- Limit screenshot frequency — use `get_view_tree()` when possible

**Leverage caching:**
- Anthropic Prompt Caching saves up to 90% on repeated prompts
- System prompts and large documents are automatically cached on reuse

## Budget Alerts

Model pricing is in `/sdcard/.androidforclaw/openclaw.json`. When spending exceeds thresholds, recommend switching to cheaper models for routine tasks and reviewing high-cost operations.

## Tools

- `read_file` — read usage logs and config
- `list_dir` — list available usage log files
- `javascript_exec` — parse and analyze JSON data
- `write_file` — update budget settings
