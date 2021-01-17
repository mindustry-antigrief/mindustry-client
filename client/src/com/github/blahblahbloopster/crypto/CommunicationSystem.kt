package com.github.blahblahbloopster.crypto

/** An interface for ways to transmit information between clients. */
interface CommunicationSystem {
    /** List of lambdas to run when a message is received. */
    val listeners: MutableList<(ByteArray) -> Unit>

    /** Sends a [ByteArray] to all other clients.  Note: this may take time. todo: consider moving to a queue system */
    fun send(bytes: ByteArray)
}
