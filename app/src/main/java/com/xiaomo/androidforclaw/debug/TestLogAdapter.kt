/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: debug and test runners.
 */
package com.xiaomo.androidforclaw.ui.adapter

import android.annotation.SuppressLint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.xiaomo.androidforclaw.data.model.ItemType
import com.xiaomo.androidforclaw.data.model.StreamingStatus
import com.xiaomo.androidforclaw.data.model.TestLogItem
import com.xiaomo.androidforclaw.ext.setMarkdownText
import com.bumptech.glide.Glide
import com.draco.ladb.R
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference

class TestLogAdapter : RecyclerView.Adapter<TestLogAdapter.BaseTypeViewHolder>() {

    // 数据列表
    val items: MutableList<ExpandItem> = mutableListOf()
    private val viewHolderCache = SparseArray<TestLogAdapter.BaseTypeViewHolder>()

    // 视图类型
    companion object {
        private const val VIEW_TYPE_1 = 1 // 对应item_type1.xml 纯文本
        private const val VIEW_TYPE_2 = 2 // 对应item_type2.xml 纯图片
        private const val VIEW_TYPE_3 = 3 // 对应item_type3.xml 双图片+文本
    }

    // 创建不同类型的ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseTypeViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_1 -> {
                val view = layoutInflater.inflate(R.layout.item_type1, parent, false)
                Type1ViewHolder(view)
            }

            VIEW_TYPE_2 -> {
                val view = layoutInflater.inflate(R.layout.item_type2, parent, false)
                Type2ViewHolder(view)
            }

            VIEW_TYPE_3 -> {
                val view = layoutInflater.inflate(R.layout.item_type3, parent, false)
                Type3ViewHolder(view)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // 获取视图类型
    override fun getItemViewType(position: Int): Int {
        return when (items[position].data.type) {
            ItemType.TEXT -> VIEW_TYPE_1
            ItemType.IMAGE -> VIEW_TYPE_2
            ItemType.IMAGE_IMAGE_TEXT -> VIEW_TYPE_3
        }
    }

    // 绑定数据
    override fun onBindViewHolder(holder: BaseTypeViewHolder, position: Int) {
        holder.reset()
        val item = items[position]
        holder.bind(item)
        viewHolderCache.put(position, holder)
    }

    override fun getItemCount(): Int = items.size

    // 追加完整的item
    fun addItem(item: ExpandItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    // 设置所有items
    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: List<ExpandItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: BaseTypeViewHolder) {
        super.onViewRecycled(holder)
        holder.destroy()
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            viewHolderCache.remove(position)
        }
    }

    override fun getItemId(position: Int): Long {
        return items[position].data.id.hashCode().toLong()
    }

    fun destroyAllViewHolders() {
        for (i in 0 until viewHolderCache.size()) {
            viewHolderCache.valueAt(i)?.destroy()
        }
        viewHolderCache.clear()
    }

    // Type1 ViewHolder
    inner class Type1ViewHolder(view: View) : BaseTypeViewHolder(view) {
        private val textTitle: TextView = view.findViewById(R.id.text_title)
        private val btnExpandCollapse: Button = view.findViewById(R.id.btn_expand_collapse)
        private val progressBar: ProgressBar = view.findViewById(R.id.status_progress)
        private val textContent: TextView = view.findViewById(R.id.text_content)
        private val textDuration: TextView? = view.findViewById(R.id.text_duration)

        @OptIn(DelicateCoroutinesApi::class)
        override fun bind(item: ExpandItem) {
            // 绑定新 item 前先取消旧协程，防止更新到错误的 ViewHolder
            job?.cancel()
            item.data.holder = WeakReference(this)

            currentItemId = item.data.id
            textTitle.text = item.data.title
            
            // 设置duration显示
            if (item.data.duration > 0 && textDuration != null) {
                // 格式化时间，毫秒转秒
                val seconds = item.data.duration / 1000.0
                textDuration.text = String.format("%.1fs", seconds)
                textDuration.isVisible = true
            } else if (textDuration != null) {
                textDuration.isVisible = false
            }
            
            btnExpandCollapse.text = if (item.isExpand) "收起" else "展开"
            btnExpandCollapse.isVisible =
                if (item.showCollapseBtn) item.data.status == StreamingStatus.COMPLETED else false
            textContent.isVisible = item.isExpand || item.data.status == StreamingStatus.NOT_STARTED
            if (item.data.status == StreamingStatus.COMPLETED) {
                progressBar.isVisible = false
                try {
                    textContent.setMarkdownText(item.data.content)
                } catch (e: Exception) {
                    e.printStackTrace()
                    textContent.setText(e.message)
                }
            } else {
                var lastType = ""
                val sb = StringBuilder()
                job = GlobalScope.launch(Dispatchers.IO) {
                    item.data.flow?.buffer(capacity = 1024)?.collect { data ->
                        // 检查 ViewHolder 是否还在显示同一个 item，防止乱序
                        val type1ViewHolder = item.data.holder?.get()
//                        if (currentItemId != item.data.id) {
//                            cancel()
//                        }

                        val json = JSONObject(data)

                        if (json.has("_sse_error")) {
                            withContext(Dispatchers.Main) {
                                val hint = "连接出现错误，请等待重试"
                                val redSpan = ForegroundColorSpan(ContextCompat.getColor(textContent.context, android.R.color.holo_red_light))
                                val str = sb.append(hint).toString()
                                val spannableString = SpannableString(str)
                                spannableString.setSpan(redSpan, str.length - hint.length, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                type1ViewHolder?.textContent?.text = spannableString
                                type1ViewHolder?.btnExpandCollapse?.isVisible = false
                                type1ViewHolder?.progressBar?.isVisible = true
                            }
                            return@collect
                        }
                        if (json.has("requestStyle") && json.getString("requestStyle") == "block") {
                            sb.setLength(0)
                            val childJson = JSONObject(json.getString("content"))
                            sb.append(childJson.getString("answer"))
                            withContext(Dispatchers.Main) {
                                type1ViewHolder?.btnExpandCollapse?.isVisible = true
                                type1ViewHolder?.btnExpandCollapse?.text = "展开"
                                type1ViewHolder?.progressBar?.isVisible = false
                                type1ViewHolder?.textContent?.isVisible = false
                                cancel()
                            }
                        }
                        if (json.has("event")) {
                            if (lastType == "message" && json.getString("event") != "message") {
                                withContext(Dispatchers.Main) {
                                    type1ViewHolder?.btnExpandCollapse?.isVisible = true
                                    type1ViewHolder?.btnExpandCollapse?.text = "展开"
                                    type1ViewHolder?.progressBar?.isVisible = false
                                    type1ViewHolder?.textContent?.isVisible = false
                                    cancel()
                                }
                            }
                            if (json.getString("event") == "message") {
                                val answer = json.getString("answer")
                                sb.append(answer)
                                withContext(Dispatchers.Main) {
                                    type1ViewHolder?.btnExpandCollapse?.isVisible = false
                                    type1ViewHolder?.progressBar?.isVisible = true
                                    type1ViewHolder?.textContent?.setMarkdownText(sb.toString())
                                }
                            }
                            lastType = json.getString("event")
                        }
                    }
                }
            }
            textContent.setOnClickListener {
                Log.d("TestLogAdapter system prompt", item.data.prompt.system)
                Log.d("TestLogAdapter user prompt", item.data.prompt.user)
                Log.d("TestLogAdapter content", item.data.content)
            }

            // 设置展开/收起按钮点击事件
            btnExpandCollapse.setOnClickListener {
                item.isExpand = !item.isExpand
                notifyItemChanged(adapterPosition)
            }
        }
    }

    // Type2 ViewHolder
    inner class Type2ViewHolder(view: View) : BaseTypeViewHolder(view) {
        private val textTitle: TextView = view.findViewById(R.id.text_title)
        private val imageScreenshot: ImageView = view.findViewById(R.id.image_screenshot)
        private val textDuration: TextView? = view.findViewById(R.id.text_duration)

        override fun bind(item: ExpandItem) {
            Glide.with(itemView.context)
                .load(item.data.image1)
                .into(imageScreenshot)
            textTitle.text = item.data.title
            
            // 设置duration显示
            if (item.data.duration > 0 && textDuration != null) {
                // 格式化时间，毫秒转秒
                val seconds = item.data.duration / 1000.0
                textDuration.text = String.format("%.1fs", seconds)
                textDuration.isVisible = true
            } else if (textDuration != null) {
                textDuration.isVisible = false
            }
        }
    }

    // Type3 ViewHolder
    inner class Type3ViewHolder(view: View) : BaseTypeViewHolder(view) {
        private val textTitle: TextView = view.findViewById(R.id.text_title)
        private val imageLeft: ImageView = view.findViewById(R.id.image_left)
        private val imageRight: ImageView = view.findViewById(R.id.image_right)
        private val textContent: TextView = view.findViewById(R.id.text_content)
        private val progressBar: ProgressBar = view.findViewById(R.id.status_progress)
        private val divider: View = view.findViewById(R.id.divider)
        private val textDuration: TextView? = view.findViewById(R.id.text_duration)

        @OptIn(DelicateCoroutinesApi::class)
        override fun bind(item: ExpandItem) {
            // 绑定新 item 前先取消旧协程，防止更新到错误的 ViewHolder
            job?.cancel()
            currentItemId = item.data.id

            Glide.with(itemView.context)
                .load(item.data.image1)
                .into(imageLeft)
            Glide.with(itemView.context)
                .load(item.data.image2)
                .into(imageRight)
            textTitle.text = item.data.title
            
            // 设置duration显示
            if (item.data.duration > 0 && textDuration != null) {
                // 格式化时间，毫秒转秒
                val seconds = item.data.duration / 1000.0
                textDuration.text = String.format("%.1fs", seconds)
                textDuration.isVisible = true
            } else if (textDuration != null) {
                textDuration.isVisible = false
            }

            if (item.data.status == StreamingStatus.COMPLETED) {
                divider.isVisible = true
                progressBar.isVisible = false
                textContent.setMarkdownText(item.data.content)
            } else {
                divider.isVisible = false
                job = GlobalScope.launch(Dispatchers.IO) {
                    var lastType = ""
                    val sb = StringBuilder()
                    item.data.flow?.buffer(capacity = 1024)?.collect { data ->
                        // 检查 ViewHolder 是否还在显示同一个 item，防止乱序
                        if (currentItemId != item.data.id) {
                            cancel()
                        }

                        val json = JSONObject(data)
                        if (json.has("_sse_error")) {
                            withContext(Dispatchers.Main) {
                                val hint = "连接出现错误，请等待重试"
                                val redSpan = ForegroundColorSpan(ContextCompat.getColor(textContent.context, android.R.color.holo_red_light))
                                val str = sb.append(hint).toString()
                                val spannableString = SpannableString(str)
                                spannableString.setSpan(redSpan, str.length - hint.length, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                textContent.text = spannableString
                                progressBar.isVisible = true
                            }
                            return@collect
                        }
                        if (json.has("requestStyle") && json.getString("requestStyle") == "block") {
                            sb.setLength(0)
                            val childJson = JSONObject(json.getString("content"))
                            sb.append(childJson.getString("answer"))
                            withContext(Dispatchers.Main) {
                                progressBar.isVisible = false
                                divider.isVisible = true
                                cancel()
                            }
                        }
                        if (json.has("event")) {
                            if (lastType == "message" && json.getString("event") != "message") {
                                withContext(Dispatchers.Main) {
                                    progressBar.isVisible = false
                                    divider.isVisible = true
                                    cancel()
                                }
                            }
                            if (json.getString("event") == "message") {
                                val answer = json.getString("answer")
                                sb.append(answer)
                                withContext(Dispatchers.Main) {
                                    progressBar.isVisible = true
                                    textContent.setMarkdownText(sb.toString())
                                }
                            }
                            lastType = json.getString("event")
                        }

                    }

                }

            }
            textContent.setOnClickListener {
                Log.d("TestLogAdapter system prompt", item.data.prompt.system)
                Log.d("TestLogAdapter user prompt", item.data.prompt.user)
                Log.d("TestLogAdapter content", item.data.content)
            }
        }
    }

    abstract inner class BaseTypeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var job: Job? = null
        var currentItemId: String? = null
        private var isDestroyed = false

        abstract fun bind(item: ExpandItem)

        fun destroy() {
            if (isDestroyed) return
            job?.cancel()
            job = null
            currentItemId = null
            isDestroyed = true
        }

        fun reset() {
            isDestroyed = false
            job = null
        }
    }

}

data class ExpandItem(
    val data: TestLogItem,
    var isExpand: Boolean,
    val showCollapseBtn: Boolean = true
)