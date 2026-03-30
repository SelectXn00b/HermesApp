package com.xiaomo.androidforclaw.security

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/security/dangerous-config-flags.ts
 *   (collectEnabledInsecureOrDangerousFlags)
 *
 * AndroidForClaw adaptation: detect dangerous configuration flags.
 */

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * DangerousConfigFlags — Detect dangerous configuration.
 * Aligned with OpenClaw collectEnabledInsecureOrDangerousFlags.
 */
object DangerousConfigFlags {

    /**
     * Collect enabled insecure or dangerous flags from configuration.
     * Returns list of flag paths that are dangerously enabled.
     * Aligned with OpenClaw collectEnabledInsecureOrDangerousFlags.
     */
    fun check(config: OpenClawConfig): List<String> {
        val flags = mutableListOf<String>()

        // Gateway controlUi flags (aligned with OpenClaw)
        config.gateway.controlUi?.let { ui ->
            if (ui.allowInsecureAuth == true) {
                flags.add("gateway.controlUi.allowInsecureAuth=true")
            }
            if (ui.dangerouslyAllowHostHeaderOriginFallback == true) {
                flags.add("gateway.controlUi.dangerouslyAllowHostHeaderOriginFallback=true")
            }
            if (ui.dangerouslyDisableDeviceAuth == true) {
                flags.add("gateway.controlUi.dangerouslyDisableDeviceAuth=true")
            }
        }

        // Hooks gmail unsafe content
        config.hooks?.gmail?.let { gmail ->
            if (gmail.allowUnsafeExternalContent == true) {
                flags.add("hooks.gmail.allowUnsafeExternalContent=true")
            }
        }

        // Hooks mappings unsafe content
        config.hooks?.mappings?.forEachIndexed { index, mapping ->
            if (mapping.allowUnsafeExternalContent == true) {
                flags.add("hooks.mappings[$index].allowUnsafeExternalContent=true")
            }
        }

        // Tools exec applyPatch workspaceOnly explicitly false
        config.tools?.exec?.applyPatch?.let { ap ->
            if (ap.workspaceOnly == false) {
                flags.add("tools.exec.applyPatch.workspaceOnly=false")
            }
        }

        // Android-specific: overly permissive channel policies
        val channels = config.channels
        channels?.feishu?.let { feishu ->
            if (feishu.enabled && feishu.dmPolicy == "open" && feishu.groupPolicy == "open") {
                flags.add("channels.feishu: both DM and group policies are 'open'")
            }
        }
        channels?.discord?.let { discord ->
            if (discord.enabled && discord.dm?.policy == "open" && discord.groupPolicy == "open") {
                flags.add("channels.discord: both DM and group policies are 'open'")
            }
        }

        return flags
    }
}
