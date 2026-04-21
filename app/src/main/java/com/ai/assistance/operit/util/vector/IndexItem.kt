package com.ai.assistance.operit.util.vector

import com.github.jelmerk.hnswlib.core.Item

/**
 * A generic wrapper for any object that needs to be stored in an HNSW index.
 * It holds a unique ID, a vector embedding, and a generic reference to the original object.
 *
 * @param T The type of the object being indexed.
 */
data class IndexItem<Id : Any, T>(
    private val id: Id,
    private val vector: FloatArray,
    private val version: Long = 0L,
    val value: T // Generic reference to the original object (e.g., Memory or DocumentChunk)
) : Item<Id, FloatArray> {

    override fun id(): Id = id
    override fun vector(): FloatArray = vector
    override fun dimensions(): Int = vector.size
    override fun version(): Long = version

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IndexItem<*, *>
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
} 
