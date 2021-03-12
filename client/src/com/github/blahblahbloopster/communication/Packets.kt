package com.github.blahblahbloopster.communication

import arc.util.Interval
import com.github.blahblahbloopster.*
import com.github.blahblahbloopster.crypto.*
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object Packets {
    /** The list of registered types of [Transmission].  Transmissions MUST be registered here before use. */
    private val registeredTransmissionTypes = listOf<RegisteredTransmission<*>>(
        RegisteredTransmission(DummyTransmission::class, ::DummyTransmission),
        RegisteredTransmission(SignatureTransmission::class, ::SignatureTransmission),
        RegisteredTransmission(EncryptedMessageTransmission::class, ::EncryptedMessageTransmission),
        RegisteredTransmission(BuildQueueTransmission::class, ::BuildQueueTransmission)
    )

    private data class RegisteredTransmission<T : Transmission>(val type: KClass<T>, val constructor: (content: ByteArray, id: Long) -> T)

    private class Header {
        /** The total number of packets that make up this transmission. */
        val sequenceCount: Int
        /** This packet's index in the sequence. */
        val sequenceNumber: Int
        /** The time at which this packet is no longer valid. */
        val expirationTime: Instant
        /** The ID of the [Transmission] it is part of. */
        val transmissionId: Long
        /** The type of [Transmission] it is part of. */
        val transmissionType: Int

        companion object {
            const val HEADER_SIZE = Int.SIZE_BYTES + Int.SIZE_BYTES + Long.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES
        }

        /**
         * Constructs a header with the given properties.
         * @param sequenceCount The total number of packets that make up this transmission.
         * @param sequenceNumber This packet's index in the sequence.
         * @param expirationTime The time at which this packet is no longer valid.
         * @param transmissionId The ID of the [Transmission] it is part of.
         * @param transmissionType The type of [Transmission] it is part of.
         */
        constructor(sequenceCount: Int, sequenceNumber: Int, expirationTime: Instant, transmissionId: Long, transmissionType: Int) {
            this.sequenceCount = sequenceCount
            this.sequenceNumber = sequenceNumber
            this.expirationTime = expirationTime
            this.transmissionId = transmissionId
            this.transmissionType = transmissionType
        }

        /** Deserializes a header.  Compatible with [toBytes]. */
        constructor(array: ByteArray) {
            if (array.size < HEADER_SIZE) throw IllegalArgumentException("Input array is not long enough to be a packet header!")
            val buf = ByteBuffer.wrap(array)
            sequenceCount = buf.int
            sequenceNumber = buf.int
            expirationTime = buf.long.toInstant()
            transmissionId = buf.long
            transmissionType = buf.int
        }

        /** Pulls the required bytes out of the given buffer. */
        constructor(buf: ByteBuffer) : this(buf.bytes(HEADER_SIZE))

        /** Encodes this header to bytes, compatible with the byte array and buffer constructors. */
        fun toBytes(): ByteArray {
            return sequenceCount.toBytes() + sequenceNumber.toBytes() + expirationTime.epochSecond.toBytes() + transmissionId.toBytes() + transmissionType.toBytes()
        }
    }

    /** Represents a segment of a [Transmission].  Do not use directly. */
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

    /**
     * Handles sending and receiving [Transmission]s on a [CommunicationSystem].
     * There should only be one of these per communication system to avoid exceeding the rate.
     */
    class CommunicationClient(val communicationSystem: CommunicationSystem) {
        val inUse get() = !lastSent.check(0, 60f) // 1s

        /** The time that the last packet was sent at. */
        private var lastSent = Interval()
        /** A queue of packets waiting to be sent. */
        private val outgoing = LinkedList<OutgoingTransmission>()
        /** A list of incoming connections.  Each transmission ID is mapped to a nullable list of bytearray segments. */
        private val incoming = ConcurrentHashMap<Long, IncomingTransmission>()
        /** A list of listeners to be run when a transmission is received. */
        val listeners = mutableListOf<(transmission: Transmission, senderId: Int) -> Unit>()

        data class IncomingTransmission(val segments: MutableList<ByteArray?>, var expirationTime: Instant)

        init {
            communicationSystem.addListener(::handle)
        }

        fun addListener(listener: (transmission: Transmission, senderId: Int) -> Unit) {
            listeners.add(listener)
        }

        /** Updates sending.  Call once per tick. */
        fun update() {
            if (lastSent.check(0, communicationSystem.RATE)) {
                val toSend = outgoing.peek() ?: return // Return if there's nothing to send

                // Gets the next packet in this transmission, if there are no more packets move to the next transmission
                val packet = toSend.packets.poll() ?: run { outgoing.remove(toSend); toSend.onFinish?.invoke(); return }

                lastSent.reset(0, 0f) // Sending a packet, reset the timer fully
                try { communicationSystem.send(packet.bytes()) } catch (e: IOException) { outgoing.remove(toSend); toSend.onError?.invoke() }
            }
            for (inc in incoming) {
                if (inc.value.expirationTime.isBefore(Instant.now())) {
                    incoming.remove(inc.key)
                }
            }
        }

        /** Handles an incoming packet. */
        private fun handle(input: ByteArray, sender: Int) {
//            if (sender == communicationSystem.id) return
            val buf = input.buffer()

            try {
                val header = Header(buf)
                val content = buf.remainingBytes()

                if (header.sequenceNumber >= header.sequenceCount)
                    throw IndexOutOfBoundsException("Packet sequence number ${header.sequenceNumber} " +
                            "is greater than or equal to sequence count ${header.sequenceCount}!")

                if (header.transmissionType >= registeredTransmissionTypes.size)
                    throw IndexOutOfBoundsException("Transmission type ${header.transmissionType} not found!")

                if (header.sequenceCount > 500) {  // too many packets
                    incoming.remove(header.transmissionId)
                    return
                }

                if (header.expirationTime.isBefore(Instant.now())) {
                    incoming.remove(header.transmissionId)
                    return  // too old
                }

                val entry = incoming[header.transmissionId] ?: run {
                    if (incoming.size > 50) return@run null  // too many incoming connections
                    incoming[header.transmissionId] = IncomingTransmission(MutableList(header.sequenceCount) { null }, Instant.now().plusSeconds(15))  // Create new incoming connection entry
                    return@run incoming[header.transmissionId]
                } ?: return

                entry.segments[header.sequenceNumber] = content
                entry.expirationTime = Instant.now().plusSeconds(15)

                if (!entry.segments.contains(null)) {
                    val array = entry.segments.reduceRight { a, b -> a!! + b!! }!!  // Collapse the list of packet contents to the full byte array
                    val inflated = array.inflate()  // Decompress the transmission
                    val transmission = registeredTransmissionTypes[header.transmissionType].constructor(inflated, header.transmissionId)  // Deserialize the transmission

                    for (listener in listeners) {
                        listener(transmission, sender)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        private data class OutgoingTransmission(val packets: Queue<Packet>, val onFinish: (() -> Unit)?, val onError: (() -> Unit)?)

        /**
         * Splits the transmission into packets and queues them for sending.
         * @param transmission The transmission to be sent.
         * @param onFinish A lambda that will be run once it is sent, null by default.
         */
        fun send(transmission: Transmission, onFinish: (() -> Unit)? = null, onError: (() -> Unit)? = null) {
            val type = registeredTransmissionTypes.indexOfFirst { it.type == transmission::class }

            if (type == -1)
                throw IllegalArgumentException("Transmission type \"${transmission::class.simpleName}\" is not enrolled!")

            val usableBytesPerPacket = communicationSystem.MAX_LENGTH - Header.HEADER_SIZE

            // Compress the transmission and chunk it so it
            val batches = transmission.serialize().compress().toList().chunked(usableBytesPerPacket) { it.toByteArray() }

            val packets = LinkedList<Packet>()
            for ((index, content) in batches.withIndex()) {
                packets.add(Packet(content, batches.size, index, transmission.id, type))
            }

            outgoing.add(OutgoingTransmission(packets, onFinish, onError))
        }
    }
}
