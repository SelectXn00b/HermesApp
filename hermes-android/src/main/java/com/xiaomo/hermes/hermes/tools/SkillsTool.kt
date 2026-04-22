package com.xiaomo.hermes.hermes.tools

/**
 * Skills Tool — high-level interface for skill management.
 * Ported from skills_tool.py
 */

/**
 * Skill readiness status enum.
 * Ported from SkillReadinessStatus in skills_tool.py.
 */
enum class SkillReadinessStatus(val value: String) {
    AVAILABLE("available"),
    SETUP_NEEDED("setup_needed"),
    UNSUPPORTED("unsupported")
}
