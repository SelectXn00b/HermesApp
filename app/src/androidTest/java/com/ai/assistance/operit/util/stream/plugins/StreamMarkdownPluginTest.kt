package com.ai.assistance.operit.util.stream.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.stream.asCharStream
import com.ai.assistance.operit.util.stream.splitBy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamMarkdownPluginTest {

    // --- 测试基本的 Markdown 粗体插件 ---
    @Test
    fun testBoldPlugin() = runBlocking {
        val boldText = "这是一段**粗体文字**普通文字"
        val stream = boldText.asCharStream()
        val plugin = StreamMarkdownBoldPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一段", groups[0].content)

        // 第二组：粗体文本
        assertSame(plugin, groups[1].tag)
        assertEquals("**粗体文字**", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("普通文字", groups[2].content)
    }

    // --- 测试基本的 Markdown 斜体插件 ---
    @Test
    fun testItalicPlugin() = runBlocking {
        val italicText = "普通文字*斜体内容*后面的文字"
        val stream = italicText.asCharStream()
        val plugin = StreamMarkdownItalicPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("普通文字", groups[0].content)

        // 第二组：斜体文本
        assertSame(plugin, groups[1].tag)
        assertEquals("*斜体内容*", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("后面的文字", groups[2].content)
    }

    // --- 测试行内代码插件 ---
    @Test
    fun testInlineCodePlugin() = runBlocking {
        val codeText = "这是`行内代码`示例"
        val stream = codeText.asCharStream()
        val plugin = StreamMarkdownInlineCodePlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)
        assertNull(groups[0].tag)
        assertEquals("这是", groups[0].content)

        assertSame(plugin, groups[1].tag)
        assertEquals("`行内代码`", groups[1].content)

        assertNull(groups[2].tag)
        assertEquals("示例", groups[2].content)
    }

    // --- 测试代码块插件 ---
    @Test
    fun testFencedCodeBlockPlugin() = runBlocking {
        val codeBlock =
                """
            前置文本
            ```
            代码块内容
            多行代码
            ```
            后置文本
        """.trimIndent()

        val stream = codeBlock.asCharStream()
        val plugin = StreamMarkdownFencedCodeBlockPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：前置文本
        assertNull(groups[0].tag)
        assertTrue(groups[0].content.contains("前置文本"))

        // 第二组：代码块
        assertSame(plugin, groups[1].tag)
        assertTrue(groups[1].content.contains("```"))
        assertTrue(groups[1].content.contains("代码块内容"))
        assertTrue(groups[1].content.contains("多行代码"))

        // 第三组：后置文本
        assertNull(groups[2].tag)
        assertTrue(groups[2].content.contains("后置文本"))
    }

    // --- 测试标题插件 ---
    @Test
    fun testHeaderPlugin() = runBlocking {
        val headerText =
                """
            # 一级标题
            普通文字
            ## 二级标题
            更多内容
        """.trimIndent()

        val stream = headerText.asCharStream()
        val plugin = StreamMarkdownHeaderPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(4, groups.size)

        // 第一组：一级标题
        assertSame(plugin, groups[0].tag)
        assertTrue(groups[0].content.contains("# 一级标题"))

        // 第二组：普通文字
        assertNull(groups[1].tag)
        assertEquals("普通文字\n", groups[1].content)

        // 第三组：二级标题
        assertSame(plugin, groups[2].tag)
        assertTrue(groups[2].content.contains("## 二级标题"))

        // 第四组：后续内容
        assertNull(groups[3].tag)
        assertEquals("更多内容", groups[3].content)
    }

    // --- 测试链接插件 ---
    @Test
    fun testLinkPlugin() = runBlocking {
        val linkText = "这是一个[链接文本](https://example.com)示例"
        val stream = linkText.asCharStream()
        val plugin = StreamMarkdownLinkPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一个", groups[0].content)

        // 第二组：链接
        assertSame(plugin, groups[1].tag)
        assertEquals("[链接文本](https://example.com)", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("示例", groups[2].content)
    }

    // --- 测试图片插件 ---
    @Test
    fun testImagePlugin() = runBlocking {
        val imageText = "这是一张图片![图片描述](image.jpg)示例"
        val stream = imageText.asCharStream()
        val plugin = StreamMarkdownImagePlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一张图片", groups[0].content)

        // 第二组：图片
        assertSame(plugin, groups[1].tag)
        assertEquals("![图片描述](image.jpg)", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("示例", groups[2].content)
    }

    // --- 测试引用块插件 ---
    @Test
    fun testBlockQuotePlugin() = runBlocking {
        val quoteText =
                """
            前置文本
            > 这是一段引用文字
            > 这是引用的第二行
            普通文字
        """.trimIndent()

        val stream = quoteText.asCharStream()
        val plugin = StreamMarkdownBlockQuotePlugin()

        val groups = collectGroups(stream, plugin)

        assertTrue("应至少包含3个组", groups.size >= 3)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertTrue(groups[0].content.contains("前置文本"))

        // 引用块（可能分为多个组）
        var foundQuote = false
        for (i in 1 until groups.size - 1) {
            if (groups[i].tag === plugin) {
                foundQuote = true
                assertTrue(groups[i].content.contains("> 这是"))
            }
        }
        assertTrue("应找到引用块", foundQuote)

        // 最后一组：普通文本
        assertNull(groups.last().tag)
        assertTrue(groups.last().content.contains("普通文字"))
    }

    // --- 测试水平线插件 ---
    @Test
    fun testHorizontalRulePlugin() = runBlocking {
        val hrText = """
            前置文本
            ---
            后置文本
        """.trimIndent()

        val stream = hrText.asCharStream()
        val plugin = StreamMarkdownHorizontalRulePlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("前置文本\n", groups[0].content)

        // 第二组：水平线
        assertSame(plugin, groups[1].tag)
        assertEquals("---\n", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("后置文本", groups[2].content)

        // 测试其他水平线格式
        val otherHrText = """
            ***
            ___
        """.trimIndent()

        val otherStream = otherHrText.asCharStream()
        val otherGroups = collectGroups(otherStream, plugin)

        assertTrue("应至少找到两个水平线", otherGroups.count { it.tag === plugin } >= 2)
    }

    // --- 测试删除线插件 ---
    @Test
    fun testStrikethroughPlugin() = runBlocking {
        val strikeText = "这是~~删除线~~文本"
        val stream = strikeText.asCharStream()
        val plugin = StreamMarkdownStrikethroughPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是", groups[0].content)

        // 第二组：删除线
        assertSame(plugin, groups[1].tag)
        assertEquals("~~删除线~~", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("文本", groups[2].content)
    }

    // --- 测试下划线插件 ---
    @Test
    fun testUnderlinePlugin() = runBlocking {
        val underlineText = "这是__下划线__文本"
        val stream = underlineText.asCharStream()
        val plugin = StreamMarkdownUnderlinePlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是", groups[0].content)

        // 第二组：下划线
        assertSame(plugin, groups[1].tag)
        assertEquals("__下划线__", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("文本", groups[2].content)
    }

    // --- 测试有序列表插件 ---
    @Test
    fun testOrderedListPlugin() = runBlocking {
        val listText =
                """
            前置文本
            1. 第一项
            2. 第二项
            后置文本
        """.trimIndent()

        val stream = listText.asCharStream()
        val plugin = StreamMarkdownOrderedListPlugin()

        val groups = collectGroups(stream, plugin)

        assertTrue("应至少包含3个组", groups.size >= 3)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertTrue(groups[0].content.contains("前置文本"))

        // 列表（可能分为多个组）
        var listItemCount = 0
        for (group in groups) {
            if (group.tag === plugin) {
                listItemCount++
            }
        }
        assertEquals("应找到2个列表项", 2, listItemCount)

        // 最后一组：普通文本
        assertNull(groups.last().tag)
        assertTrue(groups.last().content.contains("后置文本"))
    }

    // --- 测试无序列表插件 ---
    @Test
    fun testUnorderedListPlugin() = runBlocking {
        val listText =
                """
            前置文本
            - 减号项
            + 加号项
            后置文本
        """.trimIndent()

        val stream = listText.asCharStream()
        val plugin = StreamMarkdownUnorderedListPlugin()

        val groups = collectGroups(stream, plugin)

        assertTrue("应至少包含4个组", groups.size >= 4)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertTrue(groups[0].content.contains("前置文本"))

        // 列表（可能分为多个组）
        var listItemCount = 0
        for (group in groups) {
            if (group.tag === plugin) {
                listItemCount++
            }
        }
        assertEquals("应找到2个列表项", 2, listItemCount)

        // 最后一组：普通文本
        assertNull(groups.last().tag)
        assertTrue(groups.last().content.contains("后置文本"))
    }

    // --- 测试多个插件共同工作 ---
    @Test
    fun testMultipleMarkdownPlugins() = runBlocking {
        val markdownText =
                """
            # 标题
            
            这是**粗体**和*斜体*，还有`代码`。
            
            ```
            代码块
            ```
            
            > 引用块
            
            [链接](https://example.com)
            
            ![图片](image.jpg)
            
            1. 有序列表
            - 无序列表
            
            ---
            
            ~~删除线~~和__下划线__
        """.trimIndent()

        val stream = markdownText.asCharStream()
        // 注意顺序很重要，长分隔符的插件必须排在前面
        val plugins =
                listOf(
                        StreamMarkdownFencedCodeBlockPlugin(),
                        StreamMarkdownUnderlinePlugin(),
                        StreamMarkdownBoldPlugin(),
                        StreamMarkdownStrikethroughPlugin(),
                        StreamMarkdownItalicPlugin(),
                        StreamMarkdownInlineCodePlugin(),
                        StreamMarkdownHeaderPlugin(),
                        StreamMarkdownLinkPlugin(),
                        StreamMarkdownImagePlugin(),
                        StreamMarkdownBlockQuotePlugin(),
                        StreamMarkdownHorizontalRulePlugin(),
                        StreamMarkdownOrderedListPlugin(),
                        StreamMarkdownUnorderedListPlugin()
                )

        val groupList = mutableListOf<GroupInfo>()
        stream.splitBy(plugins).collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            groupList.add(GroupInfo(group.tag, content.toString()))
        }

        // 验证至少所有插件类型都被找到
        assertTrue("应找到标题插件", groupList.any { it.tag is StreamMarkdownHeaderPlugin })
        assertTrue("应找到粗体插件", groupList.any { it.tag is StreamMarkdownBoldPlugin })
        assertTrue("应找到斜体插件", groupList.any { it.tag is StreamMarkdownItalicPlugin })
        assertTrue("应找到行内代码插件", groupList.any { it.tag is StreamMarkdownInlineCodePlugin })
        assertTrue("应找到代码块插件", groupList.any { it.tag is StreamMarkdownFencedCodeBlockPlugin })
        assertTrue("应找到链接插件", groupList.any { it.tag is StreamMarkdownLinkPlugin })
        assertTrue("应找到图片插件", groupList.any { it.tag is StreamMarkdownImagePlugin })
        assertTrue("应找到引用块插件", groupList.any { it.tag is StreamMarkdownBlockQuotePlugin })
        assertTrue("应找到水平线插件", groupList.any { it.tag is StreamMarkdownHorizontalRulePlugin })
        assertTrue("应找到删除线插件", groupList.any { it.tag is StreamMarkdownStrikethroughPlugin })
        assertTrue("应找到下划线插件", groupList.any { it.tag is StreamMarkdownUnderlinePlugin })
        assertTrue("应找到有序列表插件", groupList.any { it.tag is StreamMarkdownOrderedListPlugin })
        assertTrue("应找到无序列表插件", groupList.any { it.tag is StreamMarkdownUnorderedListPlugin })
    }

    // --- 测试不包含分隔符 ---
    @Test
    fun testPluginsWithoutDelimiters() = runBlocking {
        val boldText = "这是一段**粗体文字**普通文字"
        val stream = boldText.asCharStream()
        // 不包含分隔符
        val plugin = StreamMarkdownBoldPlugin(includeAsterisks = false)

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一段", groups[0].content)

        // 第二组：粗体文本（不含分隔符）
        assertSame(plugin, groups[1].tag)
        assertEquals("粗体文字", groups[1].content) // 不含**

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("普通文字", groups[2].content)

        // 测试不包含分隔符的链接
        val linkText = "这是[链接](https://example.com)"
        val linkStream = linkText.asCharStream()
        val linkPlugin = StreamMarkdownLinkPlugin()

        val linkGroups = collectGroups(linkStream, linkPlugin)
        assertEquals(2, linkGroups.size)
        assertSame(linkPlugin, linkGroups[1].tag)
        // 由于我们的实现不支持不包含分隔符时分别提取文本和URL，这里仍然会包括分隔符
        assertNotEquals("链接", linkGroups[1].content)
    }

    // --- 测试异常情况 ---
    @Test
    fun testEdgeCases() = runBlocking {
        // 1. 测试不闭合的粗体
        val unclosedBold = "这是**不闭合的粗体"
        val boldPlugin = StreamMarkdownBoldPlugin()

        val unclosedGroups = collectGroups(unclosedBold.asCharStream(), boldPlugin)

        assertEquals(2, unclosedGroups.size)
        assertNull(unclosedGroups[0].tag)

        // 2. 测试换行中断的行内代码
        val newlineInCode = "这是`行内\n代码`测试"
        val codePlugin = StreamMarkdownInlineCodePlugin()

        val newlineGroups = collectGroups(newlineInCode.asCharStream(), codePlugin)
        // 行内代码中断，判定为4
        assertEquals(4, newlineGroups.size)
        assertNull(newlineGroups[0].tag)

        // 3. 测试过长的标题（超过6个#）
        val tooLongHeader = "####### 这不是有效标题"
        val headerPlugin = StreamMarkdownHeaderPlugin()

        val headerGroups = collectGroups(tooLongHeader.asCharStream(), headerPlugin)
        // 不应被识别为标题
        println(headerGroups[0].content)
        assertEquals(1, headerGroups.size)
        assertNull(headerGroups[0].tag)

        // 4. 测试不完整的链接
        val invalidLink = "这是一个[链接文本](https://example"
        val linkPlugin = StreamMarkdownLinkPlugin()

        val linkGroups = collectGroups(invalidLink.asCharStream(), linkPlugin)
        assertEquals(2, linkGroups.size)
        assertNull(linkGroups[0].tag)
    }

    // --- Helper function ---
    private suspend fun collectGroups(
            stream: com.ai.assistance.operit.util.stream.Stream<Char>,
            plugin: StreamPlugin
    ): List<GroupInfo> {
        val groupList = mutableListOf<GroupInfo>()
        stream.splitBy(listOf(plugin)).collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            groupList.add(GroupInfo(group.tag, content.toString()))
        }
        return groupList
    }

    private data class GroupInfo(val tag: StreamPlugin?, val content: String)
}
