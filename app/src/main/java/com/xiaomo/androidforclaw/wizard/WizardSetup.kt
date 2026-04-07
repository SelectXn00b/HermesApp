package com.xiaomo.androidforclaw.wizard

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: wizard
 * Source: OpenClaw/src/wizard/setup.ts
 *
 * Orchestrates the setup wizard flow on Android.
 * Android adaptation: no CLI-specific imports (onboard-helpers, daemon, TUI);
 * config read/write goes through ConfigLoader.
 * The high-level flow mirrors TS runSetupWizard logic.
 */
object WizardSetup {

    private const val DEFAULT_GATEWAY_PORT = 19789

    /**
     * Resolve the quickstart gateway defaults from an existing config.
     * Aligned with TS anonymous IIFE in runSetupWizard.
     */
    fun resolveQuickstartGatewayDefaults(config: OpenClawConfig): QuickstartGatewayDefaults {
        val gw = config.gateway
        val auth = gw.auth

        val hasExisting = gw.port != DEFAULT_GATEWAY_PORT ||
            gw.bind != "loopback" ||
            auth?.mode != null ||
            auth?.token != null

        val bind = GatewayBindMode.fromString(gw.bind)

        val authMode = when {
            auth?.mode == "token" -> GatewayAuthChoice.TOKEN
            auth?.mode == "password" -> GatewayAuthChoice.PASSWORD
            auth?.token != null -> GatewayAuthChoice.TOKEN
            else -> GatewayAuthChoice.TOKEN
        }

        return QuickstartGatewayDefaults(
            hasExisting = hasExisting,
            port = gw.port,
            bind = bind,
            authMode = authMode,
            tailscaleMode = TailscaleMode.OFF,
            token = auth?.token,
            password = null,
            customBindHost = null,
            tailscaleResetOnExit = false,
        )
    }

    /**
     * Run the main setup wizard.
     * Aligned with TS runSetupWizard flow: intro -> risk acknowledgement ->
     * flow selection -> gateway config -> auth -> channels -> finalize.
     *
     * On Android, many substeps are simplified (no CLI daemon install, no
     * shell completion, no TUI launch).
     */
    suspend fun runSetupWizard(
        config: OpenClawConfig,
        prompter: WizardPrompter,
        options: SetupWizardOptions = SetupWizardOptions(),
    ): SetupWizardResult {
        prompter.intro("OpenClaw setup")

        // Risk acknowledgement — aligned with requireRiskAcknowledgement
        if (!options.acceptRisk) {
            prompter.note(SECURITY_NOTICE, "Security")
            val ok = prompter.confirm(
                WizardConfirmParams(
                    message = "I understand this is personal-by-default and shared/multi-user use requires lock-down. Continue?",
                    initialValue = false,
                )
            )
            if (!ok) {
                throw WizardCancelledError("risk not accepted")
            }
        }

        // Flow selection
        val flow = options.explicitFlow ?: prompter.select(
            WizardSelectParams(
                message = "Setup mode",
                options = listOf(
                    WizardSelectOption("quickstart", "QuickStart", "Configure details later."),
                    WizardSelectOption("advanced", "Manual", "Configure port, network, and auth options."),
                ),
                initialValue = "quickstart",
            )
        ).let { WizardFlow.fromString(it) ?: WizardFlow.QUICKSTART }

        val quickstartGateway = resolveQuickstartGatewayDefaults(config)

        // Display quickstart summary
        if (flow == WizardFlow.QUICKSTART) {
            val lines = if (quickstartGateway.hasExisting) {
                buildList {
                    add("Keeping your current gateway settings:")
                    add("Gateway port: ${quickstartGateway.port}")
                    add("Gateway bind: ${formatBind(quickstartGateway.bind)}")
                    if (quickstartGateway.bind == GatewayBindMode.CUSTOM && quickstartGateway.customBindHost != null) {
                        add("Gateway custom IP: ${quickstartGateway.customBindHost}")
                    }
                    add("Gateway auth: ${formatAuth(quickstartGateway.authMode)}")
                    add("Tailscale exposure: ${formatTailscale(quickstartGateway.tailscaleMode)}")
                    add("Direct to chat channels.")
                }
            } else {
                listOf(
                    "Gateway port: ${quickstartGateway.port}",
                    "Gateway bind: Loopback (127.0.0.1)",
                    "Gateway auth: Token (default)",
                    "Tailscale exposure: Off",
                    "Direct to chat channels.",
                )
            }
            prompter.note(lines.joinToString("\n"), "QuickStart")
        }

        // Gateway configuration
        val settings = configureGateway(flow, quickstartGateway, prompter)

        prompter.outro("Onboarding complete.")

        return SetupWizardResult(
            flow = flow,
            settings = settings,
        )
    }

