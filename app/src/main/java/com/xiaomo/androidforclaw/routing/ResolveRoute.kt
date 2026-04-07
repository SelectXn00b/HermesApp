package com.xiaomo.androidforclaw.routing

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: routing
 * Source:
 *   - OpenClaw/src/routing/resolve-route.ts
 *   - OpenClaw/src/routing/account-lookup.ts
 *   - OpenClaw/src/routing/bindings.ts
 *   - OpenClaw/src/routing/default-account-warnings.ts
 *
 * Inbound message routing engine: maps channels, peers, guilds, teams, and roles
 * to agents and session keys using configurable bindings with 8-tier matching.
 */

// =========================================================================
// ChatType — aligned with OpenClaw/src/channels/chat-type.ts
// =========================================================================

typealias ChatType = String  // "direct" | "group" | "channel"

fun normalizeChatType(raw: String?): ChatType? {
    val value = raw?.trim()?.lowercase() ?: return null
    return when (value) {
        "direct", "dm" -> "direct"
        "group" -> "group"
        "channel" -> "channel"
        else -> null
    }
}

// =========================================================================
// RoutePeer / ResolveAgentRouteInput / ResolvedAgentRoute types
// =========================================================================

data class RoutePeer(val kind: ChatType, val id: String)

data class ResolveAgentRouteInput(
    val cfg: OpenClawConfig,
    val channel: String,
    val accountId: String? = null,
    val peer: RoutePeer? = null,
    /** Parent peer for threads -- used for binding inheritance when peer doesn't match directly. */
    val parentPeer: RoutePeer? = null,
    val guildId: String? = null,
    val teamId: String? = null,
    /** Discord member role IDs -- used for role-based agent routing. */
    val memberRoleIds: List<String>? = null
)

data class ResolvedAgentRoute(
    val agentId: String,
    val channel: String,
    val accountId: String,
    /** Internal session key used for persistence + concurrency. */
    val sessionKey: String,
    /** Convenience alias for direct-chat collapse. */
    val mainSessionKey: String,
    /** Which session should receive inbound last-route updates. */
    val lastRoutePolicy: String,    // "main" | "session"
    /** Match description for debugging/logging. */
    val matchedBy: String
    // matchedBy values: "binding.peer" | "binding.peer.parent" | "binding.peer.wildcard"
    //   | "binding.guild+roles" | "binding.guild" | "binding.team"
    //   | "binding.account" | "binding.channel" | "default"
)

// =========================================================================
// deriveLastRoutePolicy / resolveInboundLastRouteSessionKey
// =========================================================================

fun deriveLastRoutePolicy(sessionKey: String, mainSessionKey: String): String =
    if (sessionKey == mainSessionKey) "main" else "session"

fun resolveInboundLastRouteSessionKey(
    lastRoutePolicy: String,
    mainSessionKey: String,
    sessionKey: String
): String =
    if (lastRoutePolicy == "main") mainSessionKey else sessionKey

// =========================================================================
// account-lookup.ts — resolveAccountEntry / resolveNormalizedAccountEntry
// =========================================================================

fun <T> resolveAccountEntry(
    accounts: Map<String, T>?,
    accountId: String
): T? {
    if (accounts == null) return null
    accounts[accountId]?.let { return it }
    val normalized = accountId.lowercase()
    val matchKey = accounts.keys.find { it.lowercase() == normalized }
    return matchKey?.let { accounts[it] }
}

fun <T> resolveNormalizedAccountEntry(
    accounts: Map<String, T>?,
    accountId: String,
    normalizer: (String) -> String = ::normalizeAccountId
): T? {
    if (accounts == null) return null
    accounts[accountId]?.let { return it }
    val normalized = normalizer(accountId)
    val matchKey = accounts.keys.find { normalizer(it) == normalized }
    return matchKey?.let { accounts[it] }
}

// =========================================================================
// default-account-warnings.ts
// =========================================================================

fun formatChannelDefaultAccountPath(channelKey: String): String =
    "channels.$channelKey.defaultAccount"

fun formatChannelAccountsDefaultPath(channelKey: String): String =
    "channels.$channelKey.accounts.default"

fun formatSetExplicitDefaultInstruction(channelKey: String): String =
    "Set ${formatChannelDefaultAccountPath(channelKey)} or add ${formatChannelAccountsDefaultPath(channelKey)}"

fun formatSetExplicitDefaultToConfiguredInstruction(channelKey: String): String =
    "Set ${formatChannelDefaultAccountPath(channelKey)} to one of these accounts, or add ${formatChannelAccountsDefaultPath(channelKey)}"

// =========================================================================
// AgentRouteBinding types — aligned with config/types.agents.ts
// =========================================================================

