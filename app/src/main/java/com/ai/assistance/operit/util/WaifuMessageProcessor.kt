package com.ai.assistance.operit.util

import android.content.Context
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.stream.stream
import com.ai.assistance.operit.util.streamnative.nativeMarkdownSplitByBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Waifu模式消息处理器
 * 负责将AI回复按句号分割并模拟逐句发送
 */
object WaifuMessageProcessor {
    
    private var customEmojiRepository: CustomEmojiRepository? = null
    private var activePromptManager: ActivePromptManager? = null
    
    /**
     * 初始化处理器（需要在应用启动时调用）
     */
    fun initialize(appContext: Context) {
        customEmojiRepository = CustomEmojiRepository.getInstance(appContext)
        activePromptManager = ActivePromptManager.getInstance(appContext)
    }
    
    /**
     * 将完整的消息按句号分割成句子
     * @param content 完整的消息内容
     * @param removePunctuation 是否移除标点符号
     * @return 分割后的句子列表
     */
    fun splitMessageBySentences(content: String, removePunctuation: Boolean = false): List<String> {
        if (content.isBlank()) return emptyList()

        // 正则表达式，用于匹配Markdown的图片 ![]() 和链接 []()
        val markdownEntityRegex = Regex("""!?\[[^\]]*?\]\([^)]*?\)""")
        val entities = mutableListOf<String>()
        val placeholderPrefix = "{MDENTITY:"
        val placeholderSuffix = "}"

        // 1. 将Markdown实体替换为占位符，以保护它们不被错误分割
        var entityIndex = 0
        val contentWithPlaceholders = markdownEntityRegex.replace(content) {
            val placeholder = "$placeholderPrefix${entityIndex++}$placeholderSuffix"
            entities.add(it.value)
            placeholder
        }
        
        // 2. 首先分离表情包和文本内容（在处理占位符版本的内容上）
        val segments = splitIntoSegments(contentWithPlaceholders)

        val resultWithPlaceholders = mutableListOf<String>()

        for (segment in segments) {
            if (segment.isProtected) {
                val block = segment.content.trim('\n', '\r')
                if (block.isNotBlank()) {
                    resultWithPlaceholders.add(block)
                }
                continue
            }

            val contentWithoutThinking = ChatUtils.removeThinkingContent(segment.content)
            if (contentWithoutThinking.isBlank()) continue

            val separatedContent = separateEmotionAndText(contentWithoutThinking)

            for (item in separatedContent) {
                // 如果这个item是表情包（包含![开头的），直接添加
                if (item.startsWith("![")) {
                    resultWithPlaceholders.add(item)
                    continue
                }

                // 对于文本内容，进行正常的清理和分句处理
                val cleanedContent = cleanContentForWaifu(item)

                if (cleanedContent.isBlank()) continue

                val splitRegex =
                    Regex("(?<=[。！？~～])|(?<=[.!?]{1}(?![.]))|(?<=\\.{3})|(?<=[…](?![…]))")

                com.ai.assistance.operit.util.AppLogger.d(
                    "WaifuMessageProcessor",
                    "分割正则: $splitRegex"
                )
                com.ai.assistance.operit.util.AppLogger.d(
                    "WaifuMessageProcessor",
                    "待分割内容: '$cleanedContent'"
                )

                var sentences =
                    cleanedContent.split(splitRegex)
                        .filter { it.isNotBlank() }
                        .map { it.trim() }

                // 如果需要移除标点符号，则处理每个句子
                if (removePunctuation) {
                    sentences =
                        sentences
                            .map { sentence ->
                                // 移除句末标点，但保留省略号"..."
                                if (sentence.endsWith("...")) {
                                    sentence.trim()
                                } else {
                                    sentence.replace(Regex("[。！？.!?]+$"), "").trim()
                                }
                            }
                            .filter { it.isNotBlank() }
                }

                resultWithPlaceholders.addAll(sentences)
            }
        }
        
        // 3.5. 合并仅包含标点符号的句子到前一句
        val mergedResultWithPlaceholders = mutableListOf<String>()
        if (resultWithPlaceholders.isNotEmpty()) {
            mergedResultWithPlaceholders.add(resultWithPlaceholders[0])
            for (i in 1 until resultWithPlaceholders.size) {
                val currentSentence = resultWithPlaceholders[i]
                val trimmedSentence = currentSentence.trim()
                // 正则表达式匹配一个或多个结尾标点符号
                if (trimmedSentence.isNotEmpty() && trimmedSentence.matches(Regex("^[。！？~～.!?…]+$"))) {
                    val lastIndex = mergedResultWithPlaceholders.size - 1
                    val lastSentence = mergedResultWithPlaceholders[lastIndex]
                    if (!lastSentence.contains('\n') && !lastSentence.contains('\r')) {
                        mergedResultWithPlaceholders[lastIndex] = lastSentence + currentSentence
                    } else {
                        mergedResultWithPlaceholders.add(currentSentence)
                    }
                } else {
                    mergedResultWithPlaceholders.add(currentSentence)
                }
            }
        }
        
        // 4. 将占位符恢复为原始的Markdown实体
        val finalResult = mergedResultWithPlaceholders.map { sentence ->
            var currentSentence = sentence
            val placeholderRegex =
                Regex(
                    "${Regex.escape(placeholderPrefix)}(\\d+)${Regex.escape(placeholderSuffix)}"
                )
            
            // 循环替换，以处理一个句子中可能存在的多个占位符
            while (placeholderRegex.containsMatchIn(currentSentence)) {
                currentSentence = placeholderRegex.replace(currentSentence) { matchResult ->
                    val index = matchResult.groupValues[1].toInt()
                    
                    if (index < entities.size) {
                        entities[index]
                    } else {
                        matchResult.value // 理论上不会发生，作为安全回退
                    }
                }
            }
            currentSentence
        }

        com.ai.assistance.operit.util.AppLogger.d("WaifuMessageProcessor", "分割出${finalResult.size}个结果")
        
        return finalResult
    }
    
