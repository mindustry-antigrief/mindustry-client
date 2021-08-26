package mindustry.client.communication

import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.nio.ByteBuffer
import kotlin.random.Random

class TLSDataTransmission : Transmission {

    val destination: Int
    val content: ByteArray

    val isRequest get() = content.isEmpty()

    constructor(destination: Int, content: ByteArray) {
        this.destination = destination
        this.content = content
        id = Random.nextLong()
    }

    constructor(destination: Int) {
        this.destination = destination
        content = byteArrayOf()
        id = Random.nextLong()
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = ByteBuffer.wrap(input)
        destination = buf.int
        content = buf.remainingBytes()
    }

    override fun serialize() = destination.toBytes() + content
}
