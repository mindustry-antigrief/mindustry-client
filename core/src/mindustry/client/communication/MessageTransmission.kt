package mindustry.client.communication

import kotlin.random.*

class MessageTransmission(val content: String) : Transmission {

    override var id = Random.nextLong()
    override val secureOnly: Boolean = true

    constructor(input: ByteArray, id: Long, @Suppress("UNUSED_PARAMETER") senderID: Int) : this(input.decodeToString()) {
        this.id = id
    }

    override fun serialize() = content.encodeToByteArray()
}
