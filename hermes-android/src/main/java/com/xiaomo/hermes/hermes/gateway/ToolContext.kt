package com.xiaomo.hermes.hermes.gateway

/**
 * ToolContext - 对齐 hermes-agent/environments/tool_context.py
 * 提供终端、文件、搜索、浏览器等工具操作的上下文
 */

class ToolContext(
    val task_id: String
) {
    fun terminal(command: String, timeout: Long): Unit {
    // Hermes: terminal
        // Hermes: terminal
    }
    fun readFile(path: String): Unit {
    // Hermes: read_file
        // Hermes: readFile
    }
    fun writeFile(path: String, content: String): Unit {
    // Hermes: write_file
        // Hermes: writeFile
    }
    fun uploadFile(local_path: String, remote_path: String): Unit {
    // Hermes: upload_file
        // Hermes: uploadFile
    }
    fun uploadDir(local_dir: String, remote_dir: String): Unit {
    // Hermes: upload_dir
        // Hermes: uploadDir
    }
    fun downloadFile(remote_path: String, local_path: String): Unit {
    // Hermes: download_file
        // Hermes: downloadFile
    }
    fun downloadDir(remote_dir: String, local_dir: String): Unit {
    // Hermes: download_dir
        // Hermes: downloadDir
    }
    fun search(query: String, path: String): Unit {
    // Hermes: search
        // Hermes: search
    }
    fun webSearch(query: String): Unit {
    // Hermes: web_search
        // Hermes: webSearch
    }
    fun webExtract(urls: String): Unit {
    // Hermes: web_extract
        // Hermes: webExtract
    }
    fun browserNavigate(url: String): Unit {
    // Hermes: browser_navigate
        // Hermes: browserNavigate
    }
    fun browserSnapshot(): Unit {
    // Hermes: browser_snapshot
        // Hermes: browserSnapshot
    }
    fun callTool(tool_name: String, arguments: String): Unit {
    // Hermes: call_tool
        // Hermes: callTool
    }
    fun cleanup(): Unit {
    // Hermes: cleanup
        // Hermes: cleanup
    }
}