/**
 * Minimal representation of an agent route binding from config.
 * Aligned with OpenClaw AgentRouteBinding.
 */
data class AgentBindingMatch(
    val channel: String? = null,
    val accountId: String? = null,
    val peer: AgentBindingPeer? = null,
    val guildId: String? = null,
    val teamId: String? = null,
    val roles: List<String>? = null
)

data class AgentBindingPeer(
    val kind: String? = null,
    val id: String? = null
)

data class AgentRouteBinding(
    val type: String? = null,   // null or "route" => route binding; "acp" => skip
    val agentId: String = DEFAULT_AGENT_ID,
    val comment: String? = null,
    val match: AgentBindingMatch? = null
)

// =========================================================================
// bindings.ts — listBindings + helpers
// =========================================================================

/**
 * List route bindings from config. Reads cfg.bindings as a list of
 * [AgentRouteBinding]. On Android we use a dynamic accessor pattern since
 * the OpenClawConfig data class may not yet expose a typed `bindings` field.
 */
fun listBindings(cfg: OpenClawConfig): List<AgentRouteBinding> {
    return listRouteBindings(cfg)
}

/**
 * Reads route bindings from the config. Since OpenClawConfig may not have
 * a typed `bindings` field yet, we access it reflectively or fall back to
 * an empty list.
 */
