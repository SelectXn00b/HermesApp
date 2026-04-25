package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import android.content.Context
import android.database.Cursor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqlViewerViewModel(private val context: Context) : ViewModel() {
    data class QueryResult(
        val columns: List<String>,
        val rows: List<List<String>>
    )

    data class State(
        val isRunning: Boolean = false,
        val result: QueryResult? = null,
        val error: String? = null,
        val message: String? = null,
        val affectedRows: Int? = null,
        val lastBaseQuery: String = "",
        val canPaginate: Boolean = false,
        val currentOffset: Int = 0,
        val pageSize: Int = 50,
        val lastFetchCount: Int = 0
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val database by lazy { AppDatabase.getDatabase(context).openHelper.writableDatabase }

    fun runQuery(
        rawSql: String,
        pageSize: Int,
        offset: Int,
        canPaginate: Boolean,
        append: Boolean
    ) {
        val trimmed = sanitizeSql(rawSql)
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(
                error = context.getString(R.string.sql_viewer_empty_sql),
                message = null,
                affectedRows = null
            )
            return
        }
        val effectiveSql =
            if (canPaginate && isQueryStatement(trimmed)) {
                buildPaginatedQuery(trimmed, pageSize, offset)
            } else {
                trimmed
            }

        _state.value = _state.value.copy(isRunning = true, error = null, message = null, affectedRows = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isQueryStatement(trimmed)) {
                    val queryResult = executeQuery(effectiveSql)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.let { current ->
                            val mergedRows =
                                if (append && current.result != null) {
                                    current.result.rows + queryResult.rows
                                } else {
                                    queryResult.rows
                                }
                            current.copy(
                                isRunning = false,
                                result = queryResult.copy(rows = mergedRows),
                                lastBaseQuery = trimmed,
                                canPaginate = canPaginate,
                                currentOffset = offset,
                                pageSize = pageSize,
                                lastFetchCount = queryResult.rows.size
                            )
                        }
                    }
                } else {
                    database.execSQL(trimmed)
                    val affected = queryChanges()
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            isRunning = false,
                            result = null,
                            affectedRows = affected,
                            message = "OK",
                            lastBaseQuery = trimmed,
                            canPaginate = false,
                            currentOffset = 0,
                            pageSize = pageSize,
                            lastFetchCount = 0
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isRunning = false,
                        error = e.message ?: "Unknown error",
                        message = null,
                        affectedRows = null
                    )
                }
            }
        }
    }

    private fun executeQuery(sql: String): QueryResult {
        database.query(sql).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<List<String>>()
            while (cursor.moveToNext()) {
                val row = buildList {
                    for (index in columns.indices) {
                        add(readCell(cursor, index))
                    }
                }
                rows.add(row)
            }
            return QueryResult(columns = columns, rows = rows)
        }
    }

    private fun readCell(cursor: Cursor, index: Int): String {
        return when (cursor.getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
            android.database.Cursor.FIELD_TYPE_BLOB -> {
                val blob = cursor.getBlob(index)
                "BLOB(${blob.size})"
            }
            else -> cursor.getString(index) ?: ""
        }
    }

    private fun queryChanges(): Int? {
        return try {
            database.query("SELECT changes()")?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isQueryStatement(sql: String): Boolean {
        val normalized = sql.trimStart().lowercase()
        return normalized.startsWith("select") || normalized.startsWith("with") || normalized.startsWith("pragma")
    }

    private fun buildPaginatedQuery(baseQuery: String, limit: Int, offset: Int): String {
        val trimmed = sanitizeSql(baseQuery)
        return "$trimmed LIMIT $limit OFFSET $offset"
    }

    private fun sanitizeSql(sql: String): String {
        return sql.trim().removeSuffix(";")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SqlViewerViewModel::class.java)) {
                return SqlViewerViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
