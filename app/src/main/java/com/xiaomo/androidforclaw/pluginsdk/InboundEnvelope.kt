package com.xiaomo.androidforclaw.pluginsdk

/**
 * OpenClaw Source Reference:
 * - src/plugin-sdk/inbound-envelope.ts
 *
 * Inbound envelope builder: creates formatted envelopes for inbound messages
 * from resolved routes and session state.
 * Android adaptation: uses Kotlin function types instead of TS generic type params.
 */

// ---------- Types ----------

/**
 * Route-like minimal shape.
 * Aligned with TS RouteLike.
 */
data class RouteLike(
    val agentId: String,
    val sessionKey: String,
)

/**
 * Route peer-like minimal shape.
 * Aligned with TS RoutePeerLike.
 */
data class RoutePeerLike(
    val kind: String,
    val id: String,
)

/**
 * Inbound envelope format parameters.
 * Aligned with TS InboundEnvelopeFormatParams.
 */
data class InboundEnvelopeFormatParams<TEnvelope>(
    val channel: String,
    val from: String,
    val timestamp: Long? = null,
    val previousTimestamp: Long? = null,
    val envelope: TEnvelope,
    val body: String,
)

/**
 * Inbound route resolve parameters.
 * Aligned with TS InboundRouteResolveParams.
 */
data class InboundRouteResolveParams<TConfig>(
    val cfg: TConfig,
    val channel: String,
    val accountId: String,
    val peer: RoutePeerLike,
)

/**
 * Built envelope result.
 */
data class BuiltEnvelope(
    val storePath: String,
    val body: String,
)

/**
 * Input for building an inbound envelope.
 */
data class InboundEnvelopeInput(
    val channel: String,
    val from: String,
    val body: String,
    val timestamp: Long? = null,
)

// ---------- Envelope Builder ----------

/**
 * Create an envelope formatter bound to one resolved route and session store.
 * Aligned with TS createInboundEnvelopeBuilder.
 */
fun <TConfig, TEnvelope> createInboundEnvelopeBuilder(
    cfg: TConfig,
    route: RouteLike,
    sessionStore: String? = null,
    resolveStorePath: (store: String?, agentId: String) -> String,
    readSessionUpdatedAt: (storePath: String, sessionKey: String) -> Long?,
    resolveEnvelopeFormatOptions: (cfg: TConfig) -> TEnvelope,
    formatAgentEnvelope: (params: InboundEnvelopeFormatParams<TEnvelope>) -> String,
): (input: InboundEnvelopeInput) -> BuiltEnvelope {
    val storePath = resolveStorePath(sessionStore, route.agentId)
    val envelopeOptions = resolveEnvelopeFormatOptions(cfg)

    return { input ->
        val previousTimestamp = readSessionUpdatedAt(storePath, route.sessionKey)
        val body = formatAgentEnvelope(
            InboundEnvelopeFormatParams(
                channel = input.channel,
                from = input.from,
                timestamp = input.timestamp,
                previousTimestamp = previousTimestamp,
                envelope = envelopeOptions,
                body = input.body,
            )
        )
        BuiltEnvelope(storePath = storePath, body = body)
    }
}

/**
 * Resolve a route first, then return both the route and a formatter for future inbound messages.
 * Aligned with TS resolveInboundRouteEnvelopeBuilder.
 */
fun <TConfig, TEnvelope, TRoute : RouteLike> resolveInboundRouteEnvelopeBuilder(
    cfg: TConfig,
    channel: String,
    accountId: String,
    peer: RoutePeerLike,
    resolveAgentRoute: (params: InboundRouteResolveParams<TConfig>) -> TRoute,
    sessionStore: String? = null,
    resolveStorePath: (store: String?, agentId: String) -> String,
    readSessionUpdatedAt: (storePath: String, sessionKey: String) -> Long?,
    resolveEnvelopeFormatOptions: (cfg: TConfig) -> TEnvelope,
    formatAgentEnvelope: (params: InboundEnvelopeFormatParams<TEnvelope>) -> String,
): Pair<TRoute, (InboundEnvelopeInput) -> BuiltEnvelope> {
    val route = resolveAgentRoute(
        InboundRouteResolveParams(
            cfg = cfg,
            channel = channel,
            accountId = accountId,
            peer = peer,
        )
    )
    val buildEnvelope = createInboundEnvelopeBuilder(
        cfg = cfg,
        route = route,
        sessionStore = sessionStore,
        resolveStorePath = resolveStorePath,
        readSessionUpdatedAt = readSessionUpdatedAt,
        resolveEnvelopeFormatOptions = resolveEnvelopeFormatOptions,
        formatAgentEnvelope = formatAgentEnvelope,
    )
    return route to buildEnvelope
}
