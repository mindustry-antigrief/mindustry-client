package mindustry.client.communication

import kotlin.random.*

class DummyTransmission(val content: ByteArray) : Transmission {

    override var id = Random.nextLong()
    override val secureOnly: Boolean = false

    constructor(input: ByteArray, id: Long, senderID: Int) : this(input) {
        this.id = id
    }

    override fun serialize() = content
}
