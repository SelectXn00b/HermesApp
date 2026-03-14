---
name: feishu-doc
description: |
  飞书（Feishu）文档读写操作。当用户提到飞书文档、feishu doc、云文档、或包含 feishu.cn/docx/ 链接时激活。
---

# 飞书文档工具

## Token 提取

从 URL `https://xxx.feishu.cn/docx/ABC123def` → `document_id` = `ABC123def`

## 可用工具

### feishu_doc_read — 读取文档内容

```json
{ "document_id": "ABC123def" }
```

返回：文档纯文本内容。

### feishu_doc_create — 创建新文档

```json
{ "title": "文档标题", "content": "可选的初始内容", "folder_id": "可选的文件夹ID" }
```

### feishu_doc_update — 更新文档内容

```json
{ "document_id": "ABC123def", "content": "要添加的内容" }
```

### feishu_doc_delete — 删除文档

```json
{ "document_id": "ABC123def" }
```

## 注意事项

- `document_id` 从飞书文档 URL 的 `/docx/` 后面提取，保持原始大小写
- 读取返回纯文本（raw_content），不含格式信息
- 更新操作是追加内容，不是替换
