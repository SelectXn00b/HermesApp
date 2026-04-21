package com.ai.assistance.operit.util.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stream.splitBy功能的Android测试类 主要测试splitBy与XML插件结合使用的情况 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class StreamSplitByTest {

    private lateinit var xmlPlugin: StreamXmlPlugin

    @Before
    fun setup() {
        xmlPlugin = StreamXmlPlugin()
    }

    @Test
    fun testSplitByWithXmlPlugin() = runBlocking {
        // 创建包含XML和普通文本的字符流
        val charStream = "Text before <tag>Content</tag> and after".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 应该得到3个组: 前文本，XML标签，后文本
        assertEquals("应该分割成3个组", 3, groups.size)

        // 验证各组内容
        val contents =
                groups.map { group ->
                    var content = ""
                    runBlocking { group.stream.collect { content += it } }
                    Pair(group.tag, content)
                }

        // 第一组应该是普通文本（tag为null）
        assertNull("第一组应该是普通文本", contents[0].first)
        assertEquals("第一组内容应该匹配", "Text before ", contents[0].second)

        // 第二组应该是XML（tag为xmlPlugin）
        assertSame("第二组应该是XML标签", xmlPlugin, contents[1].first)
        assertEquals("第二组内容应该匹配", "<tag>Content</tag>", contents[1].second)

        // 第三组应该是普通文本（tag为null）
        assertNull("第三组应该是普通文本", contents[2].first)
        assertEquals("第三组内容应该匹配", " and after", contents[2].second)
    }

    @Test
    fun testSplitByWithMultipleXmlTags() = runBlocking {
        // 创建包含多个XML标签的字符流
        val charStream = "<tag1>First</tag1>Middle<tag2>Second</tag2>".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 应该得到3个组: 第一个XML标签，中间文本，第二个XML标签
        assertEquals("应该分割成3个组", 3, groups.size)

        // 验证各组内容
        val contents =
                groups.map { group ->
                    var content = ""
                    runBlocking { group.stream.collect { content += it } }
                    Pair(group.tag, content)
                }

        // 第一组应该是XML（tag为xmlPlugin）
        assertSame("第一组应该是XML标签", xmlPlugin, contents[0].first)
        assertEquals("第一组内容应该匹配", "<tag1>First</tag1>", contents[0].second)

        // 第二组应该是普通文本（tag为null）
        assertNull("第二组应该是普通文本", contents[1].first)
        assertEquals("第二组内容应该匹配", "Middle", contents[1].second)

        // 第三组应该是XML（tag为xmlPlugin）
        assertSame("第三组应该是XML标签", xmlPlugin, contents[2].first)
        assertEquals("第三组内容应该匹配", "<tag2>Second</tag2>", contents[2].second)
    }

    @Test
    fun testSplitByWithIncompleteXml() = runBlocking {
        // 创建包含不完整XML标签的字符流
        val charStream = "Start <tag>Incomplete".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 应该得到2个组: 开始文本和不完整XML
        assertEquals("应该分割成2个组", 2, groups.size)

        // 验证各组内容
        val contents =
                groups.map { group ->
                    var content = ""
                    runBlocking { group.stream.collect { content += it } }
                    Pair(group.tag, content)
                }

        // 第一组应该是普通文本（tag为null）
        assertNull("第一组应该是普通文本", contents[0].first)
        assertEquals("第一组内容应该匹配", "Start ", contents[0].second)

        // 第二组应该是XML（tag为xmlPlugin）
        assertSame("第二组应该是XML标签", xmlPlugin, contents[1].first)
        assertEquals("第二组内容应该匹配", "<tag>Incomplete", contents[1].second)
    }

    @Test
    fun testSplitByWithNestedLikeTags() = runBlocking {
        // 创建包含嵌套式标签的字符流（这里模拟嵌套，实际XML插件不支持真正的嵌套）
        val charStream = "<outer>Text <inner>Inner</inner> More</outer>".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 验证各组内容
        val contents =
                groups.map { group ->
                    var content = ""
                    runBlocking { group.stream.collect { content += it } }
                    Pair(group.tag, content)
                }

        // 确保分组合理（注意：实际行为可能因插件实现而异）
        assertTrue("应该至少有1个组", groups.size >= 1)

        // 检查第一个组的标签类型（应该是XML）
        assertSame("第一组应该是XML标签", xmlPlugin, contents[0].first)
    }

    @Test
    fun testSplitByWithEmptyInput() = runBlocking {
        // 创建空字符流
        val charStream = "".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 空输入应该没有任何组
        assertEquals("空输入应该不产生任何组", 0, groups.size)
    }

    @Test
    fun testSplitByWithOnlyText() = runBlocking {
        // 创建只有普通文本的字符流
        val charStream = "Just plain text, no XML here".asSequence().asStream()

        // 用XML插件分割字符流
        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        // 收集结果组
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 应该只有一个普通文本组
        assertEquals("应该只有一个组", 1, groups.size)

        // 验证组内容
        val content = runBlocking {
            var result = ""
            groups[0].stream.collect { result += it }
            result
        }

        assertNull("应该是普通文本组（tag为null）", groups[0].tag)
        assertEquals("内容应该匹配", "Just plain text, no XML here", content)
    }

    @Test
    fun testSplitByWithNestedContentForReadme() = runBlocking {
        // 这个测试用例用于验证 StreamXmlPlugin 的实际行为，以便纠正 README.md 中的文档。
        val xmlStreamText = "<user><id>123</id><name>test</name></user>"
        val charStream = xmlStreamText.asSequence().asStream()

        val groupedStream = charStream.splitBy(listOf(xmlPlugin))

        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        // 当前的插件实现不支持嵌套。它会找到第一个 <user> 标签，并消耗所有内容，直到找到 </user>。
        // 因此，结果应该是一个单独的组。
        assertEquals("应该只有一个XML组", 1, groups.size)

        val contents =
            groups.map { group ->
                var content = ""
                runBlocking { group.stream.collect { content += it } }
                Pair(group.tag, content)
            }

        // 该组应该是一个XML组
        assertSame("该组应该是XML组", xmlPlugin, contents[0].first)
        // 内容应该是整个XML字符串
        assertEquals("内容应该是完整的XML字符串", xmlStreamText, contents[0].second)
    }

    @Test
    fun testSplitByWithEmojiTrigger() = runBlocking {
        // U+2600 + U+FE0F: 验证带变体选择符的 emoji 也能触发后续 XML 起始识别
        val emoji = "\u2600\uFE0F"
        val input = "Text $emoji <tag>Sun</tag> tail"
        val charStream = input.asSequence().asStream()

        val groupedStream = charStream.splitBy(listOf(xmlPlugin))
        val groups = mutableListOf<StreamGroup<StreamPlugin?>>()
        groupedStream.collect { groups.add(it) }

        assertEquals("应该分割成3个组", 3, groups.size)

        val contents =
            groups.map { group ->
                var content = ""
                runBlocking { group.stream.collect { content += it } }
                Pair(group.tag, content)
            }

        assertNull("第一组应该是普通文本", contents[0].first)
        assertEquals("第一组内容应该匹配", "Text $emoji ", contents[0].second)
        assertSame("第二组应该是XML标签", xmlPlugin, contents[1].first)
        assertEquals("第二组内容应该匹配", "<tag>Sun</tag>", contents[1].second)
        assertNull("第三组应该是普通文本", contents[2].first)
        assertEquals("第三组内容应该匹配", " tail", contents[2].second)
    }
}
