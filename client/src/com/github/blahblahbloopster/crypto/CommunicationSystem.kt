package com.github.blahblahbloopster.crypto

/** An interface for ways to transmit information between clients. */
interface CommunicationSystem {
    /** List of lambdas to run when a message is received. */
    val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit>
    /** This instance's ID. */
    val id: Int
    /** The maximum number of bytes that can be sent at once. */
    val MAX_LENGTH: Int
    /** The time in 1/60s to wait between transmissions. */
    val RATE: Float

    /** Initializes the system. */
    fun init() {}

    /** Sends a [ByteArray] to all other clients.  Note: this may take time. todo: consider moving to a queue system */
    fun send(bytes: ByteArray)

    fun addListener(listener: (input: ByteArray, sender: Int) -> Unit) {
        listeners.add(listener)
    }
}
