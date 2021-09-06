package mindustry.client.communication

import mindustry.client.crypto.Signatures
import mindustry.client.utils.buffer
import mindustry.client.utils.bytes
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.math.BigInteger
import kotlin.random.Random

class SignatureTransmission : Transmission {

    val signature: ByteArray
    val sn: BigInteger
    val time: Long
    val senderId: Int
    val messageId: Short

    override val secureOnly = false

    constructor(signature: ByteArray, sn: BigInteger, time: Long, senderId: Int, messageId: Short) {
        this.signature = signature
        this.sn = sn
        this.id = Random.nextLong()
        this.time = time
        this.senderId = senderId
        this.messageId = messageId
    }

    override var id: Long

    constructor(input: ByteArray, id: Long) {
        this.id = id
        val buf = input.buffer()
        signature = buf.bytes(Signatures.SIGNATURE_LENGTH)
        time = buf.long
        senderId = buf.int
        messageId = buf.short
        sn = BigInteger(buf.remainingBytes())
    }

    override fun serialize() = signature + time.toBytes() + senderId.toBytes() + messageId.toBytes() + sn.toByteArray()

    fun toSignable(original: ByteArray) = original + sn.toByteArray() + time.toBytes() + messageId.toBytes() + senderId.toBytes()
}
