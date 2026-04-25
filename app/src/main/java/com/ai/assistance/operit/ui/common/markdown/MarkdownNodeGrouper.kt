package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.stream.Stream

sealed class MarkdownGroupedItem {
    data class Single(val index: Int) : MarkdownGroupedItem()

    data class Group(
        val startIndex: Int,
        val endIndexInclusive: Int,
        val stableKey: String
    ) : MarkdownGroupedItem()
}

interface MarkdownNodeGrouper {
    fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem>

    @Composable
    fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean
    )
}

object NoopMarkdownNodeGrouper : MarkdownNodeGrouper {
    override fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem> {
        return nodes.indices.map { MarkdownGroupedItem.Single(it) }
    }

    @Composable
    override fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        xmlStreamResolver: (Int) -> Stream<String>?,
        fillMaxWidth: Boolean
    ) {
    }
}
