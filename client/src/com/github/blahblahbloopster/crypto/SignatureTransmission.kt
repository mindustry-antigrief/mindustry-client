package com.github.blahblahbloopster.crypto

import com.github.blahblahbloopster.communication.Transmission
import kotlin.random.Random

class SignatureTransmission : Transmission {

    override var id = Random.nextLong()
    val signature: ByteArray

    constructor(signature: ByteArray) {
        this.signature = signature
    }

    constructor(input: ByteArray, id: Long) {
        this.signature = input
        this.id = id
    }

    override fun serialize(): ByteArray {
        return signature
    }
}
