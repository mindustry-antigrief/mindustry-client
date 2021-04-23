package mindustry.client.communication

/** An interface for ways to transmit information between clients. */
abstract class CommunicationSystem {
    /** List of lambdas to run when a message is received. */
    protected abstract val listeners: MutableList<(input: ByteArray, sender: Int) -> Unit>
    /** This instance's ID. */
    abstract val id: Int
    /** The maximum number of bytes that can be sent at once. */
    abstract val MAX_LENGTH: Int
    /** The time in 1/60s to wait between transmissions. */
    abstract val RATE: Float

    /** Initializes the system. */
    open fun init() {}

    open fun clearListeners() {
        listeners.clear()
    }

    /** Sends a [ByteArray] to all other clients.  Note: this may take time. todo: consider moving to a queue system */
    abstract fun send(bytes: ByteArray)

    open fun addListener(listener: (input: ByteArray, sender: Int) -> Unit) {
        listeners.add(listener)
    }

    open fun addAllListeners(items: Collection<(input: ByteArray, sender: Int) -> Unit>) {
        listeners.addAll(items)
    }
}
