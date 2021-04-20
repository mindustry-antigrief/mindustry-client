package mindustry.client.crypto

import mindustry.client.communication.Transmission
import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.time.Instant
import kotlin.random.Random

class EncryptedMessageTransmission : Transmission {

    override var id = Random.nextLong()
    val ciphertext: ByteArray
    val timeSent: Instant

    constructor(signature: ByteArray, time: Instant = Instant.now()) {
        this.ciphertext = signature
        this.timeSent = time
    }

    constructor(input: ByteArray, id: Long) {
        val buf = input.buffer()
        this.timeSent = Instant.ofEpochSecond(buf.long)
        this.ciphertext = buf.remainingBytes()
        this.id = id
    }

    override fun serialize(): ByteArray {
        return timeSent.epochSecond.toBytes() + ciphertext
    }
}
