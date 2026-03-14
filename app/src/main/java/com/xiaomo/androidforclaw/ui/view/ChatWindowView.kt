/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaomo.androidforclaw.core.MainEntryNew
import com.draco.ladb.R
import io.noties.markwon.Markwon

/**
 * Chat window View - Based on OpenClaw design
 *
 * Features:
 * 1. Real-time display of AI conversation messages
 * 2. Support Tool call display (with badge)
 * 3. Support reasoning process expand/collapse
 * 4. Message copy function
 * 5. Stop generation button
 * 6. Input box (optional)
 */
class ChatWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ChatWindowView"

        /**
         * Clean Extended Thinking tags
         * Remove <think>...</think> content, keep only actual reply
         */
        private fun cleanThinkingTags(content: String): String {
            // Remove <think>...</think> tags and content
            var cleaned = content.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            // Remove possible isolated tags
            cleaned = cleaned.replace("<think>", "").replace("</think>", "")
            // Clean extra whitespace
            return cleaned.trim()
        }
    }

    private lateinit var tvChatTitle: TextView
    private lateinit var btnStopGeneration: android.widget.ImageView
    private lateinit var btnMinimize: android.widget.ImageView
    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: android.widget.ImageView

    private lateinit var adapter: ChatMessageAdapter
    private var messages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var markwon: Markwon

    init {
        // Initialize Markwon
        markwon = Markwon.create(context)
        setupViews()
    }

    /**
     * Set message list (for data restoration)
     */
    fun setMessages(messageList: MutableList<ChatMessage>) {
        messages = messageList
        adapter = ChatMessageAdapter(context, messages, markwon)
        rvMessages.adapter = adapter
        if (messages.isNotEmpty()) {
            rvMessages.scrollToPosition(messages.size - 1)
        }
    }

    /**
     * Get current message list
     */
    fun getMessages(): MutableList<ChatMessage> {
        return messages
    }

    private fun setupViews() {
        orientation = VERTICAL
        background = android.graphics.drawable.GradientDrawable().apply {
            colors = intArrayOf(
                android.graphics.Color.parseColor("#0F2027"),
                android.graphics.Color.parseColor("#203A43"),
                android.graphics.Color.parseColor("#2C5364")
            )
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
        }

        // Create top toolbar
        val toolbar = createToolbar()
        addView(toolbar)

        // Create message list
        rvMessages = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0).apply {
                weight = 1f
            }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            clipToPadding = false
        }
        addView(rvMessages)

        // Create bottom input area
        val inputArea = createInputArea()
        addView(inputArea)

        setupRecyclerView()
        setupListeners()
    }

    private fun createToolbar(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(60))
            orientation = HORIZONTAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(12), dpToPx(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                colors = intArrayOf(
                    android.graphics.Color.parseColor("#667EEA"),
                    android.graphics.Color.parseColor("#764BA2")
                )
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
            }
            gravity = android.view.Gravity.CENTER_VERTICAL
            elevation = dpToPx(4).toFloat()

            // Title
            tvChatTitle = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                }
                text = "🤖 AndroidForClaw"
                textSize = 20f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(tvChatTitle)

            // Stop button
            btnStopGeneration = android.widget.ImageView(context).apply {
                layoutParams = LayoutParams(dpToPx(44), dpToPx(44))
                setImageResource(android.R.drawable.ic_media_pause)
                setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                isClickable = true
                isFocusable = true
                background = createRippleDrawable(android.graphics.Color.parseColor("#40FFFFFF"))
            }
            addView(btnStopGeneration)

            // Minimize button
            btnMinimize = android.widget.ImageView(context).apply {
                layoutParams = LayoutParams(dpToPx(44), dpToPx(44)).apply {
                    marginStart = dpToPx(8)
                }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                isClickable = true
                isFocusable = true
                background = createRippleDrawable(android.graphics.Color.parseColor("#40FFFFFF"))
            }
            addView(btnMinimize)
        }
    }

    private fun createRippleDrawable(color: Int): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#60FFFFFF")),
                shape,
                null
            )
        } else {
            shape
        }
    }

    private fun createInputArea(): LinearLayout {
        return LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            orientation = HORIZONTAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(16))
            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
            gravity = android.view.Gravity.CENTER_VERTICAL
            elevation = dpToPx(8).toFloat()

            // Input box container
            val inputContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                    marginEnd = dpToPx(8)
                }
                orientation = HORIZONTAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#16213E"))
                    cornerRadius = dpToPx(24).toFloat()
                    setStroke(dpToPx(1), android.graphics.Color.parseColor("#4A5568"))
                }
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))

                etInput = EditText(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    hint = "💬 输入指令..."
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(android.graphics.Color.parseColor("#718096"))
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    textSize = 15f
                    maxLines = 4
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                }
                addView(etInput)
            }
            addView(inputContainer)

            // Send button
            btnSend = android.widget.ImageView(context).apply {
                layoutParams = LayoutParams(dpToPx(50), dpToPx(50))
                setImageResource(android.R.drawable.ic_menu_send)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    colors = intArrayOf(
                        android.graphics.Color.parseColor("#667EEA"),
                        android.graphics.Color.parseColor("#764BA2")
                    )
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                }
                elevation = dpToPx(4).toFloat()
            }
            addView(btnSend)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(context, messages, markwon)
        rvMessages.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true  // Stack from bottom
        }
        rvMessages.adapter = adapter

        // Auto-scroll to latest message
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                rvMessages.smoothScrollToPosition(messages.size - 1)
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                // If last message updated, stay at bottom
                if (positionStart + itemCount >= messages.size) {
                    rvMessages.smoothScrollToPosition(messages.size - 1)
                }
            }
        })
    }

    private fun setupListeners() {
        // Stop generation
        btnStopGeneration.setOnClickListener {
            Log.d(TAG, "停止生成")
            MainEntryNew.cancelCurrentJob(false)
            Toast.makeText(context, "已停止生成", Toast.LENGTH_SHORT).show()
        }

        // Minimize window
        btnMinimize.setOnClickListener {
            Log.d(TAG, "缩小按钮被点击")
            try {
                if (onMinimizeListener != null) {
                    Log.d(TAG, "调用 onMinimizeListener")
                    onMinimizeListener?.invoke()
                } else {
                    Log.e(TAG, "onMinimizeListener 为 null！")
                }
            } catch (e: Exception) {
                Log.e(TAG, "缩小窗口失败", e)
            }
        }

        // Send button
        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                etInput.text.clear()
            }
        }
    }

    /**
     * Add message
     */
    fun addMessage(message: ChatMessage) {
        // Clean Extended Thinking tags
        val cleanedMessage = message.copy(content = cleanThinkingTags(message.content))
        messages.add(cleanedMessage)
        adapter.notifyItemInserted(messages.size - 1)
    }

    /**
     * Update last message (for streaming output)
     */
    fun updateLastMessage(content: String) {
        if (messages.isEmpty()) return
        val lastIndex = messages.size - 1
        // Clean Extended Thinking tags
        val cleanedContent = cleanThinkingTags(content)
        messages[lastIndex] = messages[lastIndex].copy(
            content = cleanedContent,
            isStreaming = true
        )
        adapter.notifyItemChanged(lastIndex)
    }

    /**
     * Finish last message streaming output
     */
    fun finishLastMessage() {
        if (messages.isEmpty()) return
        val lastIndex = messages.size - 1
        messages[lastIndex] = messages[lastIndex].copy(isStreaming = false)
        adapter.notifyItemChanged(lastIndex)
    }

    /**
     * Add Tool call message
     */
    fun addToolCall(toolName: String, args: String, result: String) {
        val message = ChatMessage(
            role = "tool",
            content = result,
            timestamp = System.currentTimeMillis(),
            toolName = toolName,
            reasoning = null
        )
        addMessage(message)
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        messages.clear()
        adapter.notifyDataSetChanged()
    }

    /**
     * Send message (reserved interface)
     */
    private fun sendMessage(text: String) {
        // TODO: Implement send message functionality
        val message = ChatMessage(
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        addMessage(message)
        Log.d(TAG, "发送消息: $text")
    }

    /**
     * Set minimize button listener
     */
    private var onMinimizeListener: (() -> Unit)? = null
    fun setOnMinimizeListener(listener: () -> Unit) {
        onMinimizeListener = listener
    }

    fun cleanup() {
        Log.d(TAG, "清理资源")
    }
}

