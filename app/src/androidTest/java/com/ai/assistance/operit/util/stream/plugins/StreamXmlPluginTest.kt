package com.ai.assistance.operit.util.stream.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** StreamXmlPlugin的Android测试类 */
@RunWith(AndroidJUnit4::class)
class StreamXmlPluginTest {

    private lateinit var plugin: StreamXmlPlugin

    @Before
    fun setup() {
        plugin = StreamXmlPlugin()
        plugin.initPlugin()
    }

    @Test
    fun testInitialState() {
        // 初始状态应该是空闲的
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test
    fun testStartTagDetection() {
        // 检测"<"字符是否正确触发尝试开始状态
        plugin.processChar('<', false)
        assertEquals(PluginState.TRYING, plugin.state)

        plugin.processChar('t', false)
        assertEquals(PluginState.TRYING, plugin.state)
    }

    @Test
    fun testCompleteTagRecognitionAndProcessingEnter() {
        val xmlTag = "<test>"
        xmlTag.forEach { plugin.processChar(it, false) }

        assertEquals("Should be in processing state after a full start tag", PluginState.PROCESSING, plugin.state)
    }

    @Test
    fun testTagWithAttributes() {
        val xmlTag = "<test attr=\"value\">"
        xmlTag.forEach { plugin.processChar(it, false) }
        assertEquals("Should be in processing state after a tag with attributes", PluginState.PROCESSING, plugin.state)
    }

    @Test
    fun testEndTagRecognition() {
        val fullXml = "<test>content</test>"
        fullXml.forEach { plugin.processChar(it, false) }

        assertEquals("Should be in IDLE state after the end tag is found", PluginState.IDLE, plugin.state)
    }

    @Test
    fun testFailedMatch() {
        // Test non-XML content
        val nonXml = "This is not XML"
        nonXml.forEach { plugin.processChar(it, false) }

        assertEquals(
                "Should be in IDLE state after non-matching input",
                PluginState.IDLE, 
                plugin.state
        )

        // Test an incomplete tag
        plugin.reset()
        val incompleteXml = "<tag"
        incompleteXml.forEach { plugin.processChar(it, false) }
        assertEquals("Should be in TRYING state with an incomplete tag", PluginState.TRYING, plugin.state)

        // Reset and then feed invalid characters
        plugin.reset()
        plugin.processChar('<', false)
        plugin.processChar(' ', false) // Invalid start for a tag name
        assertEquals(
                "Should be in IDLE state after an invalid tag char",
                PluginState.IDLE,
                plugin.state
        )
    }

    @Test
    fun testResetFunction() {
        // 先进入处理状态
        val xmlTag = "<test>"
        xmlTag.forEach { plugin.processChar(it, false) }

        assertEquals(PluginState.PROCESSING, plugin.state)

        // 重置插件
        plugin.reset()

        // 检查状态复位
        assertEquals(PluginState.IDLE, plugin.state)
    }

    @Test
    fun testFullXmlProcessingAndCharacterConsumption() {
        val fullXml = "<root attr=\"value\">Content</root>"

        // Test full processing by feeding the entire XML string
        plugin.reset()
        fullXml.forEach { c -> plugin.processChar(c, false) }

        assertEquals("Should be in IDLE state after completion", PluginState.IDLE, plugin.state)
    }
}
