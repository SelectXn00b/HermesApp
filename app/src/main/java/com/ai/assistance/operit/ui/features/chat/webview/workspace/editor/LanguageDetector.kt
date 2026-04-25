package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

/**
 * 根据文件名检测编程语言
 */
object LanguageDetector {
    /**
     * 根据文件名获取编程语言
     * @param fileName 文件名
     * @return 编程语言标识符
     */
    fun detectLanguage(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
        return when (extension) {
            // Kotlin
            "kt", "kts" -> "kotlin"
            
            // Java
            "java" -> "java"
            
            // JavaScript
            "js", "mjs" -> "javascript"
            
            // TypeScript
            "ts", "tsx" -> "typescript"
            
            // HTML
            "html", "htm", "xhtml" -> "html"
            
            // CSS
            "css", "scss", "sass", "less" -> "css"
            
            // XML
            "xml", "svg", "xsd", "xsl" -> "xml"
            
            // JSON
            "json" -> "json"
            
            // Markdown
            "md", "markdown" -> "markdown"
            
            // Python
            "py", "pyw", "pyc" -> "python"
            
            // C/C++
            "c", "cpp", "cc", "h", "hpp" -> "cpp"
            
            // C#
            "cs" -> "csharp"
            
            // PHP
            "php", "phtml" -> "php"
            
            // Ruby
            "rb", "rbw" -> "ruby"
            
            // Go
            "go" -> "go"
            
            // Rust
            "rs" -> "rust"
            
            // Swift
            "swift" -> "swift"
            
            // Dart
            "dart" -> "dart"
            
            // Shell
            "sh", "bash", "zsh" -> "shell"
            
            // SQL
            "sql" -> "sql"
            
            // YAML
            "yml", "yaml" -> "yaml"
            
            // 其他
            else -> "text"
        }
    }
} 