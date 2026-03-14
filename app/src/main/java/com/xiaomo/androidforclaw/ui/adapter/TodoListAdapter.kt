/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
// TODO: OperationStep/StepStatus 已删除（旧架构概念）
// import com.xiaomo.androidforclaw.agent.OperationStep
// import com.xiaomo.androidforclaw.agent.StepStatus
import com.draco.ladb.R

/**
 * Todos适配器
 * TODO: 旧架构 - OperationStep 已删除，nanobot 架构中没有预生成的步骤列表
 * 新架构使用动态 agent loop，不需要预先定义 todos
 */
@Deprecated("OperationStep no longer exists in nanobot architecture")
class TodosAdapter : RecyclerView.Adapter<TodosAdapter.TodoViewHolder>() {

    private var steps: List<String> = emptyList()  // Changed from OperationStep to String

    fun updateSteps(newSteps: List<String>) {
        steps = newSteps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo_step, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(steps[position], position)
    }

    override fun getItemCount(): Int = steps.size

    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stepContainer: View = itemView.findViewById(R.id.stepContainer)
        private val stepIndex: TextView = itemView.findViewById(R.id.stepIndex)
        private val stepDescription: TextView = itemView.findViewById(R.id.stepDescription)
        private val stepStatus: TextView = itemView.findViewById(R.id.stepStatus)

        fun bind(stepText: String, position: Int) {
            stepIndex.text = "步骤 ${position + 1}"
            stepDescription.text = stepText
            stepStatus.text = ""  // No status in new architecture
        }
    }
} 