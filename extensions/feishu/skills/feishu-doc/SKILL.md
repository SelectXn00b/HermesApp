---
name: feishu-doc
description: |
  Feishu document operations. Aligned with ByteDance official @larksuite/openclaw-lark plugin.
  Activate when user mentions Feishu docs, cloud docs, or docx links.
---

# Feishu Document Tools

Aligned with ByteDance official `@larksuite/openclaw-lark` plugin.

## Tools

### feishu_fetch_doc — Read Document

```json
{ "doc_id": "ABC123def", "offset": 0, "limit": 5000 }
```

- `doc_id` (required): Document ID or full URL (auto-parsed)
- `offset`: Character offset for pagination
- `limit`: Max characters to return
- Returns: title, content (plain text), total_length, has_more, next_offset

### feishu_create_doc — Create Document

```json
{ "title": "New Doc", "markdown": "# Hello\n\nContent...", "folder_token": "fldcnXXX" }
```

- `markdown`: Markdown content
- `title`: Document title
- `folder_token`: Parent folder token
- `wiki_node`: Wiki node token or URL (mutually exclusive with folder_token/wiki_space)
- `wiki_space`: Wiki space ID or `my_library` (mutually exclusive with folder_token/wiki_node)

### feishu_update_doc — Update Document

7 update modes with selection-based editing:

```json
{ "doc_id": "ABC123def", "mode": "append", "markdown": "New content..." }
```

**Modes:**
- `overwrite` — Replace entire document content
- `append` — Add content to end
- `replace_range` — Replace selected range with new content
- `replace_all` — Find and replace all occurrences
- `insert_before` — Insert content before selection
- `insert_after` — Insert content after selection
- `delete_range` — Delete selected range (no markdown needed)

**Selection (required for range/insert/delete modes):**
- `selection_with_ellipsis`: `"start text...end text"` — locates range by matching start and end content
- `selection_by_title`: `"## Section Title"` — locates the section under this heading

**Additional:**
- `new_title`: Set new document title

### feishu_doc_media — Document Media

```json
{ "action": "insert", "doc_id": "ABC123def", "file_path": "/path/to/image.png", "type": "image" }
```

**Actions:**
- `insert` — Insert image or file into document
  - `doc_id`, `file_path` (required), `type` (image/file), `align` (left/center/right)
- `download` — Download document media
  - `resource_token`, `resource_type` (media/whiteboard), `output_path` (required)

### feishu_doc_comments — Document Comments

```json
{ "action": "list", "file_token": "ABC123def", "file_type": "docx" }
```

**Actions:**
- `list` — List comments with replies (supports pagination, filtering)
- `create` — Create whole-document comment with rich text elements
- `patch` — Resolve or restore a comment

## URL Parsing

Document ID is auto-extracted from URLs:
- `https://xxx.feishu.cn/docx/ABC123def` → `ABC123def`
- `https://xxx.larksuite.com/docx/ABC123def` → `ABC123def`

## Permissions

Required: `docx:document`, `docx:document:readonly`, `drive:drive`
