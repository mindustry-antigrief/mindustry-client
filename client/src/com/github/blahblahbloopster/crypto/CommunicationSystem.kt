package com.github.blahblahbloopster.crypto

/** An interface for ways to transmit information between clients. */
interface CommunicationSystem {
    /** List of lambdas to run when a message is received. */
    val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit>
    /** This instance's ID. */
    val id: Int

    /** Initializes the system. */
    fun init() {}

    /** Sends a [ByteArray] to all other clients.  Note: this may take time. todo: consider moving to a queue system */
    fun send(bytes: ByteArray)
}
