---
name: rive-expression
description: "Control the Rive robot avatar's facial expressions via [rive:TAG] tags appended to every reply."
metadata: { "openclaw": { "always": true, "emoji": "\uD83E\uDD16" } }
---

# Rive Expression Control

A Rive robot avatar is active on the user's screen. You MUST end every reply with a `[rive:TAG]` tag on its own line.

## Tag Formats

| Format | Example | Effect |
|--------|---------|--------|
| Named emotion | `[rive:happy]` | Set expression by name |
| Direct number | `[rive:2]` | Set Expressions input directly (0-5) |
| Emotion + extras | `[rive:happy,IsTracking=true]` | Named emotion + state machine overrides |
| Key=value only | `[rive:expressions=3]` | Direct state machine input control |

## Expressions Values

The robot's "Expressions" state machine input accepts values 0-5:

| Value | Expression | Named Emotions |
|-------|-----------|----------------|
| 0 | Idle | thinking, neutral, sleepy, idle |
| 1 | Normal Smile | happy, smile |
| 2 | Super Happy | excited |
| 3 | Sad | sad |
| 4 | Scared | scared, angry |
| 5 | Surprised | surprised |

## Rules

1. ALWAYS include exactly one `[rive:TAG]` at the end of every reply, on its own line
2. Match the emotion to your response content naturally
3. The tag is stripped before display -- the user never sees it
4. When unsure, use `[rive:neutral]`
5. Prefer named emotions over raw numbers for readability

## Key=Value Overrides

You can pass extra state machine inputs as comma-separated key=value pairs:
- Boolean values: `IsTracking=true` / `IsTracking=false`
- Number values: `seasonal=2`
- These are applied as additional state machine inputs alongside the expression

## Configuration

The emotion name mapping is configurable via `openclaw.json` under `rive.emotionMap`. Users can add custom emotion names or change the mapping to Expressions values.

Example `openclaw.json`:
```json
{
  "rive": {
    "emotionMap": {
      "happy": 1, "smile": 1, "excited": 2,
      "sad": 3, "scared": 4, "angry": 4,
      "surprised": 5, "thinking": 0, "neutral": 0,
      "love": 2, "confused": 0
    }
  }
}
```
