package mindustry.client.communication

import mindustry.client.utils.bytes
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.random.Random

class TLSDataTransmission : Transmission {

    val source: BigInteger
    val destination: BigInteger
    val content: ByteArray

    constructor(source: BigInteger, destination: BigInteger, content: ByteArray) {
        this.source = source
        this.destination = destination
        this.content = content
        id = Random.nextLong()
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = ByteBuffer.wrap(input)
        source = BigInteger(buf.bytes(buf.int))
        destination = BigInteger(buf.bytes(buf.int))
        content = buf.remainingBytes()
    }

    override fun serialize() = source.toByteArray().size.toBytes() + source.toByteArray() + destination.toByteArray().size.toBytes() + destination.toByteArray() + content
}
