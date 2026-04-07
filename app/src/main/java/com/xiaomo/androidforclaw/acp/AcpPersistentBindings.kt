package com.xiaomo.androidforclaw.acp

import java.security.MessageDigest

/**
 * OpenClaw module: acp
 * Source: OpenClaw/src/acp/persistent-bindings.types.ts
 *        OpenClaw/src/acp/persistent-bindings.lifecycle.ts
 *
 * Persistent ACP binding management: maps channel conversations to
 * ACP sessions so inbound messages route to the correct agent session.
 */

// ---------------------------------------------------------------------------
// Binding types  (aligned with TS ConfiguredAcpBindingSpec, etc.)
// ---------------------------------------------------------------------------

data class ConfiguredAcpBindingSpec(
    val channel: String,
    val accountId: String,
    val conversationId: String,
    val parentConversationId: String? = null,
    val agentId: String,
    val acpAgentId: String? = null,
    val mode: AcpRuntimeSessionMode = AcpRuntimeSessionMode.PERSISTENT,
    val cwd: String? = null,
    val backend: String? = null,
    val label: String? = null,
)

data class SessionBindingRecord(
    val bindingId: String,
    val targetSessionKey: String,
    val targetKind: String = "session",
    val conversation: BindingConversation,
    val status: String = "active",
    val boundAt: Long = 0,
    val metadata: Map<String, Any?> = emptyMap(),
)

data class BindingConversation(
    val channel: String,
    val accountId: String,
    val conversationId: String,
    val parentConversationId: String? = null,
)

data class ResolvedConfiguredAcpBinding(
    val spec: ConfiguredAcpBindingSpec,
    val record: SessionBindingRecord,
)

data class AcpBindingConfigShape(
    val mode: String? = null,
    val cwd: String? = null,
    val backend: String? = null,
    val label: String? = null,
)

// ---------------------------------------------------------------------------
// Normalization helpers  (aligned with TS persistent-bindings.types.ts)
// ---------------------------------------------------------------------------

fun normalizeBindingText(value: Any?): String? {
    if (value !is String) return null
    val trimmed = value.trim()
    return trimmed.ifEmpty { null }
}

fun normalizeBindingMode(value: Any?): AcpRuntimeSessionMode {
    val raw = normalizeBindingText(value)?.lowercase()
    return if (raw == "oneshot") AcpRuntimeSessionMode.ONESHOT else AcpRuntimeSessionMode.PERSISTENT
}

@Suppress("UNCHECKED_CAST")
fun normalizeBindingConfig(raw: Any?): AcpBindingConfigShape {
    if (raw == null || raw !is Map<*, *>) return AcpBindingConfigShape()
    val shape = raw as Map<String, Any?>
    val mode = normalizeBindingText(shape["mode"])
    return AcpBindingConfigShape(
        mode = if (mode != null) normalizeBindingMode(mode).value else null,
        cwd = normalizeBindingText(shape["cwd"]),
        backend = normalizeBindingText(shape["backend"]),
        label = normalizeBindingText(shape["label"]),
    )
}

// ---------------------------------------------------------------------------
// Session key builder  (aligned with TS buildConfiguredAcpSessionKey)
// ---------------------------------------------------------------------------

private fun buildBindingHash(channel: String, accountId: String, conversationId: String): String {
    val input = "$channel:$accountId:$conversationId"
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }.take(16)
}

private fun sanitizeAgentId(agentId: String): String =
    agentId.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "-")

fun normalizeAccountId(accountId: String): String =
    accountId.trim().lowercase().ifEmpty { "default" }

fun buildConfiguredAcpSessionKey(spec: ConfiguredAcpBindingSpec): String {
    val hash = buildBindingHash(spec.channel, spec.accountId, spec.conversationId)
    return "agent:${sanitizeAgentId(spec.agentId)}:acp:binding:${spec.channel}:${spec.accountId}:$hash"
}

