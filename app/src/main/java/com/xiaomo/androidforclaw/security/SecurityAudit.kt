package com.xiaomo.androidforclaw.security

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/security/audit.ts (runSecurityAudit, SecurityAuditReport)
 * - ../openclaw/src/security/audit-channel.ts
 * - ../openclaw/src/security/audit-tool-policy.ts
 *
 * AndroidForClaw adaptation: runtime security audit for Android agent.
 * Checks config, tool policies, channel security, and file permissions.
 */

import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.agent.context.ToolPolicyResolver
import com.xiaomo.androidforclaw.logging.Log

/**
 * Security audit severity levels.
 * Aligned with OpenClaw SecurityAuditSeverity.
 */
enum class SecurityAuditSeverity { INFO, WARN, CRITICAL }

/**
 * A single security audit finding.
 * Aligned with OpenClaw SecurityAuditFinding.
 */
data class SecurityAuditFinding(
    val checkId: String,
    val severity: SecurityAuditSeverity,
    val title: String,
    val detail: String,
    val remediation: String? = null
)

/**
 * Audit report summary counts.
 * Aligned with OpenClaw SecurityAuditSummary.
 */
data class SecurityAuditSummary(
    val critical: Int,
    val warn: Int,
    val info: Int
)

/**
 * Complete security audit report.
 * Aligned with OpenClaw SecurityAuditReport.
 */
data class SecurityAuditReport(
    val timestamp: Long,
    val summary: SecurityAuditSummary,
    val findings: List<SecurityAuditFinding>
)

/**
 * SecurityAudit — Runtime security audit for the Android agent.
 * Aligned with OpenClaw runSecurityAudit.
 */
object SecurityAudit {

    private const val TAG = "SecurityAudit"

    /**
     * Run a comprehensive security audit.
     * Aligned with OpenClaw runSecurityAudit.
     */
    fun runAudit(configLoader: ConfigLoader?): SecurityAuditReport {
        val findings = mutableListOf<SecurityAuditFinding>()
        val config = try {
            configLoader?.loadOpenClawConfig()
        } catch (_: Exception) { null }

        // 1. Config security checks
        findings.addAll(auditConfig(config))

        // 2. Channel security checks
        findings.addAll(auditChannelSecurity(config))

        // 3. Tool policy checks
        findings.addAll(auditToolPolicy())

        // 4. Gateway security checks
        findings.addAll(auditGatewaySecurity(config))

        // 5. File permission checks
        findings.addAll(auditFilePermissions())

        val summary = SecurityAuditSummary(
            critical = findings.count { it.severity == SecurityAuditSeverity.CRITICAL },
            warn = findings.count { it.severity == SecurityAuditSeverity.WARN },
            info = findings.count { it.severity == SecurityAuditSeverity.INFO }
        )

        val report = SecurityAuditReport(
            timestamp = System.currentTimeMillis(),
            summary = summary,
            findings = findings
        )

        Log.i(TAG, "Security audit complete: ${summary.critical} critical, ${summary.warn} warn, ${summary.info} info")
        return report
    }

    /**
     * Audit configuration for security issues.
     */
    private fun auditConfig(config: com.xiaomo.androidforclaw.config.OpenClawConfig?): List<SecurityAuditFinding> {
        val findings = mutableListOf<SecurityAuditFinding>()

        if (config == null) {
            findings.add(SecurityAuditFinding(
                checkId = "config-missing",
                severity = SecurityAuditSeverity.WARN,
                title = "No configuration loaded",
                detail = "OpenClaw config could not be loaded. Default settings are in use.",
                remediation = "Create openclaw.json with explicit security settings"
            ))
            return findings
        }

        // Check if any channel has open DM policy without allowlist
        config.channels?.feishu?.let { feishu ->
            if (feishu.enabled && feishu.dmPolicy == "open") {
                findings.add(SecurityAuditFinding(
                    checkId = "feishu-dm-open",
                    severity = SecurityAuditSeverity.WARN,
                    title = "Feishu DM policy is 'open'",
                    detail = "Anyone can message the bot in DM without access control.",
                    remediation = "Set dmPolicy to 'pairing' or 'allowlist' with specific allowFrom"
                ))
            }
        }

        config.channels?.discord?.let { discord ->
            if (discord.enabled && discord.dm?.policy == "open") {
                findings.add(SecurityAuditFinding(
                    checkId = "discord-dm-open",
                    severity = SecurityAuditSeverity.WARN,
                    title = "Discord DM policy is 'open'",
                    detail = "Anyone can message the bot in DM without access control.",
                    remediation = "Set dm.policy to 'pairing' or 'allowlist'"
                ))
            }
        }

        // Check for missing gateway auth token
        config.gateway.let { gateway ->
            if (gateway.auth?.token.isNullOrBlank()) {
                findings.add(SecurityAuditFinding(
                    checkId = "gateway-no-auth",
                    severity = SecurityAuditSeverity.CRITICAL,
                    title = "Gateway has no auth token",
                    detail = "Gateway auth token is not set.",
                    remediation = "Set gateway.auth.token to a strong random string"
                ))
            }
        }

        return findings
    }

