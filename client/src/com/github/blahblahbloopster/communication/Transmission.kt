package com.github.blahblahbloopster.communication

/** An arbitrary-length serializable container. */
interface Transmission {
    val id: Long

    fun serialize(): ByteArray
}
