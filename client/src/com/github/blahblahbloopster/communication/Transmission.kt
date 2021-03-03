package com.github.blahblahbloopster.communication

/** An arbitrary-length serializable container. */
interface Transmission {

    fun serialize(): ByteArray
}
