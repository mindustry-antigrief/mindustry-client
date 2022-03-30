package mindustry.client.communication

import mindustry.client.utils.*
import java.math.*
import java.nio.*
import kotlin.random.*

class TLSDataTransmission : Transmission {

    val source: BigInteger
    val destination: BigInteger
    val content: ByteArray

    override val secureOnly = false

    constructor(source: BigInteger, destination: BigInteger, content: ByteArray) {
        this.source = source
        this.destination = destination
        this.content = content
        id = Random.nextLong()
    }

    override var id: Long

    constructor(input: ByteArray, id: Long, @Suppress("UNUSED_PARAMETER") senderID: Int) {
        this.id = id
        val buf = ByteBuffer.wrap(input)
        source = BigInteger(buf.bytes(buf.int))
        destination = BigInteger(buf.bytes(buf.int))
        content = buf.remainingBytes()
    }

    override fun serialize() = source.toByteArray().size.toBytes() + source.toByteArray() + destination.toByteArray().size.toBytes() + destination.toByteArray() + content
}