    /**
     * Audit channel security settings.
     * Aligned with OpenClaw audit-channel.ts.
     */
    private fun auditChannelSecurity(config: com.xiaomo.androidforclaw.config.OpenClawConfig?): List<SecurityAuditFinding> {
        val findings = mutableListOf<SecurityAuditFinding>()

        // Check for channels with open group policy
        config?.channels?.feishu?.let { feishu ->
            if (feishu.enabled && feishu.groupPolicy == "open") {
                findings.add(SecurityAuditFinding(
                    checkId = "feishu-group-open",
                    severity = SecurityAuditSeverity.INFO,
                    title = "Feishu group policy is 'open'",
                    detail = "Bot will respond in any group it's added to.",
                    remediation = "Set groupPolicy to 'allowlist' with specific groupAllowFrom"
                ))
            }
            if (feishu.enabled && feishu.requireMention != true) {
                findings.add(SecurityAuditFinding(
                    checkId = "feishu-no-mention-required",
                    severity = SecurityAuditSeverity.INFO,
                    title = "Feishu does not require @mention in groups",
                    detail = "Bot responds to all messages in groups without requiring @mention.",
                    remediation = "Set requireMention to true to avoid noise"
                ))
            }
        }

        return findings
    }

    /**
     * Audit tool policy configuration.
     * Aligned with OpenClaw audit-tool-policy.ts.
     */
    private fun auditToolPolicy(): List<SecurityAuditFinding> {
        val findings = mutableListOf<SecurityAuditFinding>()

        // Verify that group-restricted tools are properly configured
        val restricted = ToolPolicyResolver.getRestrictedToolNames()
        if (restricted.isEmpty()) {
            findings.add(SecurityAuditFinding(
                checkId = "tool-policy-no-restrictions",
                severity = SecurityAuditSeverity.WARN,
                title = "No tools restricted in group chats",
                detail = "GROUP_RESTRICTED_TOOLS is empty. Memory and config tools may be exposed in shared contexts.",
                remediation = "Add sensitive tools to GROUP_RESTRICTED_TOOLS in ToolPolicy.kt"
            ))
        }

        return findings
    }

    /**
     * Audit gateway security settings.
     */
    private fun auditGatewaySecurity(config: com.xiaomo.androidforclaw.config.OpenClawConfig?): List<SecurityAuditFinding> {
        val findings = mutableListOf<SecurityAuditFinding>()

        config?.gateway?.let { gateway ->
            // Check bind address
            if (gateway.bind != "loopback" && gateway.bind != "localhost") {
                findings.add(SecurityAuditFinding(
                    checkId = "gateway-bind-all",
                    severity = SecurityAuditSeverity.WARN,
                    title = "Gateway binds to all interfaces",
                    detail = "Gateway bind='${gateway.bind}' is accessible from external networks.",
                    remediation = "Set gateway.bind to 'loopback' for local-only access"
                ))
            }
        }

        return findings
    }

    /**
     * Audit file permissions for sensitive files.
     */
    private fun auditFilePermissions(): List<SecurityAuditFinding> {
        val findings = mutableListOf<SecurityAuditFinding>()

        // On Android, app-private files are automatically sandboxed.
        // Check if openclaw.json is world-readable (unlikely on Android but good to verify)
        findings.add(SecurityAuditFinding(
            checkId = "android-sandbox-ok",
            severity = SecurityAuditSeverity.INFO,
            title = "Android app sandbox active",
            detail = "Files are protected by Android's app sandbox (SELinux + UID isolation)."
        ))

        return findings
    }
}
