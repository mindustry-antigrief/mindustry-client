package com.github.blahblahbloopster.communication

import com.github.blahblahbloopster.toBytes
import com.github.blahblahbloopster.toInstant
import java.nio.ByteBuffer
import java.time.Instant

object Packets {

    class Header {
        val sequenceCount: Int
        val sequenceNumber: Int
        val expirationTime: Instant

        constructor(sequenceCount: Int, sequenceNumber: Int, expirationTime: Instant) {
            this.sequenceCount = sequenceCount
            this.sequenceNumber = sequenceNumber
            this.expirationTime = expirationTime
        }

        constructor(array: ByteArray) {
            val buf = ByteBuffer.wrap(array)
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
        }

        fun toBytes(): ByteArray {
            return sequenceCount.toBytes() + sequenceNumber.toBytes() + expirationTime.epochSecond.toBytes()
        }
    }
}
