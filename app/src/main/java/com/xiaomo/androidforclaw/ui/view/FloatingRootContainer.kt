/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import com.draco.ladb.R

/**
 * Floating window root layout container
 * Uses custom Button Tabs to switch View pages (to avoid Material Design component theme issues in Service)
 */
class FloatingRootContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var btnTabChatHistory: Button
    private lateinit var btnTabTodos: Button
    private lateinit var btnTabBugList: Button
    private lateinit var contentContainer: LinearLayout

    // Page Views (ChatHistoryView and ChatLogView removed - old architecture)
    private var todoListView: TodosView? = null

    // Current displayed page index
    private var currentPageIndex = 0

    init {
        orientation = VERTICAL
        setupViews()
        setupTabListener()
        showPage(0) // Display first page by default
    }

    private fun setupViews() {
        // Load layout
        LayoutInflater.from(context).inflate(R.layout.layout_floating_root, this, true)

        btnTabChatHistory = findViewById(R.id.btnTabChatHistory)
        btnTabTodos = findViewById(R.id.btnTabTodos)
        btnTabBugList = findViewById(R.id.btnTabBugList)
        contentContainer = findViewById(R.id.contentContainer)

        // Create page Views
        createViews()
    }

    private fun createViews() {
        // Create TodosView
        todoListView = TodosView(context)
    }

    private fun setupTabListener() {
        btnTabChatHistory.setOnClickListener {
            showPage(0)
        }

        btnTabTodos.setOnClickListener {
            showPage(1)
        }

        btnTabBugList.setOnClickListener {
            showPage(2)
        }
    }

    private fun showPage(index: Int) {
        currentPageIndex = index
        contentContainer.removeAllViews()

        // Update Tab button style
        updateTabAppearance(index)

        // Show corresponding page
        when (index) {
            0 -> {
                // ChatHistoryView and ChatLogView removed (old architecture), not displayed for now
            }

            1 -> {
                todoListView?.let { contentContainer.addView(it) }
            }

            2 -> {
                // BugListView removed
            }
        }
    }

    private fun updateTabAppearance(selectedIndex: Int) {
        // Reset all buttons to unselected state
        btnTabChatHistory.setBackgroundResource(R.drawable.tab_unselected_background)
        btnTabChatHistory.setTextColor(context.getColor(android.R.color.white))
        btnTabTodos.setBackgroundResource(R.drawable.tab_unselected_background)
        btnTabTodos.setTextColor(context.getColor(android.R.color.white))
        btnTabBugList.setBackgroundResource(R.drawable.tab_unselected_background)
        btnTabBugList.setTextColor(context.getColor(android.R.color.white))

        // Set selected button style
        when (selectedIndex) {
            0 -> {
                btnTabChatHistory.setBackgroundResource(R.drawable.tab_selected_background)
                btnTabChatHistory.setTextColor(context.getColor(R.color.primary_color))
            }

            1 -> {
                btnTabTodos.setBackgroundResource(R.drawable.tab_selected_background)
                btnTabTodos.setTextColor(context.getColor(R.color.primary_color))
            }

            2 -> {
                btnTabBugList.setBackgroundResource(R.drawable.tab_selected_background)
                btnTabBugList.setTextColor(context.getColor(R.color.primary_color))
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        todoListView?.cleanup()
    }
} 