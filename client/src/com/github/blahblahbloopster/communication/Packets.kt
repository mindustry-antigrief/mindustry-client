package com.github.blahblahbloopster.communication

import com.github.blahblahbloopster.crypto.CommunicationSystem
import com.github.blahblahbloopster.toBytes
import com.github.blahblahbloopster.toInstant
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.random.Random
import kotlin.reflect.KClass

object Packets {
    val registeredTransmissionTypes = listOf<RegisteredTransmission<*>>()

    data class RegisteredTransmission<T : Transmission>(val type: KClass<T>, val constructor: (ByteArray) -> T)

    private class Header {
        val sequenceCount: Int
        val sequenceNumber: Int
        val expirationTime: Instant
        val transmissionId: Long

        companion object {
            const val HEADER_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES + Long.SIZE_BYTES
        }

        constructor(sequenceCount: Int, sequenceNumber: Int, expirationTime: Instant, transmissionId: Long) {
            this.sequenceCount = sequenceCount
            this.sequenceNumber = sequenceNumber
            this.expirationTime = expirationTime
            this.transmissionId = transmissionId
        }

        constructor(array: ByteArray) {
            if (array.size < HEADER_SIZE) throw IllegalArgumentException("Input array is not long enough to be a packet header!")
            val buf = ByteBuffer.wrap(array)
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
            transmissionId = buf.long
        }

        constructor(buf: ByteBuffer) {
            if (buf.remaining() < HEADER_SIZE) throw IllegalArgumentException("Input buffer does not have enough remaining bytes to be a packet header!")
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
            transmissionId = buf.long
        }

        fun toBytes(): ByteArray {
            return sequenceCount.toBytes() + sequenceNumber.toBytes() + expirationTime.epochSecond.toBytes()
        }
    }

    class CommunicationClient(val communicationSystem: CommunicationSystem) {
        val timer = Timer()
        /** A queue of packets waiting to be sent. */
        private val outgoing = LinkedList<Packet>()

        init {

            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    updateSending()
                }
            }, 0L, communicationSystem.RATE)
        }

        fun updateSending() {
            val toSend = outgoing.poll() ?: return
            communicationSystem.send(toSend.bytes())
        }

        fun send(transmission: Transmission) {
            registeredTransmissionTypes.find { it.type == transmission::class }
                ?: throw IllegalArgumentException("Transmission type \"${transmission::class.simpleName}\" is not enrolled!")

            val usableBytesPerPacket = communicationSystem.MAX_LENGTH - Header.HEADER_SIZE

            val batches = transmission.serialize().toList().chunked(usableBytesPerPacket) { it.toByteArray() }
            val id = Random.nextLong()

            for ((index, content) in batches.withIndex()) {
                outgoing.add(Packet(content, batches.size, index, id))
            }
        }

        data class Packet(val content: ByteArray, val sequenceCount: Int, val sequenceNumber: Int, val transmissionId: Long) {

            fun bytes() = Header(
                sequenceCount,
                sequenceNumber,
                Instant.now().plus(5, ChronoUnit.SECONDS), transmissionId
            ).toBytes() + content

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Packet

                if (!content.contentEquals(other.content)) return false
                if (sequenceCount != other.sequenceCount) return false
                if (sequenceNumber != other.sequenceNumber) return false

                return true
            }

            override fun hashCode(): Int {
                var result = content.contentHashCode()
                result = 31 * result + sequenceCount
                result = 31 * result + sequenceNumber
                return result
            }

        }
    }
}
