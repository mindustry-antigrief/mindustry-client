package mindustry.client.communication

import kotlin.random.Random

class MessageTransmission(val content: String) : Transmission {

    override var id = Random.nextLong()
    override val secureOnly: Boolean = true

    constructor(input: ByteArray, id: Long) : this(input.decodeToString()) {
        this.id = id
    }

    override fun serialize() = content.encodeToByteArray()
}
