package com.xiaomo.hermes.hermes.tools

import android.util.Log
import com.google.gson.Gson

/**
 * Tirith Security — security policy enforcement.
 * Ported from tirith_security.py
 */
object TirithSecurity {

    private const val TAG = "TirithSecurity"
    private val gson = Gson()

    data class SecurityCheck(
        val name: String,
        val passed: Boolean,
        val message: String = "")

    data class SecurityReport(
        val passed: Boolean,
        val checks: List<SecurityCheck>,
        val riskLevel: String,  // "low", "medium", "high", "critical"
    )

    /**
     * Run all security checks on a proposed action.
     */
    fun runChecks(
        action: String,
        target: String? = null,
        content: String? = null): SecurityReport {
        val checks = mutableListOf<SecurityCheck>()

        // Path security check
        if (target != null) {
            val pathError = PathSecurity.validatePath(target)
            checks.add(SecurityCheck(
                name = "path_security",
                passed = pathError == null,
                message = pathError ?: "Path is safe"))
        }

        // Write denial check
        if (target != null && action in listOf("write", "delete", "move")) {
            val denied = WritePathDenial.isWriteDenied(target)
            checks.add(SecurityCheck(
                name = "write_denial",
                passed = !denied,
                message = if (denied) "Write to '$target' is blocked" else "Write allowed"))
        }

        // URL safety check
        if (target != null && action == "fetch") {
            val safe = UrlSafety.isSafeUrl(target)
            checks.add(SecurityCheck(
                name = "url_safety",
                passed = safe,
                message = if (!safe) "URL blocked by SSRF protection" else "URL is safe"))
        }

        // Website policy check
        if (target != null && action in listOf("fetch", "browse")) {
            val blocked = WebsitePolicy.checkWebsiteAccess(target)
            checks.add(SecurityCheck(
                name = "website_policy",
                passed = blocked == null,
                message = blocked?.message ?: "Website access allowed"))
        }

        val allPassed = checks.all { it.passed }
        val riskLevel = when {
            !allPassed && checks.any { !it.passed } -> "high"
            else -> "low"
        }

        return SecurityReport(passed = allPassed, checks = checks, riskLevel = riskLevel)
    }


}