    /**
     * 清理内容中的状态标签和XML标签，只保留纯文本
     */
    fun cleanContentForWaifu(content: String): String {
        val sanitizedContent =
            ChatUtils.removeThinkingContent(
                ChatUtils.stripGeminiThoughtSignatureMeta(content)
            )

        return sanitizedContent
            // 移除状态标签
            .replace(ChatMarkupRegex.statusTag, "")
            .replace(ChatMarkupRegex.statusSelfClosingTag, "")
            // 移除工具标签
            .replace(ChatMarkupRegex.toolTag, "")
            .replace(ChatMarkupRegex.toolSelfClosingTag, "")
            // 移除工具结果标签
            .replace(ChatMarkupRegex.toolResultTag, "")
            .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
            // 移除emotion标签（因为已经在processEmotionTags中处理过了）
            .replace(ChatMarkupRegex.emotionTag, "")
            
            // --- 新增：移除Markdown相关标记 ---
            // 1. 移除图片和链接，保留替代文本或链接文本
            .replace(Regex("!?\\[(.*?)\\]\\(.*?\\)"), "$1")
            // 2. 移除标题标记
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            // 3. 移除引用标记
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            // 4. 移除列表标记
            .replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // 5. 移除代码块标记
            .replace(Regex("```[a-zA-Z]*\\n?|\\n?```"), "")
            // 6. 移除加粗、斜体、删除线 (注意顺序和互斥)
            .replace(Regex("(\\*\\*\\*|___)(.+?)\\1"), "$2") // 加粗斜体
            .replace(Regex("(\\*\\*|__(?!MD_ENTITY__))(.+?)\\1"), "$2") // 加粗 (避免匹配占位符)
            .replace(Regex("(\\*|_)(.+?)\\1"), "$2")        // 斜体
            .replace(Regex("~~(.+?)~~"), "$1")              // 删除线
            // 7. 移除行内代码
            .replace(Regex("`(.+?)`"), "$1")
            // 8. 移除水平线
            .replace(Regex("^[-_*]{3,}\\s*$", RegexOption.MULTILINE), "")
            // --- Markdown移除结束 ---
            
            // 移除其他常见的XML标签
            .replace(ChatMarkupRegex.anyXmlTag, "")
            // 清理多余的空白
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    

    
    /**
     * 根据字符数计算句子延迟时间
     * @param characterCount 字符数
     * @param baseDelayMs 基础延迟（毫秒/字符）
     * @return 计算后的延迟时间（毫秒）
     */
    fun calculateSentenceDelay(characterCount: Int, baseDelayMs: Long): Long {
        // 基础计算：字符数 * 基础延迟
        val baseDelay = characterCount * baseDelayMs
        
        // 添加一些变化和限制：
        // 1. 短句子（<5字符）最少延迟300ms
        // 2. 长句子（>20字符）有上限3000ms
        // 3. 添加一些随机变化（±20%）使延迟更自然
        
        val minDelay = 300L
        val maxDelay = 3000L
        
        val adjustedDelay = when {
            characterCount <= 5 -> minDelay
            baseDelay > maxDelay -> maxDelay
            else -> baseDelay
        }
        
        // 添加±20%的随机变化
        val variance = (adjustedDelay * 0.2).toLong()
        val randomAdjustment = (-variance..variance).random()
        
        return (adjustedDelay + randomAdjustment).coerceAtLeast(minDelay)
    }
    
    private data class Segment(val content: String, val isProtected: Boolean)

    private fun splitIntoSegments(content: String): List<Segment> {
        if (content.isEmpty()) return listOf(Segment(content = "", isProtected = false))

        val segments = mutableListOf<Segment>()

        runBlocking {
            content.stream()
                .nativeMarkdownSplitByBlock()
                .collect { blockGroup ->
                    val blockType = blockGroup.tag ?: MarkdownProcessorType.PLAIN_TEXT
                    val sb = StringBuilder()
                    blockGroup.stream.collect { sb.append(it) }
                    val block = sb.toString()
                    if (block.isEmpty()) return@collect

                    val isProtected =
                        when (blockType) {
                            MarkdownProcessorType.CODE_BLOCK,
                            MarkdownProcessorType.TABLE -> true
                            else -> false
                        }

                    segments.add(Segment(content = block, isProtected = isProtected))
                }
        }

        return segments
    }
    
    /**
     * 检查内容是否适合进行分句处理
     * @param content 消息内容
     * @return 是否适合分句
     */
    fun shouldSplitMessage(content: String): Boolean {
        if (content.isBlank()) return false

        val segments = splitIntoSegments(content)
        val protectedCount = segments.count { it.isProtected && it.content.isNotBlank() }
        if (protectedCount > 0) {
            val hasUnprotected = segments.any { !it.isProtected && it.content.isNotBlank() }
            if (protectedCount > 1 || hasUnprotected) return true
            return false
        }
        
        // 检查是否包含表情包标签
        val hasEmotionTags = content.contains(Regex("<emotion[^>]*>.*?</emotion>"))
        
        // 首先清理内容
        val cleanedContent = cleanContentForWaifu(content)
        if (cleanedContent.isBlank()) return false
        
        // 检查是否包含句号、问号、感叹号、波浪号或省略号（与splitMessageBySentences保持一致）
        val hasSentenceEnders = cleanedContent.contains(Regex("[。！？.!?~～…]|\\Q...\\E"))
        
        // 检查内容长度是否足够长（至少10个字符）
        val isLongEnough = cleanedContent.length >= 10
        
        // 检查是否包含多个句子 (这里不考虑标点符号移除，因为是判断是否需要分句)
        val sentences = splitMessageBySentences(content, removePunctuation = false) // 这里传入原始内容，因为splitMessageBySentences内部会清理
        val hasMultipleSentences = sentences.size > 1
        
        // 如果有表情包标签，或者满足其他条件，就进行分句处理
        val shouldSplit = hasEmotionTags || (hasSentenceEnders && isLongEnough && hasMultipleSentences)
        
        // 添加调试日志
        com.ai.assistance.operit.util.AppLogger.d("WaifuMessageProcessor", 
            "shouldSplitMessage - 包含表情包: $hasEmotionTags, 句子数: ${sentences.size}, 结果: $shouldSplit")
        
        return shouldSplit
    }
    
    /**
     * 处理表情包标签，将<emotion>标签替换为对应的表情图片
     * @param content 包含emotion标签的内容
     * @return 处理后的内容，emotion标签被替换为表情图片
     */
    fun processEmotionTags(content: String): String {
        if (content.isBlank()) return content
        
        // 匹配<emotion>标签的正则表达式
        val emotionRegex = Regex("<emotion>([^<]+)</emotion>")
        
        return emotionRegex.replace(content) { matchResult ->
            val emotion = matchResult.groupValues[1].trim()
            val emojiPath = getRandomEmojiPath(emotion)
            
            if (emojiPath != null) {
                // 判断是自定义表情（绝对路径）还是assets表情（相对路径）
                val imageUrl = if (emojiPath.startsWith("/")) {
                    // 自定义表情：使用绝对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file://$encodedPath"
                } else {
                    // assets表情：使用相对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file:///android_asset/emoji/$encodedPath"
                }
                "![$emotion]($imageUrl)"
            } else {
                // 如果找不到对应的表情，返回原始文本
                matchResult.value
            }
        }
    }
    
    /**
     * 分离表情包和文本内容
     * @param content 包含emotion标签的内容
     * @return 包含文本内容和表情包内容的列表，表情包会单独作为一个元素
     */
    fun separateEmotionAndText(content: String): List<String> {
        if (content.isBlank()) return listOf(content)
        
        val result = mutableListOf<String>()
        val emotionRegex = Regex("<emotion>([^<]+)</emotion>")
        
        // 找到所有emotion标签的位置
        val matches = emotionRegex.findAll(content)
        var lastEnd = 0
        
        for (match in matches) {
            // 添加emotion标签之前的文本（如果有的话）
            val beforeText = content.substring(lastEnd, match.range.first).trim()
            if (beforeText.isNotEmpty()) {
                result.add(beforeText)
            }
            
            // 处理emotion标签
            val emotion = match.groupValues[1].trim()
            val emojiPath = getRandomEmojiPath(emotion)
            
            if (emojiPath != null) {
                // 判断是自定义表情（绝对路径）还是assets表情（相对路径）
                val imageUrl = if (emojiPath.startsWith("/")) {
                    // 自定义表情：使用绝对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file://$encodedPath"
                } else {
                    // assets表情：使用相对路径
                    val encodedPath = emojiPath.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
                    "file:///android_asset/emoji/$encodedPath"
                }
                result.add("![$emotion]($imageUrl)")
            }
            
            lastEnd = match.range.last + 1
        }
        
        // 添加最后一个emotion标签之后的文本（如果有的话）
        val afterText = content.substring(lastEnd).trim()
        if (afterText.isNotEmpty()) {
            result.add(afterText)
        }
        
        // 如果没有找到任何emotion标签，返回原始内容
        if (result.isEmpty()) {
            result.add(content)
        }
        
        com.ai.assistance.operit.util.AppLogger.d("WaifuMessageProcessor", "分离表情包和文本: ${result.size}个元素")
        return result
    }
    
    /**
     * 根据情绪名称获取随机的表情图片路径
     * @param emotion 情绪名称（如：happy、sad、miss_you等）
     * @return 表情图片的完整路径（file:// 格式），如果找不到则返回null
     */
    private fun getRandomEmojiPath(emotion: String): String? {
        try {
            // 只从自定义表情中查找
            val customEmoji = try {
                customEmojiRepository?.let { repo ->
                    runBlocking {
                        val activePrompt = activePromptManager?.getActivePrompt() ?: return@runBlocking null
                        repo.initializeBuiltinEmojis(activePrompt)
                        val emojis = repo.getEmojisForCategory(activePrompt, emotion).first()
                        if (emojis.isNotEmpty()) {
                            val randomEmoji = emojis.random()
                            val file = repo.getEmojiFile(activePrompt, randomEmoji)
                            if (file.exists()) {
                                com.ai.assistance.operit.util.AppLogger.d("WaifuMessageProcessor", "使用自定义表情: ${file.absolutePath}")
                                return@runBlocking file.absolutePath
                            }
                        }
                        null
                    }
                }
            } catch (e: Exception) {
                com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "查询自定义表情失败", e)
                null
            }
            
            // 如果找到自定义表情，直接返回（已经是完整路径）
            if (customEmoji != null) {
                return customEmoji
            }
            
            // 如果自定义表情中没有找到，则直接返回null
            com.ai.assistance.operit.util.AppLogger.w("WaifuMessageProcessor", "在自定义表情中未找到对于情绪 '$emotion' 的表情")
            return null
            
        } catch (e: Exception) {
            com.ai.assistance.operit.util.AppLogger.e("WaifuMessageProcessor", "获取表情图片失败: $emotion", e)
            return null
        }
    }
} 
