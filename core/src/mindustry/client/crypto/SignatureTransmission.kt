package mindustry.client.crypto

import mindustry.client.communication.*
import mindustry.client.utils.*
import java.nio.*
import java.time.*
import kotlin.random.*

class SignatureTransmission : Transmission {

    override var id = Random.nextLong()
    val signature: ByteArray  // ED25519 signature
    val time: Instant  // Instant that it was signed at, required for validation

    constructor(signature: ByteArray, time: Instant) {
        this.signature = signature
        this.time = time
    }

    constructor(input: ByteArray, id: Long) {
        val buf = input.buffer()
        this.id = id
        this.time = try { Instant.ofEpochSecond(buf.long) } catch (_: Exception) { Instant.ofEpochSecond(0) }
        this.signature = buf.remainingBytes()
    }

    override fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(Long.SIZE_BYTES + signature.size)
        buf.putLong(time.epochSecond)
        buf.put(signature)
        return buf.array()
    }
}
