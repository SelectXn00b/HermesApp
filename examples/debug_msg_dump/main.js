"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
exports.onPromptHistory = onPromptHistory;
exports.onPromptFinalize = onPromptFinalize;

var DUMP_DIR = "/sdcard/Download/Operit/debug_msg_dump/dumps/";

function registerToolPkg() {
    ToolPkg.registerPromptHistoryHook({
        id: "debug_dump_history",
        function: onPromptHistory,
    });
    ToolPkg.registerPromptFinalizeHook({
        id: "debug_dump_finalize",
        function: onPromptFinalize,
    });
    // 确保输出目录存在
    try { Tools.Files.mkdir(DUMP_DIR, true); } catch(e) {}
    return true;
}

function buildDump(tag, input) {
    var payload = input.eventPayload || {};
    var stage = String(payload.stage || input.eventName || "unknown");
    var history = payload.preparedHistory || payload.chatHistory || [];
    var systemPrompt = payload.systemPrompt || "";
    var toolPrompt = payload.toolPrompt || "";

    var lines = [];
    lines.push("========================================");
    lines.push("  DEBUG MSG DUMP - " + tag);
    lines.push("  Time: " + new Date().toLocaleString());
    lines.push("  Stage: " + stage);
    lines.push("  Event: " + input.eventName);
    lines.push("========================================");
    lines.push("");

    // System Prompt - 完整输出
    lines.push("╔══════════════════════════════════════╗");
    lines.push("║         SYSTEM PROMPT                ║");
    lines.push("╚══════════════════════════════════════╝");
    lines.push("Length: " + systemPrompt.length + " chars");
    lines.push("");
    lines.push(systemPrompt);
    lines.push("");

    // Tool Prompt - 完整输出
    lines.push("╔══════════════════════════════════════╗");
    lines.push("║         TOOL PROMPT                  ║");
    lines.push("╚══════════════════════════════════════╝");
    lines.push("Length: " + toolPrompt.length + " chars");
    lines.push("");
    lines.push(toolPrompt);
    lines.push("");

    // Messages - 每条完整输出，不截断
    lines.push("╔══════════════════════════════════════╗");
    lines.push("║    MESSAGES (Total: " + history.length + ")");
    lines.push("╚══════════════════════════════════════╝");
    lines.push("");

    for (var i = 0; i < history.length; i++) {
        var msg = history[i];
        var kind = msg.kind || "N/A";
        var content = msg.content || "";
        lines.push("┌─── Message #" + (i + 1) + " ───┐");
        lines.push("│ Kind: " + kind);
        if (msg.toolName) lines.push("│ ToolName: " + msg.toolName);
        if (msg.metadata) {
            try {
                lines.push("│ Metadata: " + JSON.stringify(msg.metadata));
            } catch(e2) {
                lines.push("│ Metadata: [stringify error]");
            }
        }
        lines.push("│ Content Length: " + content.length + " chars");
        lines.push("└──────────────────┘");
        lines.push(content);
        lines.push("");
    }

    lines.push("========== END OF DUMP ==========");
    return lines.join("\n");
}

function onPromptHistory(input) {
    var stage = String(input.eventPayload.stage || input.eventName || "");
    if (stage !== "after_prepare_history") return null;

    try {
        var text = buildDump("HISTORY (after B pack processing)", input);
        var ts = new Date().toISOString().replace(/[:.]\/g, "-");
        var path = DUMP_DIR + "history_" + ts + ".txt";
        Tools.Files.write(path, text);
        console.log("[msg_dump] history saved to " + path);
    } catch(e) {
        console.log("[msg_dump] history write error: " + e.message);
    }

    return null;
}

function onPromptFinalize(input) {
    try {
        var text = buildDump("FINALIZE (final to model)", input);
        var ts = new Date().toISOString().replace(/[:.]\/g, "-");
        var path = DUMP_DIR + "finalize_" + ts + ".txt";
        Tools.Files.write(path, text);
        console.log("[msg_dump] finalize saved to " + path);
    } catch(e) {
        console.log("[msg_dump] finalize write error: " + e.message);
    }

    return null;
}