package com.github.blahblahbloopster.communication

import com.github.blahblahbloopster.*
import com.github.blahblahbloopster.crypto.CommunicationSystem
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.reflect.KClass

object Packets {
    private val registeredTransmissionTypes = listOf<RegisteredTransmission<*>>(
        RegisteredTransmission(DummyTransmission::class) { input, id -> DummyTransmission(input, id) }
    )

    private data class RegisteredTransmission<T : Transmission>(val type: KClass<T>, val constructor: (content: ByteArray, id: Long) -> T)

    private class Header {
        val sequenceCount: Int
        val sequenceNumber: Int
        val expirationTime: Instant
        val transmissionId: Long
        val transmissionType: Int

        companion object {
            const val HEADER_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES
        }

        constructor(sequenceCount: Int, sequenceNumber: Int, expirationTime: Instant, transmissionId: Long, transmissionType: Int) {
            this.sequenceCount = sequenceCount
            this.sequenceNumber = sequenceNumber
            this.expirationTime = expirationTime
            this.transmissionId = transmissionId
            this.transmissionType = transmissionType
        }

        constructor(array: ByteArray) {
            if (array.size < HEADER_SIZE) throw IllegalArgumentException("Input array is not long enough to be a packet header!")
            val buf = ByteBuffer.wrap(array)
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
            transmissionId = buf.long
            transmissionType = buf.int
        }

        constructor(buf: ByteBuffer) {
            if (buf.remaining() < HEADER_SIZE) throw IllegalArgumentException("Input buffer does not have enough remaining bytes to be a packet header!")
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
            transmissionId = buf.long
            transmissionType = buf.int
        }

        fun toBytes(): ByteArray {
            return sequenceCount.toBytes() + sequenceNumber.toBytes() + expirationTime.epochSecond.toBytes() + transmissionId.toBytes() + transmissionType.toBytes()
        }
    }

    private data class Packet(val content: ByteArray, val sequenceCount: Int, val sequenceNumber: Int, val transmissionId: Long, val transmissionType: Int) {

        fun bytes() = Header(
            sequenceCount,
            sequenceNumber,
            Instant.now().plus(5, ChronoUnit.SECONDS), transmissionId, transmissionType
        ).toBytes() + content

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Packet

            if (!content.contentEquals(other.content)) return false
            if (sequenceCount != other.sequenceCount) return false
            if (sequenceNumber != other.sequenceNumber) return false
            if (transmissionId != other.transmissionId) return false
            if (transmissionType != other.transmissionType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = content.contentHashCode()
            result = 31 * result + sequenceCount
            result = 31 * result + sequenceNumber
            result = 31 * result + transmissionId.hashCode()
            result = 31 * result + transmissionType
            return result
        }
    }

    class CommunicationClient(val communicationSystem: CommunicationSystem) {
        private var lastSent: Instant = Instant.ofEpochSecond(0)
        /** A queue of packets waiting to be sent. */
        private val outgoing = LinkedList<Packet>()
        private val incoming = mutableMapOf<Long, MutableList<ByteArray?>>()
        val listeners = mutableListOf<(Transmission) -> Unit>()

        init {
            communicationSystem.addListener(::handle)
        }

        fun update() {
            if (lastSent.age(ChronoUnit.MILLIS) >= communicationSystem.RATE) {
                lastSent = Instant.now()

                val toSend = outgoing.poll() ?: return
                communicationSystem.send(toSend.bytes())
            }
        }

        fun handle(input: ByteArray, sender: Int) {
            if (sender == communicationSystem.id) return
            val buf = input.buffer()

            try {
                val header = Header(buf)
                val content = buf.remainingBytes()

                if (header.sequenceNumber >= header.sequenceCount) {
                    throw IndexOutOfBoundsException("Packet sequence number ${header.sequenceNumber} is greater than or equal to sequence count ${header.sequenceCount}!")
                }

                if (header.transmissionType >= registeredTransmissionTypes.size) throw IndexOutOfBoundsException("Transmission type ${header.transmissionType} not found!")

                if (header.sequenceCount > 500) {  // too big
                    incoming.remove(header.transmissionId)
                    return
                }

                val entry = incoming[header.transmissionId] ?: run {
                    if (incoming.size > 50) return@run null  // too many incoming connections
                    incoming[header.transmissionId] = MutableList(header.sequenceCount) { null }
                    return@run incoming[header.transmissionId]
                } ?: return

                entry[header.sequenceNumber] = content

                if (!entry.contains(null)) {
                    val transmission = registeredTransmissionTypes[header.transmissionType].constructor(entry.reduceRight { a, b -> a!! + b!! }!!, header.transmissionId)

                    for (listener in listeners) {
                        listener(transmission)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        fun send(transmission: Transmission) {
            registeredTransmissionTypes.find { it.type == transmission::class }
                ?: throw IllegalArgumentException("Transmission type \"${transmission::class.simpleName}\" is not enrolled!")

            val usableBytesPerPacket = communicationSystem.MAX_LENGTH - Header.HEADER_SIZE

            val batches = transmission.serialize().toList().chunked(usableBytesPerPacket) { it.toByteArray() }

            for ((index, content) in batches.withIndex()) {
                outgoing.add(Packet(content, batches.size, index, transmission.id, registeredTransmissionTypes.indexOfFirst { it.type == transmission::class }))
            }
        }
    }
}