    /**
     * Configure gateway settings — aligned with setup.gateway-config.ts.
     */
    private suspend fun configureGateway(
        flow: WizardFlow,
        quickstart: QuickstartGatewayDefaults,
        prompter: WizardPrompter,
    ): GatewayWizardSettings {
        val port = if (flow == WizardFlow.QUICKSTART) {
            quickstart.port
        } else {
            prompter.text(
                WizardTextParams(
                    message = "Gateway port",
                    initialValue = quickstart.port.toString(),
                    validate = { v ->
                        if (v.toIntOrNull()?.let { it > 0 && it < 65536 } == true) null
                        else "Invalid port"
                    },
                )
            ).toInt()
        }

        var bind = if (flow == WizardFlow.QUICKSTART) {
            quickstart.bind
        } else {
            GatewayBindMode.fromString(
                prompter.select(
                    WizardSelectParams(
                        message = "Gateway bind",
                        options = listOf(
                            WizardSelectOption("loopback", "Loopback (127.0.0.1)"),
                            WizardSelectOption("lan", "LAN (0.0.0.0)"),
                            WizardSelectOption("tailnet", "Tailnet (Tailscale IP)"),
                            WizardSelectOption("auto", "Auto (Loopback -> LAN)"),
                            WizardSelectOption("custom", "Custom IP"),
                        ),
                    )
                )
            )
        }

        var customBindHost = quickstart.customBindHost
        if (bind == GatewayBindMode.CUSTOM) {
            val needsPrompt = flow != WizardFlow.QUICKSTART || customBindHost == null
            if (needsPrompt) {
                customBindHost = prompter.text(
                    WizardTextParams(
                        message = "Custom IP address",
                        placeholder = "192.168.1.100",
                        initialValue = customBindHost ?: "",
                    )
                ).trim()
            }
        }

        var authMode = if (flow == WizardFlow.QUICKSTART) {
            quickstart.authMode
        } else {
            GatewayAuthChoice.fromString(
                prompter.select(
                    WizardSelectParams(
                        message = "Gateway auth",
                        options = listOf(
                            WizardSelectOption("token", "Token", "Recommended default (local + remote)"),
                            WizardSelectOption("password", "Password"),
                        ),
                        initialValue = "token",
                    )
                )
            )
        }

        val tailscaleMode = if (flow == WizardFlow.QUICKSTART) {
            quickstart.tailscaleMode
        } else {
            TailscaleMode.fromString(
                prompter.select(
                    WizardSelectParams(
                        message = "Tailscale exposure",
                        options = listOf(
                            WizardSelectOption("off", "Off"),
                            WizardSelectOption("serve", "Serve"),
                            WizardSelectOption("funnel", "Funnel"),
                        ),
                    )
                )
            )
        }

        // Constraints: tailscale wants loopback, funnel wants password
        if (tailscaleMode != TailscaleMode.OFF && bind != GatewayBindMode.LOOPBACK) {
            prompter.note("Tailscale requires bind=loopback. Adjusting bind to loopback.", "Note")
            bind = GatewayBindMode.LOOPBACK
            customBindHost = null
        }
        if (tailscaleMode == TailscaleMode.FUNNEL && authMode != GatewayAuthChoice.PASSWORD) {
            prompter.note("Tailscale funnel requires password auth.", "Note")
            authMode = GatewayAuthChoice.PASSWORD
        }

        return GatewayWizardSettings(
            port = port,
            bind = bind,
            customBindHost = if (bind == GatewayBindMode.CUSTOM) customBindHost else null,
            authMode = authMode,
            gatewayToken = null, // Token generation handled by config layer
            tailscaleMode = tailscaleMode,
            tailscaleResetOnExit = false,
        )
    }

    /**
     * Check if setup wizard is required.
     */
    fun isSetupRequired(config: OpenClawConfig): Boolean {
        return config.models == null
    }

    // --- Format helpers aligned with TS ---

    private fun formatBind(value: GatewayBindMode): String = when (value) {
        GatewayBindMode.LOOPBACK -> "Loopback (127.0.0.1)"
        GatewayBindMode.LAN -> "LAN"
        GatewayBindMode.CUSTOM -> "Custom IP"
        GatewayBindMode.TAILNET -> "Tailnet (Tailscale IP)"
        GatewayBindMode.AUTO -> "Auto"
    }

    private fun formatAuth(value: GatewayAuthChoice): String = when (value) {
        GatewayAuthChoice.TOKEN -> "Token (default)"
        GatewayAuthChoice.PASSWORD -> "Password"
    }

    private fun formatTailscale(value: TailscaleMode): String = when (value) {
        TailscaleMode.OFF -> "Off"
        TailscaleMode.SERVE -> "Serve"
        TailscaleMode.FUNNEL -> "Funnel"
    }

    // ---------------------------------------------------------------------------
    // Security notice — aligned with TS requireRiskAcknowledgement
    // ---------------------------------------------------------------------------

    private val SECURITY_NOTICE = """
        Security warning — please read.

        OpenClaw is a hobby project and still in beta. Expect sharp edges.
        By default, OpenClaw is a personal agent: one trusted operator boundary.
        This bot can read files and run actions if tools are enabled.
        A bad prompt can trick it into doing unsafe things.

        Recommended baseline:
        - Pairing/allowlists + mention gating.
        - Multi-user/shared inbox: split trust boundaries.
        - Sandbox + least-privilege tools.
        - Keep secrets out of the agent's reachable filesystem.
        - Use the strongest available model for any bot with tools.
    """.trimIndent()
}

// ---------------------------------------------------------------------------
// SetupWizardOptions — aligned with OnboardOptions subset
// ---------------------------------------------------------------------------

data class SetupWizardOptions(
    val acceptRisk: Boolean = false,
    val explicitFlow: WizardFlow? = null,
    val skipChannels: Boolean = false,
    val skipHealth: Boolean = false,
    val skipSearch: Boolean = false,
    val skipSkills: Boolean = false,
    val skipUi: Boolean = false,
)

// ---------------------------------------------------------------------------
// SetupWizardResult
// ---------------------------------------------------------------------------

data class SetupWizardResult(
    val flow: WizardFlow,
    val settings: GatewayWizardSettings,
)
