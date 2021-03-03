package com.github.blahblahbloopster.communication

import kotlin.random.Random

class DummyTransmission(val content: ByteArray) : Transmission {

    override var id = Random.nextLong()

    constructor(input: ByteArray, id: Long) : this(input) {
        this.id = id
    }

    override fun serialize() = content
}