// ---------------------------------------------------------------------------
// Binding record builder  (aligned with TS toConfiguredAcpBindingRecord)
// ---------------------------------------------------------------------------

fun toConfiguredAcpBindingRecord(spec: ConfiguredAcpBindingSpec): SessionBindingRecord {
    return SessionBindingRecord(
        bindingId = "config:acp:${spec.channel}:${spec.accountId}:${spec.conversationId}",
        targetSessionKey = buildConfiguredAcpSessionKey(spec),
        targetKind = "session",
        conversation = BindingConversation(
            channel = spec.channel,
            accountId = spec.accountId,
            conversationId = spec.conversationId,
            parentConversationId = spec.parentConversationId,
        ),
        status = "active",
        boundAt = 0,
        metadata = buildMap {
            put("source", "config")
            put("mode", spec.mode.value)
            put("agentId", spec.agentId)
            spec.acpAgentId?.let { put("acpAgentId", it) }
            spec.label?.let { put("label", it) }
            spec.backend?.let { put("backend", it) }
            spec.cwd?.let { put("cwd", it) }
        },
    )
}

// ---------------------------------------------------------------------------
// Session key parser  (aligned with TS parseConfiguredAcpSessionKey)
// ---------------------------------------------------------------------------

data class ParsedAcpSessionKey(
    val channel: String,
    val accountId: String,
)

fun parseConfiguredAcpSessionKey(sessionKey: String): ParsedAcpSessionKey? {
    val trimmed = sessionKey.trim()
    if (!trimmed.startsWith("agent:")) return null
    val rest = trimmed.substring(trimmed.indexOf(':') + 1)
    val nextSeparator = rest.indexOf(':')
    if (nextSeparator == -1) return null
    val tokens = rest.substring(nextSeparator + 1).split(":")
    if (tokens.size != 5 || tokens[0] != "acp" || tokens[1] != "binding") return null
    val channel = tokens[2].trim().lowercase()
    if (channel.isEmpty()) return null
    return ParsedAcpSessionKey(
        channel = channel,
        accountId = normalizeAccountId(tokens.getOrElse(3) { "default" }),
    )
}

// ---------------------------------------------------------------------------
// Spec from record  (aligned with TS resolveConfiguredAcpBindingSpecFromRecord)
// ---------------------------------------------------------------------------

fun resolveConfiguredAcpBindingSpecFromRecord(record: SessionBindingRecord): ConfiguredAcpBindingSpec? {
    if (record.targetKind != "session") return null
    val conversationId = record.conversation.conversationId.trim()
    if (conversationId.isEmpty()) return null
    val agentId = normalizeBindingText(record.metadata["agentId"])
        ?: resolveAgentIdFromSessionKey(record.targetSessionKey)
        ?: return null
    return ConfiguredAcpBindingSpec(
        channel = record.conversation.channel,
        accountId = normalizeAccountId(record.conversation.accountId),
        conversationId = conversationId,
        parentConversationId = normalizeBindingText(record.conversation.parentConversationId),
        agentId = agentId,
        acpAgentId = normalizeBindingText(record.metadata["acpAgentId"]),
        mode = normalizeBindingMode(record.metadata["mode"]),
        cwd = normalizeBindingText(record.metadata["cwd"]),
        backend = normalizeBindingText(record.metadata["backend"]),
        label = normalizeBindingText(record.metadata["label"]),
    )
}

fun toResolvedConfiguredAcpBinding(record: SessionBindingRecord): ResolvedConfiguredAcpBinding? {
    val spec = resolveConfiguredAcpBindingSpecFromRecord(record) ?: return null
    return ResolvedConfiguredAcpBinding(spec, record)
}

private fun resolveAgentIdFromSessionKey(sessionKey: String): String? {
    val trimmed = sessionKey.trim()
    if (!trimmed.startsWith("agent:")) return null
    val rest = trimmed.substring("agent:".length)
    val end = rest.indexOf(':')
    return if (end > 0) rest.substring(0, end).ifEmpty { null } else rest.ifEmpty { null }
}
