---
name: data-processing
description: "Parse JSON and CSV data, compute statistics (sum, average, max, min), filter and group arrays, transform objects, and format strings using the JavaScript executor. Use when the user asks to analyze data, calculate numbers, process JSON/CSV, transform arrays, or perform string manipulation."
metadata:
  {
    "openclaw": {
      "always": false,
      "emoji": "📊"
    }
  }
---

# Data Processing Skill

Execute JavaScript for data transformation, analysis, and calculations via the `javascript` tool.

## Tool

```json
{
  "tool": "javascript",
  "args": {
    "code": "const arr = [1,2,3]; arr.map(x => x * 2)"
  }
}
```

The last expression value is automatically returned. Use `JSON.stringify()` for objects/arrays.

## Examples

### Array Statistics

```javascript
const numbers = [15, 23, 8, 42, 16, 4, 31];
const sum = numbers.reduce((a, b) => a + b, 0);
JSON.stringify({
  sum,
  avg: sum / numbers.length,
  max: Math.max(...numbers),
  min: Math.min(...numbers)
});
```

### Filter, Group, and Transform

```javascript
const items = [
  { name: "apple", category: "fruit", price: 1.2 },
  { name: "banana", category: "fruit", price: 0.8 },
  { name: "carrot", category: "vegetable", price: 0.5 },
  { name: "broccoli", category: "vegetable", price: 1.5 }
];
const grouped = items.reduce((acc, item) => {
  (acc[item.category] = acc[item.category] || []).push(item);
  return acc;
}, {});
JSON.stringify(grouped, null, 2);
```

### String Processing

```javascript
const text = "Hello, World! How are you?";
JSON.stringify({
  length: text.length,
  words: text.split(/\s+/).length,
  reversed: text.split('').reverse().join('')
});
```

## Common Patterns

```javascript
// Sum
arr.reduce((a, b) => a + b, 0)

// Unique values
[...new Set(arr)]

// Count occurrences
arr.reduce((acc, val) => { acc[val] = (acc[val] || 0) + 1; return acc; }, {})

// Flatten nested
arr.flat(Infinity)
```

## Constraints

- **Synchronous only** — no async/await
- **Pure JavaScript** — no Node.js APIs (fs, http), no DOM, no external libraries
- ES6+ features available: arrow functions, const/let, template strings, destructuring, spread