private fun listRouteBindings(cfg: OpenClawConfig): List<AgentRouteBinding> {
    // Try to read the bindings field reflectively
    return try {
        val field = cfg::class.java.getDeclaredField("bindings")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val bindings = field.get(cfg) as? List<*> ?: return emptyList()
        bindings.filterIsInstance<AgentRouteBinding>().filter { b ->
            b.type == null || b.type == "route"
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun normalizeBindingChannelId(raw: String?): String? {
    val fallback = (raw ?: "").trim().lowercase()
    return fallback.ifEmpty { null }
}

data class NormalizedBindingInfo(
    val agentId: String,
    val accountId: String,
    val channelId: String
)

private fun resolveNormalizedBindingMatch(binding: AgentRouteBinding): NormalizedBindingInfo? {
    val match = binding.match ?: return null
    val channelId = normalizeBindingChannelId(match.channel) ?: return null
    val accountId = match.accountId?.trim() ?: ""
    if (accountId.isEmpty() || accountId == "*") return null
    return NormalizedBindingInfo(
        agentId = normalizeAgentId(binding.agentId),
        accountId = normalizeAccountId(accountId),
        channelId = channelId
    )
}

fun listBoundAccountIds(cfg: OpenClawConfig, channelId: String): List<String> {
    val normalizedChannel = normalizeBindingChannelId(channelId) ?: return emptyList()
    val ids = mutableSetOf<String>()
    for (binding in listBindings(cfg)) {
        val resolved = resolveNormalizedBindingMatch(binding) ?: continue
        if (resolved.channelId != normalizedChannel) continue
        ids.add(resolved.accountId)
    }
    return ids.sorted()
}

fun resolveDefaultAgentBoundAccountId(cfg: OpenClawConfig, channelId: String): String? {
    val normalizedChannel = normalizeBindingChannelId(channelId) ?: return null
    val defaultAgentId = normalizeAgentId(resolveDefaultAgentIdFromConfig(cfg))
    for (binding in listBindings(cfg)) {
        val resolved = resolveNormalizedBindingMatch(binding) ?: continue
        if (resolved.channelId != normalizedChannel || resolved.agentId != defaultAgentId) continue
        return resolved.accountId
    }
    return null
}

fun buildChannelAccountBindings(cfg: OpenClawConfig): Map<String, Map<String, List<String>>> {
    val map = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
    for (binding in listBindings(cfg)) {
        val resolved = resolveNormalizedBindingMatch(binding) ?: continue
        val byAgent = map.getOrPut(resolved.channelId) { mutableMapOf() }
        val list = byAgent.getOrPut(resolved.agentId) { mutableListOf() }
        if (resolved.accountId !in list) list.add(resolved.accountId)
    }
    return map
}

fun resolvePreferredAccountId(
    accountIds: List<String>,
    defaultAccountId: String,
    boundAccounts: List<String>
): String {
    return if (boundAccounts.isNotEmpty()) boundAccounts[0] else defaultAccountId
}

// =========================================================================
// Normalized binding match / constraint types
// =========================================================================

sealed class NormalizedPeerConstraint {
    data object None : NormalizedPeerConstraint()
    data object Invalid : NormalizedPeerConstraint()
    data class WildcardKind(val kind: ChatType) : NormalizedPeerConstraint()
    data class Valid(val kind: ChatType, val id: String) : NormalizedPeerConstraint()
}

data class NormalizedBindingMatch(
    val accountPattern: String,
    val peer: NormalizedPeerConstraint,
    val guildId: String?,
    val teamId: String?,
    val roles: List<String>?
)

data class EvaluatedBinding(
    val binding: AgentRouteBinding,
    val match: NormalizedBindingMatch,
    val order: Int
)

data class BindingScope(
    val peer: RoutePeer?,
    val guildId: String,
    val teamId: String,
    val memberRoleIds: Set<String>
)

// =========================================================================
// Evaluated bindings index for fast multi-tier lookup
// =========================================================================

data class EvaluatedBindingsIndex(
    val byPeer: Map<String, List<EvaluatedBinding>>,
    val byPeerWildcard: List<EvaluatedBinding>,
    val byGuildWithRoles: Map<String, List<EvaluatedBinding>>,
    val byGuild: Map<String, List<EvaluatedBinding>>,
    val byTeam: Map<String, List<EvaluatedBinding>>,
    val byAccount: List<EvaluatedBinding>,
    val byChannel: List<EvaluatedBinding>
)

private data class EvaluatedBindingsByChannel(
    val byAccount: MutableMap<String, MutableList<EvaluatedBinding>>,
    val byAnyAccount: MutableList<EvaluatedBinding>
)

// =========================================================================
// Agent lookup helpers
// =========================================================================

private data class AgentLookupCache(
    val agentsRef: Any?,
    val byNormalizedId: Map<String, String>,
    val fallbackDefaultAgentId: String
)

/**
 * Resolve default agent ID from config agent list.
 * Aligned with OpenClaw/src/agents/agent-scope.ts resolveDefaultAgentId.
 */
fun resolveDefaultAgentIdFromConfig(cfg: OpenClawConfig): String {
    val agents = listAgentEntries(cfg)
    if (agents.isEmpty()) return DEFAULT_AGENT_ID
    val defaults = agents.filter { it["default"] == true }
    val chosen = (defaults.firstOrNull() ?: agents.firstOrNull())
    val id = (chosen?.get("id") as? String)?.trim()
    return normalizeAgentId(id ?: DEFAULT_AGENT_ID)
}

/**
 * List agent entries from config. Returns a list of agent maps.
 */
private fun listAgentEntries(cfg: OpenClawConfig): List<Map<String, Any?>> {
    // Try reflective access to agents.list
    return try {
        val agentsConfig = cfg.agents ?: return emptyList()
        val listField = agentsConfig::class.java.getDeclaredField("list")
        listField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = listField.get(agentsConfig) as? List<*> ?: return emptyList()
        list.filterIsInstance<Map<String, Any?>>()
    } catch (_: Exception) {
        emptyList()
    }
}

// =========================================================================
// LRU cache helpers (simple Map-based, replaces WeakMap from TS)
// =========================================================================

private const val MAX_EVALUATED_BINDINGS_CACHE_KEYS = 2000
private const val MAX_RESOLVED_ROUTE_CACHE_KEYS = 4000

// Binding evaluation caches
private var evaluatedBindingsChannelCache: MutableMap<String, EvaluatedBindingsByChannel>? = null
private var evaluatedBindingsChannelAccountCache: MutableMap<String, List<EvaluatedBinding>>? = null
private var evaluatedBindingsChannelAccountIndexCache: MutableMap<String, EvaluatedBindingsIndex>? = null
private var lastBindingsRef: Any? = null

// Route resolution cache
private var resolvedRouteCache: MutableMap<String, ResolvedAgentRoute>? = null
private var lastRouteBindingsRef: Any? = null
private var lastRouteAgentsRef: Any? = null
private var lastRouteSessionRef: Any? = null

// Agent lookup cache
private var agentLookupCache: AgentLookupCache? = null

// =========================================================================
// Agent lookup (pickFirstExistingAgentId)
// =========================================================================

private fun resolveAgentLookupCache(cfg: OpenClawConfig): AgentLookupCache {
    val agentsRef = cfg.agents
    val existing = agentLookupCache
    if (existing != null && existing.agentsRef === agentsRef) return existing

    val byNormalizedId = mutableMapOf<String, String>()
    for (agent in listAgentEntries(cfg)) {
        val rawId = (agent["id"] as? String)?.trim() ?: continue
        byNormalizedId[normalizeAgentId(rawId)] = sanitizeAgentId(rawId)
    }
    val next = AgentLookupCache(
        agentsRef = agentsRef,
        byNormalizedId = byNormalizedId,
        fallbackDefaultAgentId = sanitizeAgentId(resolveDefaultAgentIdFromConfig(cfg))
    )
    agentLookupCache = next
    return next
}

fun pickFirstExistingAgentId(cfg: OpenClawConfig, agentId: String): String {
    val lookup = resolveAgentLookupCache(cfg)
    val trimmed = agentId.trim()
    if (trimmed.isEmpty()) return lookup.fallbackDefaultAgentId

    val normalized = normalizeAgentId(trimmed)
    if (lookup.byNormalizedId.isEmpty()) return sanitizeAgentId(trimmed)

    return lookup.byNormalizedId[normalized] ?: lookup.fallbackDefaultAgentId
}

// =========================================================================
// buildAgentSessionKey — from resolve-route.ts
// =========================================================================

fun buildAgentSessionKey(
    agentId: String,
    channel: String,
    accountId: String? = null,
    peer: RoutePeer? = null,
    dmScope: String? = null,
    identityLinks: Map<String, List<String>>? = null
): String {
    val normalizedChannel = normalizeToken(channel).ifEmpty { "unknown" }
    return buildAgentPeerSessionKey(
        agentId = agentId,
        mainKey = DEFAULT_MAIN_KEY,
        channel = normalizedChannel,
        accountId = accountId,
        peerKind = peer?.kind ?: "direct",
        peerId = if (peer != null) normalizeId(peer.id).ifEmpty { "unknown" } else null,
        dmScope = dmScope,
        identityLinks = identityLinks
    )
}

// =========================================================================
// Internal helpers
// =========================================================================

private fun normalizeToken(value: String?): String =
    (value ?: "").trim().lowercase()

private fun normalizeId(value: Any?): String =
    when (value) {
        is String -> value.trim()
        is Number -> value.toString().trim()
        else -> ""
    }

// =========================================================================
// Peer constraint normalization
// =========================================================================

private fun normalizePeerConstraint(peer: AgentBindingPeer?): NormalizedPeerConstraint {
    if (peer == null) return NormalizedPeerConstraint.None
    val kind = normalizeChatType(peer.kind) ?: return NormalizedPeerConstraint.Invalid
    val id = normalizeId(peer.id)
    if (id.isEmpty()) return NormalizedPeerConstraint.Invalid
    if (id == "*") return NormalizedPeerConstraint.WildcardKind(kind)
    return NormalizedPeerConstraint.Valid(kind, id)
}

private fun normalizeBindingMatch(match: AgentBindingMatch?): NormalizedBindingMatch {
    val rawRoles = match?.roles
    return NormalizedBindingMatch(
        accountPattern = (match?.accountId ?: "").trim(),
        peer = normalizePeerConstraint(match?.peer),
        guildId = normalizeId(match?.guildId).ifEmpty { null },
        teamId = normalizeId(match?.teamId).ifEmpty { null },
        roles = if (rawRoles != null && rawRoles.isNotEmpty()) rawRoles else null
    )
}

// =========================================================================
// Peer lookup keys (group <-> channel interchangeable)
// =========================================================================

private fun peerLookupKeys(kind: ChatType, id: String): List<String> =
    when (kind) {
        "group" -> listOf("group:$id", "channel:$id")
        "channel" -> listOf("channel:$id", "group:$id")
        else -> listOf("$kind:$id")
    }

// =========================================================================
// buildEvaluatedBindingsByChannel — from resolve-route.ts
// =========================================================================

private fun resolveAccountPatternKey(accountPattern: String): String {
    val trimmed = accountPattern.trim()
    if (trimmed.isEmpty()) return DEFAULT_ACCOUNT_ID
    return normalizeAccountId(trimmed)
}

private fun buildEvaluatedBindingsByChannel(
    cfg: OpenClawConfig
): MutableMap<String, EvaluatedBindingsByChannel> {
    val byChannel = mutableMapOf<String, EvaluatedBindingsByChannel>()
    var order = 0
    for (binding in listBindings(cfg)) {
        val channelMatch = binding.match ?: continue
        val channel = normalizeToken(channelMatch.channel)
        if (channel.isEmpty()) continue

        val match = normalizeBindingMatch(channelMatch)
        val evaluated = EvaluatedBinding(binding, match, order)
        order += 1

        val bucket = byChannel.getOrPut(channel) {
            EvaluatedBindingsByChannel(mutableMapOf(), mutableListOf())
        }

        if (match.accountPattern == "*") {
            bucket.byAnyAccount.add(evaluated)
            continue
        }

        val accountKey = resolveAccountPatternKey(match.accountPattern)
        bucket.byAccount.getOrPut(accountKey) { mutableListOf() }.add(evaluated)
    }
    return byChannel
}

// =========================================================================
// mergeEvaluatedBindingsInSourceOrder
// =========================================================================

private fun mergeEvaluatedBindingsInSourceOrder(
    accountScoped: List<EvaluatedBinding>,
    anyAccount: List<EvaluatedBinding>
): List<EvaluatedBinding> {
    if (accountScoped.isEmpty()) return anyAccount
    if (anyAccount.isEmpty()) return accountScoped

    val merged = mutableListOf<EvaluatedBinding>()
    var accountIdx = 0
    var anyIdx = 0
    while (accountIdx < accountScoped.size && anyIdx < anyAccount.size) {
        val accountBinding = accountScoped[accountIdx]
        val anyBinding = anyAccount[anyIdx]
        if (accountBinding.order <= anyBinding.order) {
            merged.add(accountBinding)
            accountIdx += 1
        } else {
            merged.add(anyBinding)
            anyIdx += 1
        }
    }
    if (accountIdx < accountScoped.size) {
        merged.addAll(accountScoped.subList(accountIdx, accountScoped.size))
    }
    if (anyIdx < anyAccount.size) {
        merged.addAll(anyAccount.subList(anyIdx, anyAccount.size))
    }
    return merged
}

// =========================================================================
// pushToIndexMap
// =========================================================================

private fun pushToIndexMap(
    map: MutableMap<String, MutableList<EvaluatedBinding>>,
    key: String?,
    binding: EvaluatedBinding
) {
    if (key == null) return
    map.getOrPut(key) { mutableListOf() }.add(binding)
}

// =========================================================================
// buildEvaluatedBindingsIndex — from resolve-route.ts
// =========================================================================

private fun buildEvaluatedBindingsIndex(bindings: List<EvaluatedBinding>): EvaluatedBindingsIndex {
    val byPeer = mutableMapOf<String, MutableList<EvaluatedBinding>>()
    val byPeerWildcard = mutableListOf<EvaluatedBinding>()
    val byGuildWithRoles = mutableMapOf<String, MutableList<EvaluatedBinding>>()
    val byGuild = mutableMapOf<String, MutableList<EvaluatedBinding>>()
    val byTeam = mutableMapOf<String, MutableList<EvaluatedBinding>>()
    val byAccount = mutableListOf<EvaluatedBinding>()
    val byChannel = mutableListOf<EvaluatedBinding>()

    for (binding in bindings) {
        when (val peer = binding.match.peer) {
            is NormalizedPeerConstraint.Valid -> {
                for (key in peerLookupKeys(peer.kind, peer.id)) {
                    pushToIndexMap(byPeer, key, binding)
                }
                continue
            }
            is NormalizedPeerConstraint.WildcardKind -> {
                byPeerWildcard.add(binding)
                continue
            }
            else -> { /* fall through to guild/team/account/channel checks */ }
        }
        if (binding.match.guildId != null && binding.match.roles != null) {
            pushToIndexMap(byGuildWithRoles, binding.match.guildId, binding)
            continue
        }
        if (binding.match.guildId != null && binding.match.roles == null) {
            pushToIndexMap(byGuild, binding.match.guildId, binding)
            continue
        }
        if (binding.match.teamId != null) {
            pushToIndexMap(byTeam, binding.match.teamId, binding)
            continue
        }
        if (binding.match.accountPattern != "*") {
            byAccount.add(binding)
            continue
        }
        byChannel.add(binding)
    }

    return EvaluatedBindingsIndex(byPeer, byPeerWildcard, byGuildWithRoles, byGuild, byTeam, byAccount, byChannel)
}

// =========================================================================
// getEvaluatedBindingsForChannelAccount / Index
// =========================================================================

private fun ensureBindingsCache(cfg: OpenClawConfig): Triple<
    MutableMap<String, EvaluatedBindingsByChannel>,
    MutableMap<String, List<EvaluatedBinding>>,
    MutableMap<String, EvaluatedBindingsIndex>
> {
    val bindingsRef = try {
        val field = cfg::class.java.getDeclaredField("bindings")
        field.isAccessible = true
        field.get(cfg)
    } catch (_: Exception) { null }

    if (bindingsRef === lastBindingsRef &&
        evaluatedBindingsChannelCache != null &&
        evaluatedBindingsChannelAccountCache != null &&
        evaluatedBindingsChannelAccountIndexCache != null
    ) {
        return Triple(
            evaluatedBindingsChannelCache!!,
            evaluatedBindingsChannelAccountCache!!,
            evaluatedBindingsChannelAccountIndexCache!!
        )
    }

    val channelCache = buildEvaluatedBindingsByChannel(cfg)
    val channelAccountCache = mutableMapOf<String, List<EvaluatedBinding>>()
    val channelAccountIndexCache = mutableMapOf<String, EvaluatedBindingsIndex>()

    evaluatedBindingsChannelCache = channelCache
    evaluatedBindingsChannelAccountCache = channelAccountCache
    evaluatedBindingsChannelAccountIndexCache = channelAccountIndexCache
    lastBindingsRef = bindingsRef

    return Triple(channelCache, channelAccountCache, channelAccountIndexCache)
}

private fun getEvaluatedBindingsForChannelAccount(
    cfg: OpenClawConfig,
    channel: String,
    accountId: String
): List<EvaluatedBinding> {
    val (channelCache, channelAccountCache, channelAccountIndexCache) = ensureBindingsCache(cfg)

    val cacheKey = "$channel\t$accountId"
    channelAccountCache[cacheKey]?.let { return it }

    val channelBindings = channelCache[channel]
    val accountScoped = channelBindings?.byAccount?.get(accountId) ?: emptyList()
    val anyAccount = channelBindings?.byAnyAccount ?: emptyList()
    val evaluated = mergeEvaluatedBindingsInSourceOrder(accountScoped, anyAccount)

    channelAccountCache[cacheKey] = evaluated
    channelAccountIndexCache[cacheKey] = buildEvaluatedBindingsIndex(evaluated)

    if (channelAccountCache.size > MAX_EVALUATED_BINDINGS_CACHE_KEYS) {
        channelAccountCache.clear()
        channelAccountIndexCache.clear()
        channelAccountCache[cacheKey] = evaluated
        channelAccountIndexCache[cacheKey] = buildEvaluatedBindingsIndex(evaluated)
    }

    return evaluated
}

private fun getEvaluatedBindingIndexForChannelAccount(
    cfg: OpenClawConfig,
    channel: String,
    accountId: String
): EvaluatedBindingsIndex {
    val (_, channelAccountCache, channelAccountIndexCache) = ensureBindingsCache(cfg)

    val bindings = getEvaluatedBindingsForChannelAccount(cfg, channel, accountId)
    val cacheKey = "$channel\t$accountId"
    channelAccountIndexCache[cacheKey]?.let { return it }

    val built = buildEvaluatedBindingsIndex(bindings)
    channelAccountIndexCache[cacheKey] = built
    return built
}

// =========================================================================
// collectPeerIndexedBindings
// =========================================================================

private fun collectPeerIndexedBindings(
    index: EvaluatedBindingsIndex,
    peer: RoutePeer?
): List<EvaluatedBinding> {
    if (peer == null) return emptyList()
    val out = mutableListOf<EvaluatedBinding>()
    val seen = mutableSetOf<EvaluatedBinding>()
    for (key in peerLookupKeys(peer.kind, peer.id)) {
        val matches = index.byPeer[key] ?: continue
        for (match in matches) {
            if (match in seen) continue
            seen.add(match)
            out.add(match)
        }
    }
    return out
}

// =========================================================================
// Scope matching helpers
// =========================================================================

private fun hasGuildConstraint(match: NormalizedBindingMatch): Boolean = match.guildId != null
private fun hasTeamConstraint(match: NormalizedBindingMatch): Boolean = match.teamId != null
private fun hasRolesConstraint(match: NormalizedBindingMatch): Boolean = match.roles != null

private fun peerKindMatches(bindingKind: ChatType, scopeKind: ChatType): Boolean {
    if (bindingKind == scopeKind) return true
    val both = setOf(bindingKind, scopeKind)
    return "group" in both && "channel" in both
}

private fun matchesBindingScope(match: NormalizedBindingMatch, scope: BindingScope): Boolean {
    when (val peer = match.peer) {
        is NormalizedPeerConstraint.Invalid -> return false
        is NormalizedPeerConstraint.Valid -> {
            if (scope.peer == null ||
                !peerKindMatches(peer.kind, scope.peer.kind) ||
                scope.peer.id != peer.id
            ) return false
        }
        is NormalizedPeerConstraint.WildcardKind -> {
            if (scope.peer == null || !peerKindMatches(peer.kind, scope.peer.kind)) return false
        }
        is NormalizedPeerConstraint.None -> { /* no peer constraint, pass */ }
    }
    if (match.guildId != null && match.guildId != scope.guildId) return false
    if (match.teamId != null && match.teamId != scope.teamId) return false
    if (match.roles != null) {
        for (role in match.roles) {
            if (role in scope.memberRoleIds) return true
        }
        return false
    }
    return true
}

// =========================================================================
// Route cache helpers
// =========================================================================

private fun resolveRouteCache(cfg: OpenClawConfig): MutableMap<String, ResolvedAgentRoute>? {
    val bindingsRef = try {
        val f = cfg::class.java.getDeclaredField("bindings"); f.isAccessible = true; f.get(cfg)
    } catch (_: Exception) { null }

    if (bindingsRef === lastRouteBindingsRef &&
        cfg.agents === lastRouteAgentsRef &&
        cfg.session === lastRouteSessionRef &&
        resolvedRouteCache != null
    ) {
        return resolvedRouteCache
    }

    val cache = mutableMapOf<String, ResolvedAgentRoute>()
    resolvedRouteCache = cache
    lastRouteBindingsRef = bindingsRef
    lastRouteAgentsRef = cfg.agents
    lastRouteSessionRef = cfg.session
    return cache
}

private fun formatRouteCachePeer(peer: RoutePeer?): String {
    if (peer == null || peer.id.isEmpty()) return "-"
    return "${peer.kind}:${peer.id}"
}

private fun formatRoleIdsCacheKey(roleIds: List<String>): String {
    val count = roleIds.size
    if (count == 0) return "-"
    if (count == 1) return roleIds[0]
    if (count == 2) {
        val first = roleIds[0]
        val second = roleIds[1]
        return if (first <= second) "$first,$second" else "$second,$first"
    }
    return roleIds.sorted().joinToString(",")
}

private fun buildResolvedRouteCacheKey(
    channel: String,
    accountId: String,
    peer: RoutePeer?,
    parentPeer: RoutePeer?,
    guildId: String,
    teamId: String,
    memberRoleIds: List<String>,
    dmScope: String
): String =
    "$channel\t$accountId\t${formatRouteCachePeer(peer)}\t${formatRouteCachePeer(parentPeer)}\t${guildId.ifEmpty { "-" }}\t${teamId.ifEmpty { "-" }}\t${formatRoleIdsCacheKey(memberRoleIds)}\t$dmScope"

// =========================================================================
// resolveAgentRoute — the main 8-tier matching engine
// =========================================================================

fun resolveAgentRoute(input: ResolveAgentRouteInput): ResolvedAgentRoute {
    val channel = normalizeToken(input.channel)
    val accountId = normalizeAccountId(input.accountId)
    val peer = input.peer?.let {
        RoutePeer(
            kind = normalizeChatType(it.kind) ?: it.kind,
            id = normalizeId(it.id)
        )
    }
    val guildId = normalizeId(input.guildId)
    val teamId = normalizeId(input.teamId)
    val memberRoleIds = input.memberRoleIds ?: emptyList()
    val memberRoleIdSet = memberRoleIds.toSet()

    // Session config: dmScope + identityLinks
    val dmScope = resolveSessionDmScope(input.cfg)
    val identityLinks = resolveSessionIdentityLinks(input.cfg)

    val parentPeer = input.parentPeer?.let {
        RoutePeer(
            kind = normalizeChatType(it.kind) ?: it.kind,
            id = normalizeId(it.id)
        )
    }

    // Route cache lookup (skip when identityLinks is present)
    val routeCache = if (identityLinks == null) resolveRouteCache(input.cfg) else null
    val routeCacheKey = if (routeCache != null) buildResolvedRouteCacheKey(
        channel, accountId, peer, parentPeer, guildId, teamId, memberRoleIds, dmScope
    ) else ""
    if (routeCache != null && routeCacheKey.isNotEmpty()) {
        routeCache[routeCacheKey]?.let { return it.copy() }
    }

    // Build evaluated bindings
    val bindings = getEvaluatedBindingsForChannelAccount(input.cfg, channel, accountId)
    val bindingsIndex = getEvaluatedBindingIndexForChannelAccount(input.cfg, channel, accountId)

    // Helper to build the resolved route for a matched binding
    fun choose(matchedAgentId: String, matchedBy: String): ResolvedAgentRoute {
        val resolvedAgentId = pickFirstExistingAgentId(input.cfg, matchedAgentId)
        val sessionKey = buildAgentSessionKey(
            agentId = resolvedAgentId,
            channel = channel,
            accountId = accountId,
            peer = peer,
            dmScope = dmScope,
            identityLinks = identityLinks
        ).lowercase()
        val mainSessionKey = buildAgentMainSessionKey(
            agentId = resolvedAgentId,
            mainKey = DEFAULT_MAIN_KEY
        ).lowercase()
        val route = ResolvedAgentRoute(
            agentId = resolvedAgentId,
            channel = channel,
            accountId = accountId,
            sessionKey = sessionKey,
            mainSessionKey = mainSessionKey,
            lastRoutePolicy = deriveLastRoutePolicy(sessionKey, mainSessionKey),
            matchedBy = matchedBy
        )
        if (routeCache != null && routeCacheKey.isNotEmpty()) {
            routeCache[routeCacheKey] = route
            if (routeCache.size > MAX_RESOLVED_ROUTE_CACHE_KEYS) {
                routeCache.clear()
                routeCache[routeCacheKey] = route
            }
        }
        return route
    }

    // Base scope for matching
    val baseScope = BindingScope(
        peer = null,  // overridden per tier
        guildId = guildId,
        teamId = teamId,
        memberRoleIds = memberRoleIdSet
    )

    // 8-tier matching table
    data class Tier(
        val matchedBy: String,
        val enabled: Boolean,
        val scopePeer: RoutePeer?,
        val candidates: List<EvaluatedBinding>,
        val predicate: (EvaluatedBinding) -> Boolean
    )

    val tiers = listOf(
        // Tier 1: binding.peer (exact peer match)
        Tier(
            matchedBy = "binding.peer",
            enabled = peer != null,
            scopePeer = peer,
            candidates = collectPeerIndexedBindings(bindingsIndex, peer),
            predicate = { it.match.peer is NormalizedPeerConstraint.Valid }
        ),
        // Tier 2: binding.peer.parent (thread parent inheritance)
        Tier(
            matchedBy = "binding.peer.parent",
            enabled = parentPeer != null && parentPeer.id.isNotEmpty(),
            scopePeer = if (parentPeer != null && parentPeer.id.isNotEmpty()) parentPeer else null,
            candidates = collectPeerIndexedBindings(bindingsIndex, parentPeer),
            predicate = { it.match.peer is NormalizedPeerConstraint.Valid }
        ),
        // Tier 3: binding.peer.wildcard (kind:* match)
        Tier(
            matchedBy = "binding.peer.wildcard",
            enabled = peer != null,
            scopePeer = peer,
            candidates = bindingsIndex.byPeerWildcard,
            predicate = { it.match.peer is NormalizedPeerConstraint.WildcardKind }
        ),
        // Tier 4: binding.guild+roles (guild + role match)
        Tier(
            matchedBy = "binding.guild+roles",
            enabled = guildId.isNotEmpty() && memberRoleIds.isNotEmpty(),
            scopePeer = peer,
            candidates = if (guildId.isNotEmpty()) bindingsIndex.byGuildWithRoles[guildId] ?: emptyList() else emptyList(),
            predicate = { hasGuildConstraint(it.match) && hasRolesConstraint(it.match) }
        ),
        // Tier 5: binding.guild (guild-only match)
        Tier(
            matchedBy = "binding.guild",
            enabled = guildId.isNotEmpty(),
            scopePeer = peer,
            candidates = if (guildId.isNotEmpty()) bindingsIndex.byGuild[guildId] ?: emptyList() else emptyList(),
            predicate = { hasGuildConstraint(it.match) && !hasRolesConstraint(it.match) }
        ),
        // Tier 6: binding.team
        Tier(
            matchedBy = "binding.team",
            enabled = teamId.isNotEmpty(),
            scopePeer = peer,
            candidates = if (teamId.isNotEmpty()) bindingsIndex.byTeam[teamId] ?: emptyList() else emptyList(),
            predicate = { hasTeamConstraint(it.match) }
        ),
        // Tier 7: binding.account
        Tier(
            matchedBy = "binding.account",
            enabled = true,
            scopePeer = peer,
            candidates = bindingsIndex.byAccount,
            predicate = { it.match.accountPattern != "*" }
        ),
        // Tier 8: binding.channel (catch-all)
        Tier(
            matchedBy = "binding.channel",
            enabled = true,
            scopePeer = peer,
            candidates = bindingsIndex.byChannel,
            predicate = { it.match.accountPattern == "*" }
        )
    )

    for (tier in tiers) {
        if (!tier.enabled) continue
        val matched = tier.candidates.find { candidate ->
            tier.predicate(candidate) && matchesBindingScope(
                candidate.match,
                baseScope.copy(peer = tier.scopePeer)
            )
        }
        if (matched != null) {
            return choose(matched.binding.agentId, tier.matchedBy)
        }
    }

    // Default: no binding matched
    return choose(resolveDefaultAgentIdFromConfig(input.cfg), "default")
}

// =========================================================================
// Session config accessors (dmScope, identityLinks)
// Use reflective access since SessionConfig may not have these fields yet.
// =========================================================================

private fun resolveSessionDmScope(cfg: OpenClawConfig): String {
    return try {
        val session = cfg.session
        val field = session::class.java.getDeclaredField("dmScope")
        field.isAccessible = true
        (field.get(session) as? String) ?: "main"
    } catch (_: Exception) {
        "main"
    }
}

@Suppress("UNCHECKED_CAST")
private fun resolveSessionIdentityLinks(cfg: OpenClawConfig): Map<String, List<String>>? {
    return try {
        val session = cfg.session
        val field = session::class.java.getDeclaredField("identityLinks")
        field.isAccessible = true
        field.get(session) as? Map<String, List<String>>
    } catch (_: Exception) {
        null
    }
}
