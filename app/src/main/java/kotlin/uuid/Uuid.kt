package org.jetbrains.kotlinx.mcp.shared.util.uuid

import java.util.UUID

/**
 * Marker annotation for experimental UUID API
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This UUID API is experimental. It may be changed in the future without notice.")
public annotation class ExperimentalUuidApi

/**
 * A wrapper around java.util.UUID to provide a consistent API across platforms
 */
@ExperimentalUuidApi
public class Uuid(private val uuid: UUID) {
    /**
     * Creates a Uuid from a UUID string representation
     */
    @ExperimentalUuidApi
    public constructor(uuidString: String) : this(UUID.fromString(uuidString))
    
    /**
     * Returns the string representation of this UUID
     */
    override fun toString(): String = uuid.toString()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Uuid) return false
        return uuid == other.uuid
    }
    
    override fun hashCode(): Int = uuid.hashCode()
    
    companion object {
        /**
         * Creates a random UUID
         */
        @ExperimentalUuidApi
        public fun random(): Uuid = Uuid(UUID.randomUUID())
    }
} 