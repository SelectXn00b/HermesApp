/*
METADATA
{
    "name": "DebugMessageDump",
    "description": {
        "zh": "调试工具：转储当前对话的所有消息到文件，用于检查 AI 实际收到了什么",
        "en": "Debug tool: dump all messages in current chat to a file"
    },
    "tools": [
        {
            "name": "dump_current_chat",
            "description": {
                "zh": "将当前对话的所有消息保存到文件，方便查看 AI 收到了什么内容",
                "en": "Save all messages in current chat to a file for inspection"
            },
            "parameters": [
                {
                    "name": "limit",
                    "description": {
                        "zh": "最多读取多少条消息，默认100",
                        "en": "Max messages to read, default 100"
                    },
                    "type": "number",
                    "required": false
                }
            ]
        }
    ]
}
*/

const DebugMessageDump = (function () {
    async function wrap(func, params) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error) {
            complete({ success: false, message: "Error: " + error.message });
        }
    }

    async function dump_current_chat(params) {
        const limit = params.limit || 100;
        
        // 获取当前对话列表，找到当前对话
        const chatList = await Tools.Chat.listAll();
        if (!chatList || !chatList.chats || chatList.chats.length === 0) {
            return { success: false, message: "No chats found" };
        }
        
        // 取第一个（最近的）对话
        const currentChat = chatList.chats[0];
        const chatId = currentChat.id || currentChat.chatId;
        
        // 读取消息
        const messagesResult = await Tools.Chat.getMessages(chatId, { order: "asc", limit: limit });
        if (!messagesResult || !messagesResult.messages) {
            return { success: false, message: "Failed to get messages" };
        }
        
        const messages = messagesResult.messages;
        
        var lines = [];
        lines.push("=== CHAT MESSAGE DUMP ===");
        lines.push("Time: " + new Date().toLocaleString());
        lines.push("Chat ID: " + chatId);
        lines.push("Chat Title: " + (currentChat.title || "N/A"));
        lines.push("Total messages: " + messages.length);
        lines.push("");
        
        for (var i = 0; i < messages.length; i++) {
            var msg = messages[i];
            lines.push("--- Message #" + (i + 1) + " ---");
            lines.push("Kind/Role: " + (msg.kind || msg.role || "N/A"));
            lines.push("Sender: " + (msg.sender || msg.senderName || "N/A"));
            if (msg.toolName) lines.push("ToolName: " + msg.toolName);
            if (msg.timestamp) lines.push("Timestamp: " + msg.timestamp);
            
            var content = msg.content || msg.text || "";
            if (content.length > 2000) {
                lines.push("Content (" + content.length + " chars, showing first 2000):");
                lines.push(content.substring(0, 2000) + "...[TRUNCATED]");
            } else {
                lines.push("Content (" + content.length + " chars):");
                lines.push(content);
            }
            
            // 打印所有字段名
            var keys = Object.keys(msg);
            lines.push("All fields: " + keys.join(", "));
            lines.push("");
        }
        
        var timestamp = new Date().toISOString().replace(/[:.]\/g, "-");
        var filePath = "/sdcard/Download/Operit/debug_msg_dump/chat_dump_" + timestamp + ".txt";
        
        await Tools.Files.writeFile(filePath, lines.join("\n"));
        
        return { 
            success: true, 
            message: "Dumped " + messages.length + " messages to " + filePath,
            filePath: filePath,
            messageCount: messages.length
        };
    }

    return {
        dump_current_chat: (params) => wrap(dump_current_chat, params),
    };
})();

exports.dump_current_chat = DebugMessageDump.dump_current_chat;