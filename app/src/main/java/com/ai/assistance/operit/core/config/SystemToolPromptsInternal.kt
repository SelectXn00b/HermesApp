package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

object SystemToolPromptsInternal {

    val internalToolCategoriesEn: List<SystemToolPromptCategory> =
        listOf(
            SystemToolPromptCategory(
                categoryName = "Internal Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "execute_shell",
                            description = "Execute a device shell command.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "shell command to execute",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "create_terminal_session",
                            description = "Create or get a terminal session.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_name",
                                        type = "string",
                                        description = "terminal session name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_in_terminal_session",
                            description = "Execute a command in a terminal session and collect full output.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "command to execute",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, command timeout in milliseconds",
                                        required = false,
                                        default = "1800000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_hidden_terminal_command",
                            description = "Execute a command in a hidden non-PTY terminal executor. Commands using the same executor_key reuse the same hidden login context and are not shown in the visible terminal UI.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "command to execute",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "executor_key",
                                        type = "string",
                                        description = "optional, hidden executor key used to reuse the same background shell context",
                                        required = false,
                                        default = "default"
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, command timeout in milliseconds",
                                        required = false,
                                        default = "120000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "input_in_terminal_session",
                            description = "Write input to a terminal session. At least one of input or control is required. Typical usage is sending input first, then control=enter to submit.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "input",
                                        type = "string",
                                        description = "text to write to the terminal (can include newlines)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "control",
                                        type = "string",
                                        description = "control key or modifier (e.g. enter/tab/esc/up/down/left/right/home/end/pageup/pagedown, or ctrl with input=c for Ctrl+C)",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "close_terminal_session",
                            description = "Close a terminal session.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_terminal_session_screen",
                            description = "Get only the current visible PTY screen content for a terminal session (single screen, no scrollback/history).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "terminal session id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "browser_click",
                            description = "Click an element on the current page by browser_snapshot ref, including refs inside same-origin iframes.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "ref", type = "string", description = "target element ref from browser_snapshot output; provide ref or selector", required = false),
                                    ToolParameterSchema(name = "selector", type = "string", description = "optional CSS selector fallback when ref is not available", required = false),
                                    ToolParameterSchema(name = "element", type = "string", description = "optional, human-readable element description", required = false),
                                    ToolParameterSchema(name = "doubleClick", type = "boolean", description = "optional, perform a double click instead of a single click", required = false, default = "false"),
                                    ToolParameterSchema(name = "button", type = "string", description = "optional mouse button: left/right/middle", required = false, default = "left"),
                                    ToolParameterSchema(name = "modifiers", type = "array", description = "optional modifier keys array: Alt/Control/ControlOrMeta/Meta/Shift", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_close",
                            description = "Close the current browser tab. Closing the last tab also closes the browser overlay.",
                            parametersStructured = emptyList()
                        ),
                        ToolPrompt(
                            name = "browser_console_messages",
                            description = "Read browser console messages for the current page.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "level", type = "string", description = "optional console level: error/warning/info/debug", required = false, default = "info"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "optional output file name for large results", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_drag",
                            description = "Perform drag and drop between two page elements.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "startElement", type = "string", description = "human-readable source element description", required = true),
                                    ToolParameterSchema(name = "startRef", type = "string", description = "source element ref from browser_snapshot output", required = true),
                                    ToolParameterSchema(name = "endElement", type = "string", description = "human-readable target element description", required = true),
                                    ToolParameterSchema(name = "endRef", type = "string", description = "target element ref from browser_snapshot output", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_evaluate",
                            description = "Evaluate a JavaScript function on the page or on a target element.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "function", type = "string", description = "() => { ... } or (element) => { ... }", required = true),
                                    ToolParameterSchema(name = "element", type = "string", description = "optional, human-readable element description", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "optional target element ref; required when element is provided", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_file_upload",
                            description = "Upload one or multiple files to the active file chooser. Omit paths to cancel the chooser.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "paths", type = "array", description = "optional absolute file paths", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_fill_form",
                            description = "Fill multiple form fields on the current page.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "fields", type = "array", description = "array of field objects with name/type/value plus ref or selector", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_handle_dialog",
                            description = "Accept or dismiss the currently open dialog.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "accept", type = "boolean", description = "true to accept, false to dismiss", required = true),
                                    ToolParameterSchema(name = "promptText", type = "string", description = "optional prompt text when handling a prompt dialog", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_hover",
                            description = "Hover over an element on the current page.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "optional, human-readable element description", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "target element ref from browser_snapshot output", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_navigate",
                            description = "Navigate the active browser tab to a URL. If no tab exists yet, the first tab is created automatically.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "url", type = "string", description = "target URL", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_navigate_back",
                            description = "Go back in the current tab history.",
                            parametersStructured = emptyList()
                        ),
                        ToolPrompt(
                            name = "browser_network_requests",
                            description = "Read network requests recorded for the current page.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "includeStatic", type = "boolean", description = "optional, include static asset requests", required = false, default = "false"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "optional output file name for large results", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_press_key",
                            description = "Press a keyboard key in the current page.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "key", type = "string", description = "key name, for example ArrowLeft or a", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_resize",
                            description = "Resize the browser viewport.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "width", type = "number", description = "viewport width", required = true),
                                    ToolParameterSchema(name = "height", type = "number", description = "viewport height", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_run_code",
                            description = "Run a Playwright-style code snippet against the current tab.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "code", type = "string", description = "Playwright-style JavaScript snippet", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_select_option",
                            description = "Select option values in a dropdown element.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "optional, human-readable element description", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "target select element ref from browser_snapshot output", required = true),
                                    ToolParameterSchema(name = "values", type = "array", description = "option values or visible texts to select", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_snapshot",
                            description = "Capture a structured accessibility-style snapshot of the current page, including same-origin iframe content.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "filename", type = "string", description = "optional output snapshot file name", required = false),
                                    ToolParameterSchema(name = "selector", type = "string", description = "optional root element selector for a partial snapshot", required = false),
                                    ToolParameterSchema(name = "depth", type = "integer", description = "optional snapshot tree depth limit", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_take_screenshot",
                            description = "Take a screenshot of the current page or of a specific element.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "type", type = "string", description = "optional image type: png or jpeg", required = false, default = "png"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "optional output file name", required = false),
                                    ToolParameterSchema(name = "element", type = "string", description = "optional element description; when present ref is required", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "optional element ref; when present element is required", required = false),
                                    ToolParameterSchema(name = "fullPage", type = "boolean", description = "optional full-page capture; cannot be used with element screenshots", required = false, default = "false")
                                )
                        ),
                        ToolPrompt(
                            name = "browser_type",
                            description = "Type text into an editable element.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "optional, human-readable element description", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "target element ref from browser_snapshot output", required = true),
                                    ToolParameterSchema(name = "text", type = "string", description = "text to type", required = true),
                                    ToolParameterSchema(name = "submit", type = "boolean", description = "optional, press Enter after typing", required = false, default = "false"),
                                    ToolParameterSchema(name = "slowly", type = "boolean", description = "optional, type character by character", required = false, default = "false")
                                )
                        ),
                        ToolPrompt(
                            name = "browser_wait_for",
                            description = "Wait for text to appear, disappear, or for a duration to pass.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "time", type = "number", description = "optional wait duration in seconds", required = false),
                                    ToolParameterSchema(name = "text", type = "string", description = "optional text that must appear", required = false),
                                    ToolParameterSchema(name = "textGone", type = "string", description = "optional text that must disappear", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_tabs",
                            description = "List, create, select, or close browser tabs using 0-based indexes.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "action", type = "string", description = "one of: list, create, select, close", required = true),
                                    ToolParameterSchema(name = "index", type = "integer", description = "optional tab index used by select or close", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "calculate",
                            description = "Evaluate a math expression.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "expression",
                                        type = "string",
                                        description = "math expression, e.g. \"(1+2)*3\"",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_intent",
                            description = "Execute an Android Intent (activity/broadcast/service).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "optional, intent action",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "optional, data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "optional, package name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "optional, component in \"package/class\" format",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "type",
                                        type = "string",
                                        description = "optional, one of activity/broadcast/service",
                                        required = false,
                                        default = "activity"
                                    ),
                                    ToolParameterSchema(
                                        name = "flags",
                                        type = "string",
                                        description = "optional, JSON array string of int flags (or a single int)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "optional, JSON object string for extras",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_broadcast",
                            description = "Send a broadcast intent.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "required, broadcast action",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "optional, data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "optional, package name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "optional, component in \"package/class\" format",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "optional, JSON object string for extras",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_key",
                                        type = "string",
                                        description = "optional, a single string extra key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_value",
                                        type = "string",
                                        description = "optional, a single string extra value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_key2",
                                        type = "string",
                                        description = "optional, second string extra key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_value2",
                                        type = "string",
                                        description = "optional, second string extra value",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "device_info",
                            description = "Get device information.",
                            parametersStructured = listOf()
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Extended Memory Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "create_memory",
                            description = "Creates a new memory node in the library. Use this when you want to save important information for future reference.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "title", type = "string", description = "required, string", required = true),
                                ToolParameterSchema(name = "content", type = "string", description = "required, string", required = true),
                                ToolParameterSchema(name = "content_type", type = "string", description = "optional", required = false, default = "\"text/plain\""),
                                ToolParameterSchema(name = "source", type = "string", description = "optional", required = false, default = "\"ai_created\""),
                                ToolParameterSchema(name = "folder_path", type = "string", description = "optional", required = false, default = "\"\""),
                                ToolParameterSchema(name = "tags", type = "string", description = "optional, comma-separated string", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "update_memory",
                            description = "Updates an existing memory node by title. Use this to modify an existing memory's content or metadata.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "old_title", type = "string", description = "required, string to identify the memory", required = true),
                                ToolParameterSchema(name = "new_title", type = "string", description = "optional, string, new title if renaming", required = false),
                                ToolParameterSchema(name = "content", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "content_type", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "source", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "credibility", type = "number", description = "optional, float 0-1", required = false),
                                ToolParameterSchema(name = "importance", type = "number", description = "optional, float 0-1", required = false),
                                ToolParameterSchema(name = "folder_path", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "tags", type = "string", description = "optional, comma-separated string", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "delete_memory",
                            description = "Deletes a memory node from the library by title. Use with caution as this operation is irreversible.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "title", type = "string", description = "required, string to identify the memory", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "link_memories",
                            description = "Creates a semantic link between two memories in the library. Use this to establish relationships between related concepts, facts, or pieces of information. This helps build a knowledge graph structure for better memory retrieval and understanding.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source_title", type = "string", description = "required, string, the title of the source memory", required = true),
                                ToolParameterSchema(name = "target_title", type = "string", description = "required, string, the title of the target memory", required = true),
                                ToolParameterSchema(name = "link_type", type = "string", description = "optional, string, the type of relationship such as \"related\", \"causes\", \"explains\", \"part_of\", \"contradicts\", etc.", required = false, default = "\"related\""),
                                ToolParameterSchema(name = "weight", type = "number", description = "optional, float 0.0-1.0, the strength of the link with 1.0 being strongest", required = false, default = "0.7"),
                                ToolParameterSchema(name = "description", type = "string", description = "optional, string, additional context about the relationship", required = false, default = "\"\"")
                            )
                        ),
                        ToolPrompt(
                            name = "query_memory_links",
                            description = "Queries links in the memory graph. Supports filtering by link_id, source_title, target_title, and link_type. Use this before updating/deleting links to precisely identify targets.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "link_id", type = "integer", description = "optional, exact link id", required = false),
                                ToolParameterSchema(name = "source_title", type = "string", description = "optional, exact source memory title", required = false),
                                ToolParameterSchema(name = "target_title", type = "string", description = "optional, exact target memory title", required = false),
                                ToolParameterSchema(name = "link_type", type = "string", description = "optional, relation type filter", required = false),
                                ToolParameterSchema(name = "limit", type = "integer", description = "optional, int 1-200, maximum links to return", required = false, default = "20")
                            )
                        ),
                        ToolPrompt(
                            name = "update_user_preferences",
                            description = "Updates user preference information directly. Use this when you learn new information about the user that should be remembered (e.g., their birthday, gender, personality traits, identity, occupation, or preferred AI interaction style). This allows immediate updates without waiting for the automatic system.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "birth_date", type = "integer", description = "optional, Unix timestamp in milliseconds", required = false),
                                ToolParameterSchema(name = "gender", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "personality", type = "string", description = "optional, string describing personality traits", required = false),
                                ToolParameterSchema(name = "identity", type = "string", description = "optional, string describing identity/role", required = false),
                                ToolParameterSchema(name = "occupation", type = "string", description = "optional, string", required = false),
                                ToolParameterSchema(name = "ai_style", type = "string", description = "optional, string describing preferred AI interaction style. At least one parameter must be provided", required = false)
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Extended HTTP Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "http_request",
                            description = "Send HTTP request.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                                ToolParameterSchema(name = "method", type = "string", description = "GET/POST/PUT/DELETE", required = true),
                                ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                                ToolParameterSchema(name = "body", type = "string", description = "body", required = false),
                                ToolParameterSchema(name = "body_type", type = "string", description = "json/form/text/xml", required = false),
                                ToolParameterSchema(name = "ignore_ssl", type = "boolean", description = "ignore https certificate verification, true/false", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "multipart_request",
                            description = "Upload files.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                                ToolParameterSchema(name = "method", type = "string", description = "POST/PUT", required = true),
                                ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                                ToolParameterSchema(name = "form_data", type = "string", description = "form_data", required = false),
                                ToolParameterSchema(name = "files", type = "string", description = "JSON array string. Each item is an object: {\"field_name\": string, \"file_path\": string, \"content_type\"?: string, \"file_name\"?: string}", required = false),
                                ToolParameterSchema(name = "ignore_ssl", type = "boolean", description = "ignore https certificate verification, true/false", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "manage_cookies",
                            description = "Manage cookies.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "action", type = "string", description = "get/set/clear", required = true),
                                ToolParameterSchema(name = "domain", type = "string", description = "domain", required = false),
                                ToolParameterSchema(name = "cookies", type = "string", description = "cookies", required = false)
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Extended File Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "file_exists",
                            description = "Check if a file or directory exists.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "target path", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "move_file",
                            description = "Move or rename a file or directory.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "source path", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "destination path", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "copy_file",
                            description = "Copy a file or directory. Supports cross-environment copying between Android and Linux.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "source path", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "destination path", required = true),
                                ToolParameterSchema(name = "recursive", type = "boolean", description = "boolean", required = false, default = "false"),
                                ToolParameterSchema(name = "source_environment", type = "string", description = "optional, \"android\" or \"linux\"", required = false, default = "\"android\""),
                                ToolParameterSchema(name = "dest_environment", type = "string", description = "optional, \"android\" or \"linux\". For cross-environment copy (e.g., Android → Linux or Linux → Android), specify both source_environment and dest_environment", required = false, default = "\"android\"")
                            )
                        ),
                        ToolPrompt(
                            name = "file_info",
                            description = "Get detailed information about a file or directory including type, size, permissions, owner, group, and last modified time.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "target path", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "zip_files",
                            description = "Compress files or directories.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "path to compress", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "output zip file", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "unzip_files",
                            description = "Extract a zip file.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "zip file path", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "extract path", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "open_file",
                            description = "Open a file using the system's default application.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "file path", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "share_file",
                            description = "Share a file with other applications.",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                                ToolParameterSchema(name = "title", type = "string", description = "optional share title", required = false, default = "\"Share File\"")
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Tasker Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "trigger_tasker_event",
                            description = "Trigger a Tasker event.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "task_type",
                                        type = "string",
                                        description = "Tasker event type",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "arg1",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg2",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg3",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg4",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg5",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "args_json",
                                        type = "string",
                                        description = "optional, JSON object string",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Workflow Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_all_workflows",
                            description = "Get all workflows.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_workflow",
                            description = "Create a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "workflow name",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "optional, nodes JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "optional, connections JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_workflow",
                            description = "Get workflow detail.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_workflow",
                            description = "Update a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "optional, nodes JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "optional, connections JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "optional",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "patch_workflow",
                            description = "Patch a workflow incrementally.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "node_patches",
                                        type = "string",
                                        description = "optional, node patch JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connection_patches",
                                        type = "string",
                                        description = "optional, connection patch JSON array string",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "enable_workflow",
                            description = "Enable a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "disable_workflow",
                            description = "Disable a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_workflow",
                            description = "Delete a workflow.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "trigger_workflow",
                            description = "Trigger a workflow execution.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "workflow id",
                                        required = true
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Chat Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "start_chat_service",
                            description = "Start the floating chat service.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "initial_mode",
                                        type = "string",
                                        description = "optional, initial floating mode: WINDOW, BALL, VOICE_BALL, FULLSCREEN, RESULT_DISPLAY, SCREEN_OCR",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "auto_enter_voice_chat",
                                        type = "boolean",
                                        description = "optional, if true then enter voice mode automatically when opening FULLSCREEN",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "wake_launched",
                                        type = "boolean",
                                        description = "optional, true if launched by wake word so UI can adjust behavior",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, auto close the floating window after this timeout (milliseconds). <=0 disables auto-exit.",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "keep_if_exists",
                                        type = "boolean",
                                        description = "optional, if true and service already running, do not force floating window mode change",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_chat_service",
                            description = "Stop the floating chat service.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_new_chat",
                            description = "Create a new chat.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "group",
                                        type = "string",
                                        description = "optional group name for the new chat",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "set_as_current_chat",
                                        type = "boolean",
                                        description = "optional, whether to switch to the new chat (default true)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "character_card_id",
                                        type = "string",
                                        description = "optional, character card id to bind for the new chat",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_chats",
                            description = "List chats (supports filtering and sorting).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "query",
                                        type = "string",
                                        description = "optional, title keyword filter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "match",
                                        type = "string",
                                        description = "optional, contains | exact | regex (default contains)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "optional, max results (default 50)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sort_by",
                                        type = "string",
                                        description = "optional, updatedAt | createdAt | messageCount (default updatedAt)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sort_order",
                                        type = "string",
                                        description = "optional, asc | desc (default desc)",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "find_chat",
                            description = "Find a chat by title and return its info.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "query",
                                        type = "string",
                                        description = "title keyword/regex",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "match",
                                        type = "string",
                                        description = "optional, contains | exact | regex (default contains)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "optional, pick Nth match (default 0)",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "agent_status",
                            description = "Check a chat's input processing status.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "switch_chat",
                            description = "Switch to a chat.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_chat_title",
                            description = "Update a chat title.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "title",
                                        type = "string",
                                        description = "new chat title",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_chat",
                            description = "Delete a chat by id.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai",
                            description = "Send a user message to AI.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "message content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "optional, target chat id",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "role_card_id",
                                        type = "string",
                                        description = "optional, role card id to use for this send",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sender_name",
                                        type = "string",
                                        description = "optional, display name of the sender when AI sends as user",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai_advanced",
                            description = "Send a user message to AI with advanced runtime controls.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "message content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "optional, target chat id",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_history",
                                        type = "string",
                                        description = "optional, JSON array of [role, content]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "workspace_path",
                                        type = "string",
                                        description = "optional workspace path",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "optional FunctionType enum name, default CHAT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "prompt_function_type",
                                        type = "string",
                                        description = "optional PromptFunctionType enum name, default CHAT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_thinking",
                                        type = "boolean",
                                        description = "optional, enable thinking mode",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "thinking_guidance",
                                        type = "boolean",
                                        description = "optional, enable thinking guidance",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_memory_query",
                                        type = "boolean",
                                        description = "optional, enable memory query",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "max token budget for this request",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "token_usage_threshold",
                                        type = "number",
                                        description = "token usage threshold in range 0..1",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_system_prompt_template",
                                        type = "string",
                                        description = "optional custom system prompt template",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "is_sub_task",
                                        type = "boolean",
                                        description = "optional, marks this request as a sub task",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stream",
                                        type = "boolean",
                                        description = "optional, whether to stream output",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_character_cards",
                            description = "List all role cards.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "get_chat_messages",
                            description = "Get messages from a specific chat (cross-chat history read).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "target chat id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "order",
                                        type = "string",
                                        description = "optional, asc/desc (default desc)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "optional, number of messages to return (default 20, max 200)",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal File Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_file_full",
                            description = "Read the full content of a file without enforcing size limit.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "text_only",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "read_file_binary",
                            description = "Read binary file and return base64 content.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file",
                            description = "Write content to a file.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "content",
                                        type = "string",
                                        description = "file content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "append",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file_binary",
                            description = "Write base64 content to a binary file.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "base64Content",
                                        type = "string",
                                        description = "base64 encoded content",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "optional, \"android\" (default) or \"linux\"",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal UI Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_page_info",
                            description = "Get current page/window UI information.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "optional, xml/json",
                                        required = false,
                                        default = "xml"
                                    ),
                                    ToolParameterSchema(
                                        name = "detail",
                                        type = "string",
                                        description = "optional",
                                        required = false,
                                        default = "summary"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id for multi-display",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "tap",
                            description = "Tap at screen coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "long_press",
                            description = "Long press at screen coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y coordinate",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "swipe",
                            description = "Swipe from start to end coordinates.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "start_x",
                                        type = "integer",
                                        description = "start x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "start_y",
                                        type = "integer",
                                        description = "start y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_x",
                                        type = "integer",
                                        description = "end x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_y",
                                        type = "integer",
                                        description = "end y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "duration",
                                        type = "integer",
                                        description = "optional, duration in ms",
                                        required = false,
                                        default = "300"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "click_element",
                            description = "Click a UI element by resource id / class name / content description / bounds.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "resourceId",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "className",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "contentDesc",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bounds",
                                        type = "string",
                                        description = "optional, format: [left,top][right,bottom]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "partialMatch",
                                        type = "boolean",
                                        description = "optional, enable partial match for selectors",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "0"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_input_text",
                            description = "Set input text in focused field.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "text to input (can be empty to clear)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "press_key",
                            description = "Press a key via keyevent.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key_code",
                                        type = "string",
                                        description = "key code, e.g. KEYCODE_HOME",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "optional, display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "capture_screenshot",
                            description = "Capture a screenshot and return a file path.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "run_ui_subagent",
                            description = "Run a lightweight UI automation subagent.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "intent",
                                        type = "string",
                                        description = "task description",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "max_steps",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "20"
                                    ),
                                    ToolParameterSchema(
                                        name = "agent_id",
                                        type = "string",
                                        description = "optional, reuse agent session id. If omitted or 'default', uses the main screen. If provided and not 'default', the requested virtual screen session must be active/available; otherwise the run fails.",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "target_app",
                                        type = "string",
                                        description = "optional, target app package name",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Software Settings Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_environment_variable",
                            description = "Read current value of an environment variable by key.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key",
                                        type = "string",
                                        description = "environment variable key",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_environment_variable",
                            description = "Write an environment variable by key; empty value clears it.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key",
                                        type = "string",
                                        description = "environment variable key",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "optional, value to write; empty clears the key",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_sandbox_packages",
                            description = "List sandbox packages (built-in and external) with current enabled states and management paths.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "set_sandbox_package_enabled",
                            description = "Enable or disable a sandbox package by package_name.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "sandbox package name",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "true to enable, false to disable",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "restart_mcp_with_logs",
                            description = "Restart MCP plugin startup flow and return per-plugin startup logs.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "optional, max wait time in milliseconds",
                                        required = false,
                                        default = "120000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_speech_services_config",
                            description = "Get current TTS/STT speech services configuration.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "set_speech_services_config",
                            description = "Update TTS/STT speech services configuration fields.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "tts_service_type",
                                        type = "string",
                                        description = "optional, SIMPLE_TTS/HTTP_TTS/OPENAI_WS_TTS/SILICONFLOW_TTS/MINIMAX_TTS/OPENAI_TTS",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_url_template",
                                        type = "string",
                                        description = "optional, TTS endpoint URL template",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_api_key",
                                        type = "string",
                                        description = "optional, TTS API key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_headers",
                                        type = "string",
                                        description = "optional, TTS headers JSON object string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_http_method",
                                        type = "string",
                                        description = "optional, GET/POST",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_request_body",
                                        type = "string",
                                        description = "optional, TTS POST body template",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_content_type",
                                        type = "string",
                                        description = "optional, TTS content type",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_locale",
                                        type = "string",
                                        description = "optional, TTS locale tag such as zh-CN or en-US",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_voice_id",
                                        type = "string",
                                        description = "optional, TTS voice id",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_model_name",
                                        type = "string",
                                        description = "optional, TTS model name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_response_pipeline",
                                        type = "string",
                                        description = "optional, HTTP TTS response pipeline JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_cleaner_regexs",
                                        type = "string",
                                        description = "optional, TTS cleaner regex list JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_speech_rate",
                                        type = "number",
                                        description = "optional, TTS speech rate",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_pitch",
                                        type = "number",
                                        description = "optional, TTS pitch",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_service_type",
                                        type = "string",
                                        description = "optional, SHERPA_NCNN/OPENAI_STT/DEEPGRAM_STT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_endpoint_url",
                                        type = "string",
                                        description = "optional, STT endpoint URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_api_key",
                                        type = "string",
                                        description = "optional, STT API key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_model_name",
                                        type = "string",
                                        description = "optional, STT model name",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "test_tts_playback",
                            description = "Play one TTS test utterance using the current speech-service configuration.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "required, text to play once via the current TTS service",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "interrupt",
                                        type = "boolean",
                                        description = "optional, whether to interrupt current playback first",
                                        required = false,
                                        default = "true"
                                    ),
                                    ToolParameterSchema(
                                        name = "speech_rate",
                                        type = "number",
                                        description = "optional, override speech rate for this test only",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "pitch",
                                        type = "number",
                                        description = "optional, override pitch for this test only",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_model_configs",
                            description = "List all model configs and function-to-config bindings.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_model_config",
                            description = "Create a model config. Optional fields can be provided at creation.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "optional, config display name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_provider_type",
                                        type = "string",
                                        description = "optional, provider enum name (e.g. OPENAI_GENERIC/OPENAI_RESPONSES_GENERIC/DEEPSEEK/GEMINI_GENERIC/OLLAMA/MNN/LLAMA_CPP)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_endpoint",
                                        type = "string",
                                        description = "optional, API endpoint URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_key",
                                        type = "string",
                                        description = "optional, API key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "model_name",
                                        type = "string",
                                        description = "optional, model name; multiple models can be comma-separated",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens_enabled",
                                        type = "boolean",
                                        description = "optional, enable max_tokens parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "optional, max_tokens value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature_enabled",
                                        type = "boolean",
                                        description = "optional, enable temperature parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature",
                                        type = "number",
                                        description = "optional, temperature value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p_enabled",
                                        type = "boolean",
                                        description = "optional, enable top_p parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p",
                                        type = "number",
                                        description = "optional, top_p value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k_enabled",
                                        type = "boolean",
                                        description = "optional, enable top_k parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k",
                                        type = "integer",
                                        description = "optional, top_k value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable presence_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty",
                                        type = "number",
                                        description = "optional, presence_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable frequency_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty",
                                        type = "number",
                                        description = "optional, frequency_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable repetition_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty",
                                        type = "number",
                                        description = "optional, repetition_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "context_length",
                                        type = "number",
                                        description = "optional, base context length",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_context_length",
                                        type = "number",
                                        description = "optional, max context length",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_max_context_mode",
                                        type = "boolean",
                                        description = "optional, use max_context_length as active context",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_token_threshold",
                                        type = "number",
                                        description = "optional, token-ratio threshold for context summary trigger (0~1)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary",
                                        type = "boolean",
                                        description = "optional, enable context summary",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary_by_message_count",
                                        type = "boolean",
                                        description = "optional, enable summary trigger by message count",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_message_count_threshold",
                                        type = "integer",
                                        description = "optional, message-count threshold for summary trigger",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_parameters",
                                        type = "string",
                                        description = "optional, custom parameters JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_headers",
                                        type = "string",
                                        description = "optional, custom request headers JSON object string",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_model_config",
                            description = "Update fields of an existing model config by config_id.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "target model config id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "optional, config display name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_provider_type",
                                        type = "string",
                                        description = "optional, provider enum name",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_endpoint",
                                        type = "string",
                                        description = "optional, API endpoint URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_key",
                                        type = "string",
                                        description = "optional, API key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "model_name",
                                        type = "string",
                                        description = "optional, model name; multiple models can be comma-separated",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens_enabled",
                                        type = "boolean",
                                        description = "optional, enable max_tokens parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "optional, max_tokens value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature_enabled",
                                        type = "boolean",
                                        description = "optional, enable temperature parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature",
                                        type = "number",
                                        description = "optional, temperature value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p_enabled",
                                        type = "boolean",
                                        description = "optional, enable top_p parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p",
                                        type = "number",
                                        description = "optional, top_p value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k_enabled",
                                        type = "boolean",
                                        description = "optional, enable top_k parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k",
                                        type = "integer",
                                        description = "optional, top_k value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable presence_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty",
                                        type = "number",
                                        description = "optional, presence_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable frequency_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty",
                                        type = "number",
                                        description = "optional, frequency_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty_enabled",
                                        type = "boolean",
                                        description = "optional, enable repetition_penalty parameter",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty",
                                        type = "number",
                                        description = "optional, repetition_penalty value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "context_length",
                                        type = "number",
                                        description = "optional, base context length",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_context_length",
                                        type = "number",
                                        description = "optional, max context length",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_max_context_mode",
                                        type = "boolean",
                                        description = "optional, use max_context_length as active context",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_token_threshold",
                                        type = "number",
                                        description = "optional, token-ratio threshold for context summary trigger (0~1)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary",
                                        type = "boolean",
                                        description = "optional, enable context summary",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary_by_message_count",
                                        type = "boolean",
                                        description = "optional, enable summary trigger by message count",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_message_count_threshold",
                                        type = "integer",
                                        description = "optional, message-count threshold for summary trigger",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_parameters",
                                        type = "string",
                                        description = "optional, custom parameters JSON array string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_headers",
                                        type = "string",
                                        description = "optional, custom request headers JSON object string",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_image_processing",
                                        type = "boolean",
                                        description = "optional, enable direct image processing",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_audio_processing",
                                        type = "boolean",
                                        description = "optional, enable direct audio processing",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_video_processing",
                                        type = "boolean",
                                        description = "optional, enable direct video processing",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_google_search",
                                        type = "boolean",
                                        description = "optional, Gemini grounding switch",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_tool_call",
                                        type = "boolean",
                                        description = "optional, enable provider-native tool call",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "mnn_forward_type",
                                        type = "integer",
                                        description = "optional, MNN forward type",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "mnn_thread_count",
                                        type = "integer",
                                        description = "optional, MNN thread count",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_thread_count",
                                        type = "integer",
                                        description = "optional, llama.cpp thread count",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_context_size",
                                        type = "integer",
                                        description = "optional, llama.cpp context size",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_gpu_layers",
                                        type = "integer",
                                        description = "optional, llama.cpp GPU layer count",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "request_limit_per_minute",
                                        type = "integer",
                                        description = "optional, requests-per-minute limit (0 = unlimited)",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_concurrent_requests",
                                        type = "integer",
                                        description = "optional, max concurrent requests (0 = unlimited)",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_model_config",
                            description = "Delete a model config by config_id (default config cannot be deleted).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "target model config id",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_function_model_configs",
                            description = "List function model bindings only (function_type -> config_id + model_index).",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "get_function_model_config",
                            description = "Get the single model config bound to one function_type.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "function type enum name (CHAT/SUMMARY/MEMORY/UI_CONTROLLER/TRANSLATION/GREP/IMAGE_RECOGNITION/AUDIO_RECOGNITION/VIDEO_RECOGNITION)",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_function_model_config",
                            description = "Bind one function type to a model config (and optional model_index).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "function type enum name (CHAT/SUMMARY/MEMORY/UI_CONTROLLER/TRANSLATION/GREP/IMAGE_RECOGNITION/AUDIO_RECOGNITION/VIDEO_RECOGNITION)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "target model config id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "model_index",
                                        type = "integer",
                                        description = "optional, selected model index when model_name contains multiple models",
                                        required = false,
                                        default = "0"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "test_model_config_connection",
                            description = "Run the same model-config connection checks as settings UI for a given config_id.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "target model config id",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "model_index",
                                        type = "integer",
                                        description = "optional, selected model index when model_name contains multiple models",
                                        required = false,
                                        default = "0"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Internal System Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "close_all_virtual_displays",
                            description = "Close all virtual display overlays.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "modify_system_setting",
                            description = "Modify a system setting.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "setting key (alias: key)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "setting value",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "optional, system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_system_setting",
                            description = "Get a system setting.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "setting key (alias: key)",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "optional, system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "install_app",
                            description = "Request installing an APK.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "APK file path (alias: path)",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "uninstall_app",
                            description = "Request uninstalling an app.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_installed_apps",
                            description = "List installed apps.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "start_app",
                            description = "Start an app.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "activity",
                                        type = "string",
                                        description = "optional, activity class name",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_app",
                            description = "Stop an app background process.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "app package name",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_notifications",
                            description = "Get device notifications.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "optional",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_ongoing",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_app_usage_time",
                            description = "Get foreground app usage time from Android Usage Access. If permission is missing, ask the user to grant Usage Access first.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "optional, exact app package name to query",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "since_hours",
                                        type = "integer",
                                        description = "optional, look back this many hours",
                                        required = false,
                                        default = "24"
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "optional, max apps to return when package_name is not provided",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "optional, include system apps when package_name is not provided",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "toast",
                            description = "Show a short toast message on the device.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "toast text",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_notification",
                            description = "Send a notification using the AI reply completion notification channel.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "title",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "notification body",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_device_location",
                            description = "Get device location.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout",
                                        type = "integer",
                                        description = "optional, seconds",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "high_accuracy",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_address",
                                        type = "boolean",
                                        description = "optional",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "FFmpeg Tools",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "ffmpeg_execute",
                            description = "Execute an FFmpeg command (arguments only; do not include the leading ffmpeg).",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "FFmpeg command arguments only, without the leading ffmpeg",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "ffmpeg_info",
                            description = "Get FFmpeg information.",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "ffmpeg_convert",
                            description = "Convert a video file using FFmpeg.",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "input_path",
                                        type = "string",
                                        description = "input file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "output_path",
                                        type = "string",
                                        description = "output file path",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "resolution",
                                        type = "string",
                                        description = "optional, e.g. 1280x720",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bitrate",
                                        type = "string",
                                        description = "optional, e.g. 1000k",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "audio_codec",
                                        type = "string",
                                        description = "optional",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "video_codec",
                                        type = "string",
                                        description = "optional, use h264 for H.264 encoding",
                                        required = false
                                    )
                                )
                        )
                    )
            )
        )

    val internalToolCategoriesCn: List<SystemToolPromptCategory> =
        listOf(
            SystemToolPromptCategory(
                categoryName = "内部工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "execute_shell",
                            description = "执行设备 Shell 命令。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "要执行的命令",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "create_terminal_session",
                            description = "创建或获取终端会话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_name",
                                        type = "string",
                                        description = "终端会话名称",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_in_terminal_session",
                            description = "在终端会话中执行命令，并一次性返回完整输出。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "要执行的命令",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "可选，超时时间（毫秒）",
                                        required = false,
                                        default = "1800000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_hidden_terminal_command",
                            description = "在隐藏的非 PTY 终端执行器中执行命令。使用相同 executor_key 的命令会复用同一个后台登录上下文，且不会显示在可见终端 UI 中。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "要执行的命令",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "executor_key",
                                        type = "string",
                                        description = "可选，用于复用同一个后台 shell 上下文的隐藏执行器 key",
                                        required = false,
                                        default = "default"
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "可选，超时时间（毫秒）",
                                        required = false,
                                        default = "120000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "input_in_terminal_session",
                            description = "向终端会话写入输入。input 与 control 至少传一个。通常先发送 input，再发送 control=enter 提交内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "input",
                                        type = "string",
                                        description = "要写入终端的文本（可包含换行）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "control",
                                        type = "string",
                                        description = "控制键或修饰键（如 enter/tab/esc/up/down/left/right/home/end/pageup/pagedown，或 control=ctrl 且 input=c 表示 Ctrl+C）",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "close_terminal_session",
                            description = "关闭终端会话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_terminal_session_screen",
                            description = "获取终端会话当前可见 PTY 屏幕内容（仅一屏，不包含历史滚动缓冲）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "session_id",
                                        type = "string",
                                        description = "终端会话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "browser_click",
                            description = "按 browser_snapshot 的 ref 点击当前页面元素，包括同源 iframe 内的 ref。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "ref", type = "string", description = "来自 browser_snapshot 输出的目标元素 ref；ref 和 selector 至少提供一个", required = false),
                                    ToolParameterSchema(name = "selector", type = "string", description = "可选，ref 不可用时的 CSS 选择器兜底", required = false),
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，人类可读元素描述", required = false),
                                    ToolParameterSchema(name = "doubleClick", type = "boolean", description = "可选，是否双击", required = false, default = "false"),
                                    ToolParameterSchema(name = "button", type = "string", description = "可选鼠标按键：left/right/middle", required = false, default = "left"),
                                    ToolParameterSchema(name = "modifiers", type = "array", description = "可选修饰键数组：Alt/Control/ControlOrMeta/Meta/Shift", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_close",
                            description = "关闭当前浏览器 tab。关闭最后一个 tab 时也会关闭浏览器浮窗。",
                            parametersStructured = emptyList()
                        ),
                        ToolPrompt(
                            name = "browser_console_messages",
                            description = "读取当前页面的浏览器控制台消息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "level", type = "string", description = "可选，控制台级别：error/warning/info/debug", required = false, default = "info"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "可选，大结果输出文件名", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_drag",
                            description = "在两个页面元素之间执行拖拽。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "startElement", type = "string", description = "源元素的人类可读描述", required = true),
                                    ToolParameterSchema(name = "startRef", type = "string", description = "源元素 ref", required = true),
                                    ToolParameterSchema(name = "endElement", type = "string", description = "目标元素的人类可读描述", required = true),
                                    ToolParameterSchema(name = "endRef", type = "string", description = "目标元素 ref", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_evaluate",
                            description = "在页面上或目标元素上执行 JavaScript 函数。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "function", type = "string", description = "() => { ... } 或 (element) => { ... }", required = true),
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，人类可读元素描述", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "可选，目标元素 ref；提供 element 时必须同时提供", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_file_upload",
                            description = "向当前 file chooser 上传一个或多个文件。不传 paths 时取消选择器。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "paths", type = "array", description = "可选，绝对文件路径数组", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_fill_form",
                            description = "批量填写当前页面的多个表单字段。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "fields", type = "array", description = "字段对象数组，每项包含 name/type/value 以及 ref 或 selector", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_handle_dialog",
                            description = "接受或取消当前打开的对话框。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "accept", type = "boolean", description = "true 表示接受，false 表示取消", required = true),
                                    ToolParameterSchema(name = "promptText", type = "string", description = "可选，处理 prompt 时输入的文本", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_hover",
                            description = "悬停到当前页面的目标元素上。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，人类可读元素描述", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "目标元素 ref", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_navigate",
                            description = "让当前活动 tab 跳转到指定 URL。若当前没有 tab，会自动创建首个 tab。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "url", type = "string", description = "目标 URL", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_navigate_back",
                            description = "在当前 tab 历史中后退。",
                            parametersStructured = emptyList()
                        ),
                        ToolPrompt(
                            name = "browser_network_requests",
                            description = "读取当前页面记录到的网络请求。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "includeStatic", type = "boolean", description = "可选，是否包含静态资源请求", required = false, default = "false"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "可选，大结果输出文件名", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_press_key",
                            description = "在当前页面按下一个键盘按键。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "key", type = "string", description = "按键名，例如 ArrowLeft 或 a", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_resize",
                            description = "调整浏览器视口大小。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "width", type = "number", description = "视口宽度", required = true),
                                    ToolParameterSchema(name = "height", type = "number", description = "视口高度", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_run_code",
                            description = "运行 Playwright 风格的代码片段。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "code", type = "string", description = "Playwright 风格 JavaScript 代码片段", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_select_option",
                            description = "在下拉元素中选择一个或多个选项值。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，人类可读元素描述", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "来自 browser_snapshot 输出的目标下拉元素 ref", required = true),
                                    ToolParameterSchema(name = "values", type = "array", description = "要选择的值或可见文本数组", required = true)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_snapshot",
                            description = "抓取当前页面的结构化无障碍风格快照，包括同源 iframe 内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "filename", type = "string", description = "可选，输出快照文件名", required = false),
                                    ToolParameterSchema(name = "selector", type = "string", description = "可选，局部快照的根元素选择器", required = false),
                                    ToolParameterSchema(name = "depth", type = "integer", description = "可选，快照树深度限制", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_take_screenshot",
                            description = "截取当前页面或特定元素的截图。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "type", type = "string", description = "可选，图片类型：png 或 jpeg", required = false, default = "png"),
                                    ToolParameterSchema(name = "filename", type = "string", description = "可选，输出文件名", required = false),
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，元素描述；提供时必须同时提供 ref", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "可选，元素 ref；提供时必须同时提供 element", required = false),
                                    ToolParameterSchema(name = "fullPage", type = "boolean", description = "可选，是否整页截图；元素截图时不可使用", required = false, default = "false")
                                )
                        ),
                        ToolPrompt(
                            name = "browser_type",
                            description = "向可编辑元素输入文本。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "element", type = "string", description = "可选，人类可读元素描述", required = false),
                                    ToolParameterSchema(name = "ref", type = "string", description = "来自 browser_snapshot 输出的目标元素 ref", required = true),
                                    ToolParameterSchema(name = "text", type = "string", description = "要输入的文本", required = true),
                                    ToolParameterSchema(name = "submit", type = "boolean", description = "可选，输入后是否按 Enter 提交", required = false, default = "false"),
                                    ToolParameterSchema(name = "slowly", type = "boolean", description = "可选，是否逐字符输入", required = false, default = "false")
                                )
                        ),
                        ToolPrompt(
                            name = "browser_wait_for",
                            description = "等待文本出现、消失，或等待指定时长。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "time", type = "number", description = "可选，等待秒数", required = false),
                                    ToolParameterSchema(name = "text", type = "string", description = "可选，等待出现的文本", required = false),
                                    ToolParameterSchema(name = "textGone", type = "string", description = "可选，等待消失的文本", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "browser_tabs",
                            description = "使用 0-based 索引列出、创建、切换或关闭浏览器 tab。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(name = "action", type = "string", description = "list/create/select/close 之一", required = true),
                                    ToolParameterSchema(name = "index", type = "integer", description = "可选，select 或 close 使用的 tab 索引", required = false)
                                )
                        ),
                        ToolPrompt(
                            name = "calculate",
                            description = "计算数学表达式。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "expression",
                                        type = "string",
                                        description = "数学表达式，例如 \"(1+2)*3\"",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "execute_intent",
                            description = "执行 Android Intent（activity/broadcast/service）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "可选，Intent action",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "可选，data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "可选，包名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "可选，\"package/class\" 格式",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "type",
                                        type = "string",
                                        description = "可选，activity/broadcast/service",
                                        required = false,
                                        default = "activity"
                                    ),
                                    ToolParameterSchema(
                                        name = "flags",
                                        type = "string",
                                        description = "可选，flag 整数数组 JSON 字符串（或单个整数）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "可选，extras 的 JSON 对象字符串",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_broadcast",
                            description = "发送广播 Intent。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "action",
                                        type = "string",
                                        description = "必填，广播 action",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "uri",
                                        type = "string",
                                        description = "可选，data URI",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "package",
                                        type = "string",
                                        description = "可选，包名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "component",
                                        type = "string",
                                        description = "可选，\"package/class\" 格式",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extras",
                                        type = "string",
                                        description = "可选，extras 的 JSON 对象字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_key",
                                        type = "string",
                                        description = "可选，单个字符串 extra 的 key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_value",
                                        type = "string",
                                        description = "可选，单个字符串 extra 的 value",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_key2",
                                        type = "string",
                                        description = "可选，第二个字符串 extra 的 key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "extra_value2",
                                        type = "string",
                                        description = "可选，第二个字符串 extra 的 value",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "device_info",
                            description = "获取设备信息。",
                            parametersStructured = listOf()
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "拓展记忆工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "create_memory",
                            description = "在记忆库中创建新的记忆节点。当你想保存重要信息供将来参考时使用。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "title", type = "string", description = "必需, 字符串", required = true),
                                ToolParameterSchema(name = "content", type = "string", description = "必需, 字符串", required = true),
                                ToolParameterSchema(name = "content_type", type = "string", description = "可选", required = false, default = "\"text/plain\""),
                                ToolParameterSchema(name = "source", type = "string", description = "可选", required = false, default = "\"ai_created\""),
                                ToolParameterSchema(name = "folder_path", type = "string", description = "可选", required = false, default = "\"\""),
                                ToolParameterSchema(name = "tags", type = "string", description = "可选, 逗号分隔的字符串", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "update_memory",
                            description = "通过标题更新现有的记忆节点。用于修改现有记忆的内容或元数据。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "old_title", type = "string", description = "必需, 字符串，用于识别记忆", required = true),
                                ToolParameterSchema(name = "new_title", type = "string", description = "可选, 字符串, 重命名时的新标题", required = false),
                                ToolParameterSchema(name = "content", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "content_type", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "source", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "credibility", type = "number", description = "可选, 浮点数 0-1", required = false),
                                ToolParameterSchema(name = "importance", type = "number", description = "可选, 浮点数 0-1", required = false),
                                ToolParameterSchema(name = "folder_path", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "tags", type = "string", description = "可选, 逗号分隔的字符串", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "delete_memory",
                            description = "通过标题从记忆库中删除记忆节点。谨慎使用，此操作不可逆。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "title", type = "string", description = "必需, 字符串，用于识别记忆", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "link_memories",
                            description = "在记忆库中的两个记忆之间创建语义链接。用于建立相关概念、事实或信息片段之间的关系。这有助于构建知识图谱结构，以便更好地检索和理解记忆。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source_title", type = "string", description = "必需, 字符串, 源记忆的标题", required = true),
                                ToolParameterSchema(name = "target_title", type = "string", description = "必需, 字符串, 目标记忆的标题", required = true),
                                ToolParameterSchema(name = "link_type", type = "string", description = "可选, 字符串, 关系类型，如\"related\"（相关）、\"causes\"（导致）、\"explains\"（解释）、\"part_of\"（部分）、\"contradicts\"（矛盾）等", required = false, default = "\"related\""),
                                ToolParameterSchema(name = "weight", type = "number", description = "可选, 浮点数 0.0-1.0, 链接强度，1.0表示最强", required = false, default = "0.7"),
                                ToolParameterSchema(name = "description", type = "string", description = "可选, 字符串, 关于关系的额外上下文", required = false, default = "\"\"")
                            )
                        ),
                        ToolPrompt(
                            name = "query_memory_links",
                            description = "查询记忆图谱中的链接。支持按 link_id、source_title、target_title、link_type 过滤。适合在更新/删除链接前先精确定位目标。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "link_id", type = "integer", description = "可选, 精确链接ID", required = false),
                                ToolParameterSchema(name = "source_title", type = "string", description = "可选, 源记忆精确标题", required = false),
                                ToolParameterSchema(name = "target_title", type = "string", description = "可选, 目标记忆精确标题", required = false),
                                ToolParameterSchema(name = "link_type", type = "string", description = "可选, 关系类型过滤", required = false),
                                ToolParameterSchema(name = "limit", type = "integer", description = "可选, 整数 1-200, 返回链接数量上限", required = false, default = "20")
                            )
                        ),
                        ToolPrompt(
                            name = "update_user_preferences",
                            description = "直接更新用户偏好信息。当你了解到用户的新信息时使用（例如生日、性别、性格特征、身份、职业或首选AI交互风格）。这允许立即更新而无需等待自动系统。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "birth_date", type = "integer", description = "可选, Unix时间戳，毫秒", required = false),
                                ToolParameterSchema(name = "gender", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "personality", type = "string", description = "可选, 描述性格特征的字符串", required = false),
                                ToolParameterSchema(name = "identity", type = "string", description = "可选, 描述身份/角色的字符串", required = false),
                                ToolParameterSchema(name = "occupation", type = "string", description = "可选, 字符串", required = false),
                                ToolParameterSchema(name = "ai_style", type = "string", description = "可选, 描述首选AI交互风格的字符串. 必须提供至少一个参数", required = false)
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "拓展 HTTP 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "http_request",
                            description = "发送HTTP请求。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                                ToolParameterSchema(name = "method", type = "string", description = "GET/POST/PUT/DELETE", required = true),
                                ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                                ToolParameterSchema(name = "body", type = "string", description = "body", required = false),
                                ToolParameterSchema(name = "body_type", type = "string", description = "json/form/text/xml", required = false),
                                ToolParameterSchema(name = "ignore_ssl", type = "boolean", description = "是否忽略HTTPS证书校验，true/false", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "multipart_request",
                            description = "上传文件。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                                ToolParameterSchema(name = "method", type = "string", description = "POST/PUT", required = true),
                                ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                                ToolParameterSchema(name = "form_data", type = "string", description = "form_data", required = false),
                                ToolParameterSchema(name = "files", type = "string", description = "JSON数组字符串。每个元素是对象: {\"field_name\": 字符串, \"file_path\": 字符串, 可选 \"content_type\": 字符串, 可选 \"file_name\": 字符串}", required = false),
                                ToolParameterSchema(name = "ignore_ssl", type = "boolean", description = "是否忽略HTTPS证书校验，true/false", required = false)
                            )
                        ),
                        ToolPrompt(
                            name = "manage_cookies",
                            description = "管理cookies。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "action", type = "string", description = "get/set/clear", required = true),
                                ToolParameterSchema(name = "domain", type = "string", description = "domain", required = false),
                                ToolParameterSchema(name = "cookies", type = "string", description = "cookies", required = false)
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "拓展文件工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "file_exists",
                            description = "检查文件或目录是否存在。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "目标路径", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "move_file",
                            description = "移动或重命名文件或目录。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "源路径", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "目标路径", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "copy_file",
                            description = "复制文件或目录。支持Android和Linux之间的跨环境复制。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "源路径", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "目标路径", required = true),
                                ToolParameterSchema(name = "recursive", type = "boolean", description = "布尔值", required = false, default = "false"),
                                ToolParameterSchema(name = "source_environment", type = "string", description = "可选，\"android\"或\"linux\"", required = false, default = "\"android\""),
                                ToolParameterSchema(name = "dest_environment", type = "string", description = "可选，\"android\"或\"linux\"。跨环境复制（如Android → Linux或Linux → Android）时，需指定source_environment和dest_environment", required = false, default = "\"android\"")
                            )
                        ),
                        ToolPrompt(
                            name = "file_info",
                            description = "获取文件或目录的详细信息，包括类型、大小、权限、所有者、组和最后修改时间。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "目标路径", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "zip_files",
                            description = "压缩文件或目录。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "要压缩的路径", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "输出zip文件", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "unzip_files",
                            description = "解压zip文件。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "source", type = "string", description = "zip文件路径", required = true),
                                ToolParameterSchema(name = "destination", type = "string", description = "解压路径", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "open_file",
                            description = "使用系统默认应用程序打开文件。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true)
                            )
                        ),
                        ToolPrompt(
                            name = "share_file",
                            description = "与其他应用程序共享文件。",
                            parametersStructured = listOf(
                                ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true),
                                ToolParameterSchema(name = "title", type = "string", description = "可选的共享标题", required = false, default = "\"Share File\"")
                            )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "Tasker 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "trigger_tasker_event",
                            description = "触发 Tasker 事件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "task_type",
                                        type = "string",
                                        description = "Tasker 事件类型",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "arg1",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg2",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg3",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg4",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "arg5",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "args_json",
                                        type = "string",
                                        description = "可选，JSON 对象字符串",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "工作流工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_all_workflows",
                            description = "获取所有工作流列表。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_workflow",
                            description = "创建工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "工作流名称",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "可选，节点 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "可选，连线 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_workflow",
                            description = "获取工作流详情。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_workflow",
                            description = "更新工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "nodes",
                                        type = "string",
                                        description = "可选，节点 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connections",
                                        type = "string",
                                        description = "可选，连线 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "可选",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "patch_workflow",
                            description = "差异更新工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "description",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "node_patches",
                                        type = "string",
                                        description = "可选，节点 patch JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "connection_patches",
                                        type = "string",
                                        description = "可选，连线 patch JSON 数组字符串",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "enable_workflow",
                            description = "启用工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "disable_workflow",
                            description = "禁用工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_workflow",
                            description = "删除工作流。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "trigger_workflow",
                            description = "触发工作流执行。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "workflow_id",
                                        type = "string",
                                        description = "工作流 ID",
                                        required = true
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "对话工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "start_chat_service",
                            description = "启动对话服务（悬浮窗）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "initial_mode",
                                        type = "string",
                                        description = "可选，初始悬浮模式：WINDOW, BALL, VOICE_BALL, FULLSCREEN, RESULT_DISPLAY, SCREEN_OCR",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "auto_enter_voice_chat",
                                        type = "boolean",
                                        description = "可选，为 true 时在打开 FULLSCREEN 时自动进入语音模式",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "wake_launched",
                                        type = "boolean",
                                        description = "可选，若由唤醒词启动则为 true，以便 UI 调整行为",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "可选，超时后自动关闭悬浮窗（毫秒），<=0 禁用自动关闭",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "keep_if_exists",
                                        type = "boolean",
                                        description = "可选，若服务已在运行则不强制切换悬浮窗模式",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_chat_service",
                            description = "停止对话服务（悬浮窗）。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_new_chat",
                            description = "创建新的对话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "group",
                                        type = "string",
                                        description = "新对话分组名（可选）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "set_as_current_chat",
                                        type = "boolean",
                                        description = "可选，是否切换到新对话（默认 true）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "character_card_id",
                                        type = "string",
                                        description = "可选，创建对话时绑定的角色卡 ID",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_chats",
                            description = "列出所有对话（支持筛选与排序）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "query",
                                        type = "string",
                                        description = "可选，标题关键字筛选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "match",
                                        type = "string",
                                        description = "可选，contains | exact | regex（默认 contains）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "可选，最多返回条数（默认 50）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sort_by",
                                        type = "string",
                                        description = "可选，updatedAt | createdAt | messageCount（默认 updatedAt）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sort_order",
                                        type = "string",
                                        description = "可选，asc | desc（默认 desc）",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "find_chat",
                            description = "按标题查找对话并返回其信息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "query",
                                        type = "string",
                                        description = "标题关键字/正则",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "match",
                                        type = "string",
                                        description = "可选，contains | exact | regex（默认 contains）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "可选，选择第 N 个匹配（默认 0）",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "agent_status",
                            description = "查询对话的输入处理状态。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "switch_chat",
                            description = "切换到指定对话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_chat_title",
                            description = "更新对话标题。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "title",
                                        type = "string",
                                        description = "新的对话标题",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_chat",
                            description = "按 ID 删除对话。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai",
                            description = "向 AI 发送消息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "消息内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "可选，目标对话 ID",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "role_card_id",
                                        type = "string",
                                        description = "可选，本次发送使用的角色卡 ID",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "sender_name",
                                        type = "string",
                                        description = "可选，当以用户身份发送时的显示名称",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_message_to_ai_advanced",
                            description = "向 AI 发送消息（高级参数）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "消息内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "可选，目标对话 ID",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "chat_history",
                                        type = "string",
                                        description = "可选，JSON 数组，元素为 [role, content]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "workspace_path",
                                        type = "string",
                                        description = "可选，工作区路径",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "可选，FunctionType 枚举名，默认 CHAT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "prompt_function_type",
                                        type = "string",
                                        description = "可选，PromptFunctionType 枚举名，默认 CHAT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_thinking",
                                        type = "boolean",
                                        description = "可选，是否启用思考模式",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "thinking_guidance",
                                        type = "boolean",
                                        description = "可选，是否启用思考引导",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_memory_query",
                                        type = "boolean",
                                        description = "可选，是否启用记忆查询",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "本次请求最大 token 预算",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "token_usage_threshold",
                                        type = "number",
                                        description = "token 使用阈值（0..1）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_system_prompt_template",
                                        type = "string",
                                        description = "可选，自定义系统提示模板",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "is_sub_task",
                                        type = "boolean",
                                        description = "可选，标记为子任务",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stream",
                                        type = "boolean",
                                        description = "可选，是否使用流式输出",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_character_cards",
                            description = "列出所有角色卡。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "get_chat_messages",
                            description = "读取指定对话的消息内容（跨话题读取）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "chat_id",
                                        type = "string",
                                        description = "目标对话 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "order",
                                        type = "string",
                                        description = "可选，asc/desc（默认 desc）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "可选，返回消息条数（默认20，最大200）",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部文件工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_file_full",
                            description = "读取完整文件内容（不限制大小）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "text_only",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "read_file_binary",
                            description = "读取二进制文件并返回 Base64 内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file",
                            description = "写入文件内容。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "content",
                                        type = "string",
                                        description = "文件内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "append",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_file_binary",
                            description = "将 Base64 内容写入二进制文件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "base64Content",
                                        type = "string",
                                        description = "Base64 编码内容",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "environment",
                                        type = "string",
                                        description = "可选，\"android\"（默认）或 \"linux\"",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部 UI 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "get_page_info",
                            description = "获取当前页面/窗口 UI 信息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "可选，xml/json",
                                        required = false,
                                        default = "xml"
                                    ),
                                    ToolParameterSchema(
                                        name = "detail",
                                        type = "string",
                                        description = "可选",
                                        required = false,
                                        default = "summary"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "tap",
                            description = "点击屏幕坐标。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "long_press",
                            description = "长按屏幕坐标。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "x",
                                        type = "integer",
                                        description = "x 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "y",
                                        type = "integer",
                                        description = "y 坐标",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "swipe",
                            description = "执行滑动手势。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "start_x",
                                        type = "integer",
                                        description = "起始 x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "start_y",
                                        type = "integer",
                                        description = "起始 y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_x",
                                        type = "integer",
                                        description = "结束 x",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "end_y",
                                        type = "integer",
                                        description = "结束 y",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "duration",
                                        type = "integer",
                                        description = "可选，持续时间（毫秒）",
                                        required = false,
                                        default = "300"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "click_element",
                            description = "点击 UI 元素（resourceId / className / contentDesc / bounds）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "resourceId",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "className",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "contentDesc",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bounds",
                                        type = "string",
                                        description = "可选，格式：[left,top][right,bottom]",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "partialMatch",
                                        type = "boolean",
                                        description = "可选，是否启用部分匹配",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "index",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "0"
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_input_text",
                            description = "设置输入框文本（可传空字符串以清空）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "要输入的文本",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "press_key",
                            description = "按下按键（keyevent）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key_code",
                                        type = "string",
                                        description = "按键码，例如 KEYCODE_HOME",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "display",
                                        type = "string",
                                        description = "可选，多屏 display id",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "capture_screenshot",
                            description = "截取屏幕截图并返回文件路径。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "run_ui_subagent",
                            description = "运行轻量 UI 自动化子代理。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "intent",
                                        type = "string",
                                        description = "任务描述",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "max_steps",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "20"
                                    ),
                                    ToolParameterSchema(
                                        name = "agent_id",
                                        type = "string",
                                        description = "可选，可复用的 agent 会话 ID。不传或传 'default' 时使用主屏幕；传入且不为 'default' 时表示请求使用对应的虚拟屏幕会话，虚拟屏幕必须处于可用状态，否则本次运行将失败。",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "target_app",
                                        type = "string",
                                        description = "可选，目标应用包名",
                                        required = false
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "软件设置工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "read_environment_variable",
                            description = "按 key 读取环境变量当前值。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key",
                                        type = "string",
                                        description = "环境变量名",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "write_environment_variable",
                            description = "按 key 写入环境变量；value 为空时清除该变量。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "key",
                                        type = "string",
                                        description = "环境变量名",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "可选，写入值；空值清除该变量",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_sandbox_packages",
                            description = "列出沙盒包（内置与外部）及当前启用状态和管理路径。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "set_sandbox_package_enabled",
                            description = "按 package_name 启用或停用沙盒包。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "沙盒包名称",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "enabled",
                                        type = "boolean",
                                        description = "true 启用，false 停用",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "restart_mcp_with_logs",
                            description = "重启 MCP 插件启动流程，并返回每个插件的启动日志。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout_ms",
                                        type = "integer",
                                        description = "可选，最大等待时长（毫秒）",
                                        required = false,
                                        default = "120000"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_speech_services_config",
                            description = "获取当前 TTS/STT 语音服务配置。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "set_speech_services_config",
                            description = "更新 TTS/STT 语音服务配置字段。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "tts_service_type",
                                        type = "string",
                                        description = "可选，SIMPLE_TTS/HTTP_TTS/OPENAI_WS_TTS/SILICONFLOW_TTS/MINIMAX_TTS/OPENAI_TTS",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_url_template",
                                        type = "string",
                                        description = "可选，TTS 端点 URL 模板",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_api_key",
                                        type = "string",
                                        description = "可选，TTS API Key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_headers",
                                        type = "string",
                                        description = "可选，TTS headers 的 JSON 对象字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_http_method",
                                        type = "string",
                                        description = "可选，GET/POST",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_request_body",
                                        type = "string",
                                        description = "可选，TTS POST body 模板",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_content_type",
                                        type = "string",
                                        description = "可选，TTS Content-Type",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_locale",
                                        type = "string",
                                        description = "可选，TTS 语言标签，例如 zh-CN 或 en-US",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_voice_id",
                                        type = "string",
                                        description = "可选，TTS 音色 ID",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_model_name",
                                        type = "string",
                                        description = "可选，TTS 模型名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_response_pipeline",
                                        type = "string",
                                        description = "可选，HTTP TTS 响应处理管线 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_cleaner_regexs",
                                        type = "string",
                                        description = "可选，TTS 清理正则列表 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_speech_rate",
                                        type = "number",
                                        description = "可选，TTS 语速",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "tts_pitch",
                                        type = "number",
                                        description = "可选，TTS 音调",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_service_type",
                                        type = "string",
                                        description = "可选，SHERPA_NCNN/OPENAI_STT/DEEPGRAM_STT",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_endpoint_url",
                                        type = "string",
                                        description = "可选，STT 端点 URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_api_key",
                                        type = "string",
                                        description = "可选，STT API Key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "stt_model_name",
                                        type = "string",
                                        description = "可选，STT 模型名",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "test_tts_playback",
                            description = "使用当前语音服务配置播放一次 TTS 测试文本。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "text",
                                        type = "string",
                                        description = "必填，要通过当前 TTS 服务播放的一次性文本",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "interrupt",
                                        type = "boolean",
                                        description = "可选，播放前是否先中断当前播报",
                                        required = false,
                                        default = "true"
                                    ),
                                    ToolParameterSchema(
                                        name = "speech_rate",
                                        type = "number",
                                        description = "可选，仅对本次测试生效的语速覆盖值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "pitch",
                                        type = "number",
                                        description = "可选，仅对本次测试生效的音调覆盖值",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_model_configs",
                            description = "列出全部模型配置及当前功能模型绑定关系。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "create_model_config",
                            description = "创建模型配置；可在创建时传入部分字段。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "可选，配置名称",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_provider_type",
                                        type = "string",
                                        description = "可选，提供商枚举名（如 OPENAI_GENERIC/OPENAI_RESPONSES_GENERIC/DEEPSEEK/GEMINI_GENERIC/OLLAMA/MNN/LLAMA_CPP）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_endpoint",
                                        type = "string",
                                        description = "可选，API 端点 URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_key",
                                        type = "string",
                                        description = "可选，API Key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "model_name",
                                        type = "string",
                                        description = "可选，模型名；多个模型可用逗号分隔",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 max_tokens 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "可选，max_tokens 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 temperature 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature",
                                        type = "number",
                                        description = "可选，temperature 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 top_p 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p",
                                        type = "number",
                                        description = "可选，top_p 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 top_k 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k",
                                        type = "integer",
                                        description = "可选，top_k 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 presence_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty",
                                        type = "number",
                                        description = "可选，presence_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 frequency_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty",
                                        type = "number",
                                        description = "可选，frequency_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 repetition_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty",
                                        type = "number",
                                        description = "可选，repetition_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "context_length",
                                        type = "number",
                                        description = "可选，基础上下文长度",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_context_length",
                                        type = "number",
                                        description = "可选，最大上下文长度",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_max_context_mode",
                                        type = "boolean",
                                        description = "可选，是否启用最大上下文模式（启用后使用 max_context_length）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_token_threshold",
                                        type = "number",
                                        description = "可选，上下文总结触发的 token 比例阈值（0~1）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary",
                                        type = "boolean",
                                        description = "可选，是否启用上下文总结",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary_by_message_count",
                                        type = "boolean",
                                        description = "可选，是否启用按消息条数触发总结",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_message_count_threshold",
                                        type = "integer",
                                        description = "可选，按消息条数触发总结的阈值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_parameters",
                                        type = "string",
                                        description = "可选，自定义参数 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_headers",
                                        type = "string",
                                        description = "可选，自定义请求头 JSON 对象字符串",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "update_model_config",
                            description = "按 config_id 更新模型配置字段。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "目标配置 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "name",
                                        type = "string",
                                        description = "可选，配置名称",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_provider_type",
                                        type = "string",
                                        description = "可选，提供商枚举名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_endpoint",
                                        type = "string",
                                        description = "可选，API 端点 URL",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "api_key",
                                        type = "string",
                                        description = "可选，API Key",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "model_name",
                                        type = "string",
                                        description = "可选，模型名；多个模型可用逗号分隔",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 max_tokens 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_tokens",
                                        type = "integer",
                                        description = "可选，max_tokens 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 temperature 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "temperature",
                                        type = "number",
                                        description = "可选，temperature 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 top_p 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_p",
                                        type = "number",
                                        description = "可选，top_p 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 top_k 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "top_k",
                                        type = "integer",
                                        description = "可选，top_k 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 presence_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "presence_penalty",
                                        type = "number",
                                        description = "可选，presence_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 frequency_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "frequency_penalty",
                                        type = "number",
                                        description = "可选，frequency_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty_enabled",
                                        type = "boolean",
                                        description = "可选，是否启用 repetition_penalty 参数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "repetition_penalty",
                                        type = "number",
                                        description = "可选，repetition_penalty 数值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "context_length",
                                        type = "number",
                                        description = "可选，基础上下文长度",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_context_length",
                                        type = "number",
                                        description = "可选，最大上下文长度",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_max_context_mode",
                                        type = "boolean",
                                        description = "可选，是否启用最大上下文模式（启用后使用 max_context_length）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_token_threshold",
                                        type = "number",
                                        description = "可选，上下文总结触发的 token 比例阈值（0~1）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary",
                                        type = "boolean",
                                        description = "可选，是否启用上下文总结",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_summary_by_message_count",
                                        type = "boolean",
                                        description = "可选，是否启用按消息条数触发总结",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "summary_message_count_threshold",
                                        type = "integer",
                                        description = "可选，按消息条数触发总结的阈值",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_parameters",
                                        type = "string",
                                        description = "可选，自定义参数 JSON 数组字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "custom_headers",
                                        type = "string",
                                        description = "可选，自定义请求头 JSON 对象字符串",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_image_processing",
                                        type = "boolean",
                                        description = "可选，是否开启直接图片处理",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_audio_processing",
                                        type = "boolean",
                                        description = "可选，是否开启直接音频处理",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_direct_video_processing",
                                        type = "boolean",
                                        description = "可选，是否开启直接视频处理",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_google_search",
                                        type = "boolean",
                                        description = "可选，Gemini 搜索增强开关",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "enable_tool_call",
                                        type = "boolean",
                                        description = "可选，是否开启模型原生 Tool Call",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "mnn_forward_type",
                                        type = "integer",
                                        description = "可选，MNN 前向类型",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "mnn_thread_count",
                                        type = "integer",
                                        description = "可选，MNN 线程数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_thread_count",
                                        type = "integer",
                                        description = "可选，llama.cpp 线程数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_context_size",
                                        type = "integer",
                                        description = "可选，llama.cpp 上下文大小",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "llama_gpu_layers",
                                        type = "integer",
                                        description = "可选，llama.cpp GPU 层数",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "request_limit_per_minute",
                                        type = "integer",
                                        description = "可选，每分钟请求限制（0 为不限）",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "max_concurrent_requests",
                                        type = "integer",
                                        description = "可选，最大并发请求数（0 为不限）",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "delete_model_config",
                            description = "按 config_id 删除模型配置（默认配置不可删除）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "目标配置 ID",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_function_model_configs",
                            description = "仅列出功能模型绑定关系（function_type -> config_id + model_index）。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "get_function_model_config",
                            description = "获取某个 function_type 当前绑定的单个模型配置。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "功能类型枚举名（CHAT/SUMMARY/MEMORY/UI_CONTROLLER/TRANSLATION/GREP/IMAGE_RECOGNITION/AUDIO_RECOGNITION/VIDEO_RECOGNITION）",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "set_function_model_config",
                            description = "将某个功能类型绑定到指定模型配置（可选 model_index）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "function_type",
                                        type = "string",
                                        description = "功能类型枚举名（CHAT/SUMMARY/MEMORY/UI_CONTROLLER/TRANSLATION/GREP/IMAGE_RECOGNITION/AUDIO_RECOGNITION/VIDEO_RECOGNITION）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "目标模型配置 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "model_index",
                                        type = "integer",
                                        description = "可选，当 model_name 为多模型时指定索引",
                                        required = false,
                                        default = "0"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "test_model_config_connection",
                            description = "按 config_id 执行与设置页一致的模型连接测试。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "config_id",
                                        type = "string",
                                        description = "目标模型配置 ID",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "model_index",
                                        type = "integer",
                                        description = "可选，当 model_name 为多模型时指定索引",
                                        required = false,
                                        default = "0"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "内部系统工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "close_all_virtual_displays",
                            description = "关闭所有虚拟屏幕。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "modify_system_setting",
                            description = "修改系统设置。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "设置项 key（别名：key）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "value",
                                        type = "string",
                                        description = "设置值",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "可选，system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_system_setting",
                            description = "获取系统设置。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "setting",
                                        type = "string",
                                        description = "设置项 key（别名：key）",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "namespace",
                                        type = "string",
                                        description = "可选，system/secure/global",
                                        required = false,
                                        default = "system"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "install_app",
                            description = "请求安装 APK（需要用户确认）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "path",
                                        type = "string",
                                        description = "APK 文件路径（别名：path）",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "uninstall_app",
                            description = "请求卸载应用（需要用户确认）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "list_installed_apps",
                            description = "列出已安装应用。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "start_app",
                            description = "启动应用。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "activity",
                                        type = "string",
                                        description = "可选，Activity 类名",
                                        required = false
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "stop_app",
                            description = "停止应用后台进程。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "应用包名",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_notifications",
                            description = "获取设备通知。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "可选",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_ongoing",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_app_usage_time",
                            description = "读取 Android 使用情况访问中的前台应用使用时长。若缺少权限，应先引导用户授予 Usage Access。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "package_name",
                                        type = "string",
                                        description = "可选，精确应用包名",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "since_hours",
                                        type = "integer",
                                        description = "可选，向前统计多少小时",
                                        required = false,
                                        default = "24"
                                    ),
                                    ToolParameterSchema(
                                        name = "limit",
                                        type = "integer",
                                        description = "可选，不传 package_name 时最多返回多少个应用",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_system_apps",
                                        type = "boolean",
                                        description = "可选，不传 package_name 时是否包含系统应用",
                                        required = false,
                                        default = "false"
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "toast",
                            description = "在设备上显示 Toast 提示。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "Toast 文本",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "send_notification",
                            description = "使用 AI 回复完成的通知通道发送通知。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "title",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "message",
                                        type = "string",
                                        description = "通知内容",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "get_device_location",
                            description = "获取设备位置信息。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "timeout",
                                        type = "integer",
                                        description = "可选，超时（秒）",
                                        required = false,
                                        default = "10"
                                    ),
                                    ToolParameterSchema(
                                        name = "high_accuracy",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "false"
                                    ),
                                    ToolParameterSchema(
                                        name = "include_address",
                                        type = "boolean",
                                        description = "可选",
                                        required = false,
                                        default = "true"
                                    )
                                )
                        )
                    )
            ),
            SystemToolPromptCategory(
                categoryName = "FFmpeg 工具",
                tools =
                    listOf(
                        ToolPrompt(
                            name = "ffmpeg_execute",
                            description = "执行 FFmpeg 命令（仅填写参数，不要包含前缀 ffmpeg）。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "command",
                                        type = "string",
                                        description = "仅填写 FFmpeg 命令参数，不要包含前缀 ffmpeg",
                                        required = true
                                    )
                                )
                        ),
                        ToolPrompt(
                            name = "ffmpeg_info",
                            description = "获取 FFmpeg 信息。",
                            parametersStructured = listOf()
                        ),
                        ToolPrompt(
                            name = "ffmpeg_convert",
                            description = "使用 FFmpeg 转换视频文件。",
                            parametersStructured =
                                listOf(
                                    ToolParameterSchema(
                                        name = "input_path",
                                        type = "string",
                                        description = "输入文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "output_path",
                                        type = "string",
                                        description = "输出文件路径",
                                        required = true
                                    ),
                                    ToolParameterSchema(
                                        name = "format",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "resolution",
                                        type = "string",
                                        description = "可选，例如 1280x720",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "bitrate",
                                        type = "string",
                                        description = "可选，例如 1000k",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "audio_codec",
                                        type = "string",
                                        description = "可选",
                                        required = false
                                    ),
                                    ToolParameterSchema(
                                        name = "video_codec",
                                        type = "string",
                                        description = "可选，H.264 编码请使用 h264",
                                        required = false
                                    )
                                )
                        )
                    )
            )
        )
}
