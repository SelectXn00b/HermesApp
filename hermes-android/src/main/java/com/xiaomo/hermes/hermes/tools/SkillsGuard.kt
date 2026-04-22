package com.xiaomo.hermes.hermes.tools

/**
 * Skills Guard — security scanning of skill packages.
 * Ported from tools/skills_guard.py
 */

/**
 * A single security finding from skill code scanning.
 * Ported from Finding in skills_guard.py.
 */
data class Finding(
    val patternId: String = "",
    val severity: String = "",       // "critical" | "high" | "medium" | "low"
    val category: String = "",       // "exfiltration" | "injection" | "destructive" | "persistence" | "network" | "obfuscation"
    val file: String = "",
    val line: Int = 0,
    val match: String = "",
    val description: String = ""
)

/**
 * Result of scanning a skill for security threats.
 * Ported from ScanResult in skills_guard.py.
 */
data class ScanResult(
    val skillName: String = "",
    val source: String = "",
    val trustLevel: String = "",     // "builtin" | "trusted" | "community"
    val verdict: String = "",        // "safe" | "caution" | "dangerous"
    val findings: List<Finding> = emptyList(),
    val scannedAt: String = "",
    val summary: String = ""
)
