package com.xiaomo.androidforclaw.wizard

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/setup.types.ts + prompts.ts
 *
 * Aligned 1:1 with TS wizard data model.
 * Android adaptation: drives Compose UI instead of CLI clack prompts.
 */

// ---------------------------------------------------------------------------
// WizardFlow — from setup.types.ts
// ---------------------------------------------------------------------------

enum class WizardFlow(val value: String) {
    QUICKSTART("quickstart"),
    ADVANCED("advanced");

    companion object {
        fun fromString(raw: String?): WizardFlow? = when (raw?.trim()?.lowercase()) {
            "quickstart" -> QUICKSTART
            "advanced", "manual" -> ADVANCED
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// GatewayAuthChoice — subset from commands/onboard-types.ts
// ---------------------------------------------------------------------------

enum class GatewayAuthChoice(val value: String) {
    TOKEN("token"),
    PASSWORD("password");

    companion object {
        fun fromString(raw: String?): GatewayAuthChoice = when (raw?.trim()?.lowercase()) {
            "password" -> PASSWORD
            else -> TOKEN
        }
    }
}

// ---------------------------------------------------------------------------
// GatewayBindMode — from config.ts
// ---------------------------------------------------------------------------

enum class GatewayBindMode(val value: String) {
    LOOPBACK("loopback"),
    LAN("lan"),
    AUTO("auto"),
    CUSTOM("custom"),
    TAILNET("tailnet");

    companion object {
        fun fromString(raw: String?): GatewayBindMode = when (raw?.trim()?.lowercase()) {
            "lan" -> LAN
            "auto" -> AUTO
            "custom" -> CUSTOM
            "tailnet" -> TAILNET
            else -> LOOPBACK
        }
    }
}

// ---------------------------------------------------------------------------
// TailscaleMode — from config.ts
// ---------------------------------------------------------------------------

enum class TailscaleMode(val value: String) {
    OFF("off"),
    SERVE("serve"),
    FUNNEL("funnel");

    companion object {
        fun fromString(raw: String?): TailscaleMode = when (raw?.trim()?.lowercase()) {
            "serve" -> SERVE
            "funnel" -> FUNNEL
            else -> OFF
        }
    }
}

// ---------------------------------------------------------------------------
// QuickstartGatewayDefaults — from setup.types.ts
// ---------------------------------------------------------------------------

data class QuickstartGatewayDefaults(
    val hasExisting: Boolean = false,
    val port: Int = 19789,
    val bind: GatewayBindMode = GatewayBindMode.LOOPBACK,
    val authMode: GatewayAuthChoice = GatewayAuthChoice.TOKEN,
    val tailscaleMode: TailscaleMode = TailscaleMode.OFF,
    val token: String? = null,
    val password: String? = null,
    val customBindHost: String? = null,
    val tailscaleResetOnExit: Boolean = false,
)

// ---------------------------------------------------------------------------
// GatewayWizardSettings — from setup.types.ts
// ---------------------------------------------------------------------------

data class GatewayWizardSettings(
    val port: Int = 19789,
    val bind: GatewayBindMode = GatewayBindMode.LOOPBACK,
    val customBindHost: String? = null,
    val authMode: GatewayAuthChoice = GatewayAuthChoice.TOKEN,
    val gatewayToken: String? = null,
    val tailscaleMode: TailscaleMode = TailscaleMode.OFF,
    val tailscaleResetOnExit: Boolean = false,
)

// ---------------------------------------------------------------------------
// WizardStepType — from session.ts WizardStep.type
// ---------------------------------------------------------------------------

enum class WizardStepType(val value: String) {
    NOTE("note"),
    SELECT("select"),
    TEXT("text"),
    CONFIRM("confirm"),
    MULTISELECT("multiselect"),
    PROGRESS("progress"),
    ACTION("action");

    companion object {
        fun fromString(raw: String?): WizardStepType? = entries.find {
            it.value.equals(raw?.trim(), ignoreCase = true)
        }
    }
}

// ---------------------------------------------------------------------------
// WizardStepOption — from session.ts
// ---------------------------------------------------------------------------

data class WizardStepOption(
    val value: Any?,
    val label: String,
    val hint: String? = null,
)

// ---------------------------------------------------------------------------
// WizardStep — from session.ts
// ---------------------------------------------------------------------------

data class WizardStep(
    val id: String,
    val type: WizardStepType,
    val title: String? = null,
    val message: String? = null,
    val options: List<WizardStepOption>? = null,
    val initialValue: Any? = null,
    val placeholder: String? = null,
    val sensitive: Boolean = false,
    val executor: String? = null, // "gateway" | "client"
)

// ---------------------------------------------------------------------------
// WizardSessionStatus — from session.ts
// ---------------------------------------------------------------------------

enum class WizardSessionStatus(val value: String) {
    RUNNING("running"),
    DONE("done"),
    CANCELLED("cancelled"),
    ERROR("error");
}

// ---------------------------------------------------------------------------
// WizardNextResult — from session.ts
// ---------------------------------------------------------------------------

data class WizardNextResult(
    val done: Boolean,
    val step: WizardStep? = null,
    val status: WizardSessionStatus,
    val error: String? = null,
)

// ---------------------------------------------------------------------------
// WizardCancelledError — from prompts.ts
// ---------------------------------------------------------------------------

class WizardCancelledError(
    message: String = "wizard cancelled",
) : Exception(message)

// ---------------------------------------------------------------------------
// WizardSelectOption / WizardSelectParams — from prompts.ts
// ---------------------------------------------------------------------------

data class WizardSelectOption<T>(
    val value: T,
    val label: String,
    val hint: String? = null,
)

data class WizardSelectParams<T>(
    val message: String,
    val options: List<WizardSelectOption<T>>,
    val initialValue: T? = null,
)

data class WizardMultiSelectParams<T>(
    val message: String,
    val options: List<WizardSelectOption<T>>,
    val initialValues: List<T>? = null,
    val searchable: Boolean = false,
)

data class WizardTextParams(
    val message: String,
    val initialValue: String? = null,
    val placeholder: String? = null,
    val validate: ((String) -> String?)? = null,
)

data class WizardConfirmParams(
    val message: String,
    val initialValue: Boolean? = null,
)

// ---------------------------------------------------------------------------
// WizardProgress — from prompts.ts
// ---------------------------------------------------------------------------

interface WizardProgress {
    fun update(message: String)
    fun stop(message: String? = null)
}

// ---------------------------------------------------------------------------
// WizardPrompter — from prompts.ts
// Abstraction layer: Android implementation uses Compose dialogs / ViewModel.
// ---------------------------------------------------------------------------

interface WizardPrompter {
    suspend fun intro(title: String)
    suspend fun outro(message: String)
    suspend fun note(message: String, title: String? = null)
    suspend fun <T> select(params: WizardSelectParams<T>): T
    suspend fun <T> multiselect(params: WizardMultiSelectParams<T>): List<T>
    suspend fun text(params: WizardTextParams): String
    suspend fun confirm(params: WizardConfirmParams): Boolean
    fun progress(label: String): WizardProgress
}
