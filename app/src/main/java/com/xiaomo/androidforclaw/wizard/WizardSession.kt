package com.xiaomo.androidforclaw.wizard

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/session.ts
 *
 * Stateful wizard session that drives a step-by-step prompt/answer flow.
 * The runner (setup wizard logic) pushes steps; the UI layer calls [next]
 * to get them and [answer] to provide responses.
 *
 * Aligned 1:1 with TS WizardSession + WizardSessionPrompter.
 * Android adaptation: uses CompletableDeferred instead of JS Deferred/Promise.
 */

// ---------------------------------------------------------------------------
// WizardSessionPrompter — internal prompter backed by the session
// ---------------------------------------------------------------------------

private class WizardSessionPrompter(
    private val session: WizardSession,
) : WizardPrompter {

    override suspend fun intro(title: String) {
        prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.NOTE,
                title = title,
                message = "",
                executor = "client",
            )
        )
    }

    override suspend fun outro(message: String) {
        prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.NOTE,
                title = "Done",
                message = message,
                executor = "client",
            )
        )
    }

    override suspend fun note(message: String, title: String?) {
        prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.NOTE,
                title = title,
                message = message,
                executor = "client",
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> select(params: WizardSelectParams<T>): T {
        val res = prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.SELECT,
                message = params.message,
                options = params.options.map { opt ->
                    WizardStepOption(value = opt.value, label = opt.label, hint = opt.hint)
                },
                initialValue = params.initialValue,
                executor = "client",
            )
        )
        return res as T
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> multiselect(params: WizardMultiSelectParams<T>): List<T> {
        val res = prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.MULTISELECT,
                message = params.message,
                options = params.options.map { opt ->
                    WizardStepOption(value = opt.value, label = opt.label, hint = opt.hint)
                },
                initialValue = params.initialValues,
                executor = "client",
            )
        )
        return if (res is List<*>) res as List<T> else emptyList()
    }

    override suspend fun text(params: WizardTextParams): String {
        val res = prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.TEXT,
                message = params.message,
                initialValue = params.initialValue,
                placeholder = params.placeholder,
                executor = "client",
            )
        )
        val value = when (res) {
            null -> ""
            is String -> res
            is Number, is Boolean -> res.toString()
            else -> ""
        }
        val error = params.validate?.invoke(value)
        if (error != null) {
            throw IllegalArgumentException(error)
        }
        return value
    }

    override suspend fun confirm(params: WizardConfirmParams): Boolean {
        val res = prompt(
            WizardStep(
                id = UUID.randomUUID().toString(),
                type = WizardStepType.CONFIRM,
                message = params.message,
                initialValue = params.initialValue,
                executor = "client",
            )
        )
        return res as? Boolean ?: false
    }

    override fun progress(label: String): WizardProgress {
        // On Android, progress is rendered via the UI layer; session prompter
        // provides a no-op implementation aligned with TS.
        return object : WizardProgress {
            override fun update(message: String) {}
            override fun stop(message: String?) {}
        }
    }

    private suspend fun prompt(step: WizardStep): Any? {
        return session.awaitAnswer(step)
    }
}

// ---------------------------------------------------------------------------
// WizardSession — aligned with TS WizardSession class
// ---------------------------------------------------------------------------

class WizardSession(
    private val runner: suspend (prompter: WizardPrompter) -> Unit,
) {
    private var currentStep: WizardStep? = null
    private var stepDeferred: CompletableDeferred<WizardStep?>? = null
    private var pendingTerminalResolution = false
    private val answerDeferred = ConcurrentHashMap<String, CompletableDeferred<Any?>>()
    @Volatile
    private var status: WizardSessionStatus = WizardSessionStatus.RUNNING
    @Volatile
    private var error: String? = null

    init {
        val prompter = WizardSessionPrompter(this)
        CoroutineScope(Dispatchers.Default).launch {
            run(prompter)
        }
    }

    /**
     * Get the next step to present to the user.
     * If done, returns [WizardNextResult.done] = true.
     */
    suspend fun next(): WizardNextResult {
        currentStep?.let { step ->
            return WizardNextResult(done = false, step = step, status = status)
        }
        if (pendingTerminalResolution) {
            pendingTerminalResolution = false
            return WizardNextResult(done = true, status = status, error = error)
        }
        if (status != WizardSessionStatus.RUNNING) {
            return WizardNextResult(done = true, status = status, error = error)
        }
        val deferred = stepDeferred ?: CompletableDeferred<WizardStep?>().also {
            stepDeferred = it
        }
        val step = deferred.await()
        return if (step != null) {
            WizardNextResult(done = false, step = step, status = status)
        } else {
            WizardNextResult(done = true, status = status, error = error)
        }
    }

    /**
     * Provide the answer for a step.
     */
    fun answer(stepId: String, value: Any?) {
        val deferred = answerDeferred.remove(stepId)
            ?: throw IllegalStateException("wizard: no pending step")
        currentStep = null
        deferred.complete(value)
    }

    /**
     * Cancel the wizard session.
     */
    fun cancel() {
        if (status != WizardSessionStatus.RUNNING) return
        status = WizardSessionStatus.CANCELLED
        error = "cancelled"
        currentStep = null
        for ((_, deferred) in answerDeferred) {
            deferred.completeExceptionally(WizardCancelledError())
        }
        answerDeferred.clear()
        resolveStep(null)
    }

    /**
     * Push a step (used internally by the prompter).
     */
    internal fun pushStep(step: WizardStep) {
        currentStep = step
        resolveStep(step)
    }

    private suspend fun run(prompter: WizardPrompter) {
        try {
            runner(prompter)
            status = WizardSessionStatus.DONE
        } catch (err: WizardCancelledError) {
            status = WizardSessionStatus.CANCELLED
            error = err.message
        } catch (err: Exception) {
            status = WizardSessionStatus.ERROR
            error = err.toString()
        } finally {
            resolveStep(null)
        }
    }

    /**
     * Suspend until the UI provides an answer for the given step.
     */
    internal suspend fun awaitAnswer(step: WizardStep): Any? {
        if (status != WizardSessionStatus.RUNNING) {
            throw IllegalStateException("wizard: session not running")
        }
        pushStep(step)
        val deferred = CompletableDeferred<Any?>()
        answerDeferred[step.id] = deferred
        return deferred.await()
    }

    private fun resolveStep(step: WizardStep?) {
        val deferred = stepDeferred
        if (deferred == null) {
            if (step == null) {
                pendingTerminalResolution = true
            }
            return
        }
        stepDeferred = null
        deferred.complete(step)
    }

    fun getStatus(): WizardSessionStatus = status

    fun getError(): String? = error
}