/**
 * Chat message data class
 */
data class ChatMessage(
    val role: String,  // "user", "assistant", "tool"
    val content: String,
    val timestamp: Long,
    val toolName: String? = null,  // If tool call, show tool name
    val reasoning: String? = null,  // Reasoning process (optional)
    val isStreaming: Boolean = false  // Whether streaming output
)

/**
 * Chat message Adapter
 */
class ChatMessageAdapter(
    private val context: Context,
    private val messages: List<ChatMessage>,
    private val markwon: Markwon
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    companion object {
        private const val TAG = "ChatMessageAdapter"
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val view = createMessageItemView(parent.context)
        return MessageViewHolder(view)
    }

    private fun createMessageItemView(context: Context): View {
        return LinearLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(6, context), 0, dpToPx(6, context))
            }
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12, context), dpToPx(4, context), dpToPx(12, context), dpToPx(4, context))

            // 消息容器
            val messageContainer = LinearLayout(context).apply {
                id = android.view.View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16, context), dpToPx(14, context), dpToPx(16, context), dpToPx(14, context))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#1E3A5F"))
                    cornerRadius = dpToPx(16, context).toFloat()
                    setStroke(dpToPx(1, context), android.graphics.Color.parseColor("#2D5A7B"))
                }
                elevation = dpToPx(2, context).toFloat()

                // 头部信息（可点击展开）
                val header = LinearLayout(context).apply {
                    id = android.view.View.generateViewId()
                    tag = "header"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dpToPx(10, context))
                    isClickable = true
                    isFocusable = true

                    // 角色
                    val tvRole = TextView(context).apply {
                        id = R.id.tvRole
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "🤖 AI Assistant"
                        textSize = 15f
                        setTextColor(android.graphics.Color.parseColor("#A0D9F7"))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    addView(tvRole)

                    // 时间戳
                    val tvTimestamp = TextView(context).apply {
                        id = R.id.tvTimestamp
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = dpToPx(10, context)
                        }
                        text = "14:30"
                        textSize = 11f
                        setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                    }
                    addView(tvTimestamp)

                    // Tool Badge
                    val tvToolBadge = TextView(context).apply {
                        id = R.id.tvToolBadge
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginStart = dpToPx(10, context)
                        }
                        text = "🔧 tap"
                        textSize = 10f
                        setTextColor(android.graphics.Color.parseColor("#34D399"))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#10B981"))
                            alpha = 50
                            cornerRadius = dpToPx(8, context).toFloat()
                        }
                        setPadding(dpToPx(8, context), dpToPx(4, context), dpToPx(8, context), dpToPx(4, context))
                        visibility = View.GONE
                    }
                    addView(tvToolBadge)

                    // 展开指示器
                    val tvExpandIndicator = TextView(context).apply {
                        id = android.view.View.generateViewId()
                        tag = "expandIndicator"
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 1f
                        }
                        text = "▼"
                        textSize = 12f
                        gravity = android.view.Gravity.END
                        setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                    }
                    addView(tvExpandIndicator)
                }
                addView(header)

                // 内容预览（折叠状态）
                val tvContentPreview = TextView(context).apply {
                    id = android.view.View.generateViewId()
                    tag = "contentPreview"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "This is a preview..."
                    textSize = 14f
                    setTextColor(android.graphics.Color.parseColor("#E2E8F0"))
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                addView(tvContentPreview)

                // 完整内容（展开状态，初始隐藏）
                val tvContent = TextView(context).apply {
                    id = R.id.tvContent
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "This is the full message content"
                    textSize = 14f
                    setTextColor(android.graphics.Color.parseColor("#E2E8F0"))
                    setLineSpacing(dpToPx(4, context).toFloat(), 1f)
                    visibility = View.GONE
                }
                addView(tvContent)

                // 推理过程容器（只在展开状态显示）
                val layoutReasoning = LinearLayout(context).apply {
                    id = R.id.layoutReasoning
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(12, context)
                    }
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE

                    val tvReasoningHeader = TextView(context).apply {
                        id = R.id.tvReasoningHeader
                        text = "🧠 推理过程"
                        textSize = 13f
                        setTextColor(android.graphics.Color.parseColor("#C084FC"))
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, 0, 0, dpToPx(6, context))
                    }
                    addView(tvReasoningHeader)

                    val tvReasoningContent = TextView(context).apply {
                        id = R.id.tvReasoningContent
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "Reasoning content..."
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#1E293B"))
                            cornerRadius = dpToPx(8, context).toFloat()
                        }
                        setPadding(dpToPx(12, context), dpToPx(10, context), dpToPx(12, context), dpToPx(10, context))
                    }
                    addView(tvReasoningContent)
                }
                addView(layoutReasoning)

                // 底部操作栏（只在展开状态显示）
                val bottomBar = LinearLayout(context).apply {
                    id = android.view.View.generateViewId()
                    tag = "bottomBar"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(12, context)
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.END
                    visibility = View.GONE

                    val btnCopy = android.widget.ImageView(context).apply {
                        id = R.id.btnCopy
                        layoutParams = LinearLayout.LayoutParams(dpToPx(36, context), dpToPx(36, context))
                        setImageResource(android.R.drawable.ic_menu_save)
                        setPadding(dpToPx(8, context), dpToPx(8, context), dpToPx(8, context), dpToPx(8, context))
                        isClickable = true
                        isFocusable = true
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#334155"))
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                        }
                        setColorFilter(android.graphics.Color.parseColor("#94A3B8"))
                    }
                    addView(btnCopy)
                }
                addView(bottomBar)
            }
            addView(messageContainer)

            // 时间分隔线（隐藏）
            val tvTimeDivider = TextView(context).apply {
                id = R.id.tvTimeDivider
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                text = "今天 14:30"
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#888888"))
                setPadding(dpToPx(4, context), dpToPx(4, context), dpToPx(4, context), dpToPx(4, context))
                visibility = View.GONE
            }
            addView(tvTimeDivider, 0) // 添加到顶部
        }
    }

    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount() = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTimeDivider: TextView = itemView.findViewById(R.id.tvTimeDivider)
        private val tvRole: TextView = itemView.findViewById(R.id.tvRole)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvToolBadge: TextView = itemView.findViewById(R.id.tvToolBadge)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val layoutReasoning: LinearLayout = itemView.findViewById(R.id.layoutReasoning)
        private val tvReasoningHeader: TextView = itemView.findViewById(R.id.tvReasoningHeader)
        private val tvReasoningContent: TextView = itemView.findViewById(R.id.tvReasoningContent)
        private val btnCopy: android.widget.ImageView = itemView.findViewById(R.id.btnCopy)

        // 找到新增的视图
        // 注意：tvTimeDivider 在索引 0，messageContainer 在索引 1
        private val messageContainer = (itemView as LinearLayout).getChildAt(1) as LinearLayout
        private val header = messageContainer.findViewWithTag<LinearLayout>("header")
        private val expandIndicator = header?.findViewWithTag<TextView>("expandIndicator")
        private val contentPreview = messageContainer.findViewWithTag<TextView>("contentPreview")
        private val bottomBar = messageContainer.findViewWithTag<LinearLayout>("bottomBar")

        private var isExpanded = false

        fun bind(message: ChatMessage) {
            // 重置展开状态
            isExpanded = false

            // 角色
            tvRole.text = when (message.role) {
                "user" -> "👤 用户"
                "assistant" -> "🤖 AI 助手"
                "tool" -> "🔧 工具"
                else -> message.role
            }

            // 时间戳
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(message.timestamp))
            tvTimestamp.text = time

            // Tool Badge
            if (message.toolName != null) {
                tvToolBadge.visibility = View.VISIBLE
                tvToolBadge.text = "🔧 ${message.toolName}"
            } else {
                tvToolBadge.visibility = View.GONE
            }

            // 设置预览和完整内容
            val preview = if (message.content.length > 60) {
                message.content.substring(0, 60) + "..."
            } else {
                message.content
            }
            contentPreview?.text = preview
            markwon.setMarkdown(tvContent, message.content)

            // 如果正在流式输出，添加光标效果
            if (message.isStreaming) {
                tvContent.append(" ▋")
                contentPreview?.append(" ▋")
            }

            // 推理过程
            if (message.reasoning != null) {
                layoutReasoning.visibility = View.VISIBLE
                tvReasoningContent.text = message.reasoning
            } else {
                layoutReasoning.visibility = View.GONE
            }

            // 初始状态：显示预览，隐藏详情
            contentPreview?.visibility = View.VISIBLE
            tvContent.visibility = View.GONE
            layoutReasoning.visibility = View.GONE
            bottomBar?.visibility = View.GONE
            expandIndicator?.text = "▼"

            // 点击头部展开/收起
            header?.setOnClickListener {
                isExpanded = !isExpanded
                if (isExpanded) {
                    // 展开
                    contentPreview?.visibility = View.GONE
                    tvContent.visibility = View.VISIBLE
                    if (message.reasoning != null) {
                        layoutReasoning.visibility = View.VISIBLE
                    }
                    bottomBar?.visibility = View.VISIBLE
                    expandIndicator?.text = "▲"
                } else {
                    // 收起
                    contentPreview?.visibility = View.VISIBLE
                    tvContent.visibility = View.GONE
                    layoutReasoning.visibility = View.GONE
                    bottomBar?.visibility = View.GONE
                    expandIndicator?.text = "▼"
                }
            }

            // 复制按钮
            btnCopy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("message", message.content)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }

            // 时间分隔线（简化版，暂时隐藏）
            tvTimeDivider.visibility = View.GONE
        }
    }
}
