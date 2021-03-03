package com.github.blahblahbloopster.communication

import kotlin.random.Random

class DummyTransmission(val content: ByteArray) : Transmission {

    override val id = Random.nextLong()

    override fun serialize() = content
}
