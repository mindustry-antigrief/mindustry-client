package mindustry.client.communication

import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.math.BigInteger
import java.nio.ByteBuffer
import kotlin.random.Random

class TlsRequestTransmission : Transmission {

    val destination: Int
    val sn: BigInteger

    constructor(destination: Int, sn: BigInteger) {
        this.destination = destination
        id = Random.nextLong()
        this.sn = sn
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = ByteBuffer.wrap(input)
        destination = buf.int
        sn = BigInteger(buf.remainingBytes())
    }

    override fun serialize() = destination.toBytes() + sn.toByteArray()
}
