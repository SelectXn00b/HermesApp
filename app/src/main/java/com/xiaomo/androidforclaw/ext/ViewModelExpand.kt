/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: Kotlin extensions.
 */
package com.xiaomo.androidforclaw.ext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ViewModel.simpleSafeLaunch(
    block: suspend CoroutineScope.() -> Unit,
    onError: ((e: Exception) -> Unit)? = null,
): Job {
    return viewModelScope.launch(Dispatchers.IO) {
        try {
            block.invoke(this)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError?.invoke(e)
            }
        }
    }
}

fun CoroutineScope.simpleSafeLaunch(
    block: suspend CoroutineScope.() -> Unit,
    onError: ((e: Exception) -> Unit)? = null,
): Job {
    return this.launch(Dispatchers.IO) {
        try {
            block.invoke(this)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError?.invoke(e)
            }
        }
    }
}