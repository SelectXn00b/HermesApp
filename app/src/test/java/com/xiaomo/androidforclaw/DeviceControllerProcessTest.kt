package com.xiaomo.androidforclaw

import com.xiaomo.androidforclaw.accessibility.service.ViewNode
import com.xiaomo.androidforclaw.accessibility.service.Point
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceControllerProcessTest {

    private fun makeNode(
        text: String? = null,
        contentDesc: String? = null,
        resourceId: String? = null,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        x: Int = 100,
        y: Int = 200
    ) = ViewNode(
        index = 0,
        text = text,
        resourceId = resourceId,
        className = "android.widget.Button",
        packageName = "com.test",
        contentDesc = contentDesc,
        clickable = clickable,
        enabled = true,
        focusable = false,
        focused = false,
        scrollable = scrollable,
        point = Point(x, y),
        left = x - 50,
        right = x + 50,
        top = y - 25,
        bottom = y + 25,
        node = null
    )

    @Test
    fun removeEmptyNodes_keepsNodesWithResourceId() {
        val nodes = listOf(
            makeNode(resourceId = "com.test:id/btn_send"),
            makeNode() // truly empty
        )
        val result = DeviceController.removeEmptyNodes(nodes)
        assertEquals("Node with resourceId should be kept", 1, result.size)
        assertEquals("com.test:id/btn_send", result[0].resourceId)
    }

    @Test
    fun removeEmptyNodes_keepsClickableNodes() {
        val nodes = listOf(
            makeNode(clickable = true),
            makeNode() // truly empty
        )
        val result = DeviceController.removeEmptyNodes(nodes)
        assertEquals("Clickable node should be kept", 1, result.size)
        assertTrue(result[0].clickable)
    }

    @Test
    fun removeEmptyNodes_keepsScrollableNodes() {
        val nodes = listOf(
            makeNode(scrollable = true),
            makeNode()
        )
        val result = DeviceController.removeEmptyNodes(nodes)
        assertEquals("Scrollable node should be kept", 1, result.size)
    }

    @Test
    fun removeEmptyNodes_keepsNodesWithText() {
        val nodes = listOf(
            makeNode(text = "Submit"),
            makeNode(contentDesc = "Back button"),
            makeNode()
        )
        val result = DeviceController.removeEmptyNodes(nodes)
        assertEquals(2, result.size)
    }

    @Test
    fun removeEmptyNodes_removeTrulyEmptyNodes() {
        val nodes = listOf(
            makeNode() // no text, no desc, no id, not clickable, not scrollable
        )
        val result = DeviceController.removeEmptyNodes(nodes)
        assertEquals("Truly empty node should be removed", 0, result.size)
    }

    @Test
    fun filterDuplicateNodes_dedupesByTextAndPosition() {
        val nodes = listOf(
            makeNode(text = "OK", x = 100, y = 200),
            makeNode(text = "OK", x = 100, y = 200), // same text+position
            makeNode(text = "OK", x = 300, y = 400)  // same text, different position
        )
        val result = DeviceController.filterDuplicateNodes(nodes)
        assertEquals("Same text+position should dedup to 1, different position kept", 2, result.size)
    }

    @Test
    fun filterDuplicateNodes_keepsClickableDuplicates() {
        val nodes = listOf(
            makeNode(text = "Item", x = 100, y = 200),
            makeNode(text = "Item", x = 100, y = 200, clickable = true) // duplicate but clickable
        )
        val result = DeviceController.filterDuplicateNodes(nodes)
        assertEquals("Clickable duplicate should be kept", 2, result.size)
    }

    @Test
    fun processHierarchy_preservesUsefulNodes() {
        val nodes = listOf(
            makeNode(text = "Submit", clickable = true),
            makeNode(resourceId = "com.test:id/icon_menu", clickable = true),
            makeNode(contentDesc = "Navigate up"),
            makeNode() // empty
        )
        val result = DeviceController.processHierarchy(nodes)
        assertTrue("Should keep at least 3 useful nodes", result.size >= 3)
    }
}
