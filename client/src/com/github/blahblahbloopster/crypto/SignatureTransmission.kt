package com.github.blahblahbloopster.crypto

import com.github.blahblahbloopster.buffer
import com.github.blahblahbloopster.communication.Transmission
import com.github.blahblahbloopster.remainingBytes
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.random.Random

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
        this.time = Instant.ofEpochSecond(buf.long)
        this.signature = buf.remainingBytes()
    }

    override fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(Long.SIZE_BYTES + signature.size)
        buf.putLong(time.epochSecond)
        buf.put(signature)
        return buf.array()
    }
}
