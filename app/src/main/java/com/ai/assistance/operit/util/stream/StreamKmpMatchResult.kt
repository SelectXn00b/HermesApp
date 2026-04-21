package com.ai.assistance.operit.util.stream

/** Represents the outcome of processing a single character in a StreamKmpGraph. */
sealed class StreamKmpMatchResult {
    /** The character was not part of any match and the state was reset. */
    object NoMatch : StreamKmpMatchResult()

    /** The character is part of a potential ongoing match. */
    object InProgress : StreamKmpMatchResult()

    /** The character completed one or more matches. */
    data class Match(
            /** A map of group IDs to their captured content for any groups that completed. */
            val groups: Map<Int, String>,
            /** Indicates if the entire pattern was successfully matched. */
            val isFullMatch: Boolean
    ) : StreamKmpMatchResult()
}
