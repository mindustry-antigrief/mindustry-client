package mindustry.client.communication

import mindustry.client.utils.*
import java.math.*
import java.nio.*
import kotlin.random.*

class TlsRequestTransmission : Transmission {

    val sourceSN: BigInteger
    val destinationSN: BigInteger
    val isResponse get() = sourceSN == BigInteger.ZERO && destinationSN == BigInteger.ZERO

    override val secureOnly = false

    constructor(source: BigInteger, destination: BigInteger) {
        this.sourceSN = source
        this.destinationSN = destination
        id = Random.nextLong()
    }

    override var id: Long

    constructor(input: ByteArray, id: Long, senderID: Int) {
        this.id = id
        if (input.isEmpty()) {
            sourceSN = BigInteger.ZERO
            destinationSN = BigInteger.ZERO
            return
        }
        val buf = ByteBuffer.wrap(input)
        val size = buf.int
        sourceSN = BigInteger(buf.bytes(size))
        destinationSN = BigInteger(buf.remainingBytes())
    }

    override fun serialize() = if (isResponse) byteArrayOf() else sourceSN.toByteArray().run { size.toBytes() + this } + destinationSN.toByteArray()
}
