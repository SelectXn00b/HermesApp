<!-- chat-info: title=Python 编程讨论, group=编程技术 -->
---
title: Python 编程讨论
group: 编程技术
---

# Python 编程讨论

<!-- msg: user -->
## 👤 User

如何在 Python 中读取 JSON 文件？

<!-- msg: ai, model=gpt-4 -->
## 🤖 Assistant

*Model: gpt-4*

在 Python 中读取 JSON 文件很简单，可以使用内置的 `json` 模块：

```python
import json

# 方法1：读取 JSON 文件
with open('data.json', 'r', encoding='utf-8') as f:
    data = json.load(f)
```

**关键要点**：
- `json.load()` 用于从文件对象读取
- `json.loads()` 用于从字符串读取

<!-- msg: user -->
## 👤 User

如果 JSON 文件很大怎么办？

<!-- msg: ai, model=gpt-4 -->
## 🤖 Assistant

*Model: gpt-4*

对于大型 JSON 文件，可以考虑以下方法：

1. **流式处理**：逐行读取
2. **使用 ijson 库**：增量解析
