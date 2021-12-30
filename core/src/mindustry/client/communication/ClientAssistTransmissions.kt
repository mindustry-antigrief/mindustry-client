package mindustry.client.communication

import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.client.utils.buffer
import mindustry.client.utils.int
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import mindustry.entities.units.BuildPlan
import mindustry.io.TypeIO
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random

private val buf = ByteBuffer.allocate(Int.SIZE_BYTES * 3 + Short.SIZE_BYTES + 1 + Int.SIZE_BYTES).apply { mark() }
private val longBuf = ByteBuffer.allocate(Long.SIZE_BYTES).apply { mark() }
private val crc = CRC32()

// Zero allocation, but nonetheless maybe slow
private fun checksum(queue: List<BuildPlan>): Long {
    crc.reset()
    for (plan in queue) {
        buf.reset()
        buf.putInt(plan.x)
        buf.putInt(plan.y)
        buf.putInt(plan.rotation)
        buf.putShort(plan.block?.id ?: 0)
        buf.put(plan.breaking.int.toByte())
        buf.putInt(plan.config?.hashCode() ?: 0)

        crc.update(buf.array())
    }
    return crc.value
}

class AddRemove : Transmission {
    override var id: Long
    override val secureOnly = false
    val plans: Array<BuildPlan>
    val checksum: Long
    val adding: Boolean
    val clear: Boolean
    val destination: Int

    constructor(plans: Array<BuildPlan>, adding: Boolean, clear: Boolean, destination: Int, checksum: Long) {
        this.plans = plans
        this.checksum = checksum
        this.id = Random.nextLong()
        this.adding = adding
        this.clear = clear
        this.destination = destination
    }

    constructor(input: ByteArray, id: Long, senderID: Int) {
        val buf = input.buffer()
        checksum = buf.long
        adding = buf.get() != 0.toByte()
        clear = buf.get() != 0.toByte()
        destination = buf.int
        plans = TypeIO.readRequests(Reads(DataInputStream(ByteArrayInputStream(buf.remainingBytes()))))
        this.id = id

//        val inp = Reads(DataInputStream(ByteArrayInputStream(input, 14, input.size - 14)))
//        plans = TypeIO.readRequests(inp)
//        longBuf.position(0)
//        longBuf.put(input, 0, 8)
//        longBuf.flip()
//        checksum = longBuf.long
//        this.id = id
//        adding = input[8] != 0.toByte()
//        clear = input[9] != 0.toByte()
//        longBuf.position(0)
//        longBuf.put(input, 10, 4)
//        longBuf.flip()
//        destination = longBuf.int
    }

    private companion object {
        val out = ByteArrayOutputStream()
    }

    override fun serialize(): ByteArray {
        out.reset()
        val writes = Writes(DataOutputStream(out))
        TypeIO.writeRequests(writes, plans)
        return checksum.toBytes() + adding.int.toByte() + clear.int.toByte() + destination.toBytes() + out.toByteArray()
    }
}

class SignalingTransmission(val destination: Int, val type: Type) : Transmission {
    override var id = Random.nextLong()
    override val secureOnly = false

    constructor(input: ByteArray, id: Long, senderID: Int) : this(kotlin.run {
        longBuf.position(0)
        longBuf.put(input)
        longBuf.flip()
        longBuf.int
    }, Type.values()[longBuf.int])

    override fun serialize() = destination.toBytes() + type.ordinal.toBytes()

    enum class Type {
        START, RESEND, STOP
    }
}

class ClientAssistManager(val playerID: Int, val comms: Packets.CommunicationClient, val isSending: Boolean) {
    var isDone = false

    fun received(transmission: Transmission, q: MutableList<BuildPlan>) {
        when (transmission) {
            is AddRemove -> {
                if (isSending || transmission.destination != comms.communicationSystem.id) return
                if (transmission.clear) q.clear()
                if (transmission.adding) q.addAll(transmission.plans) else q.removeAll(transmission.plans.toSet())
                val checksum = checksum(q)
                if (checksum != transmission.checksum) comms.send(SignalingTransmission(playerID, SignalingTransmission.Type.RESEND))
            }

            is SignalingTransmission -> {
                if (transmission.destination != comms.communicationSystem.id) return
                when (transmission.type) {
                    SignalingTransmission.Type.START -> {}
                    SignalingTransmission.Type.RESEND -> {
                        val checksum = checksum(q)
                        if (isSending) comms.send(AddRemove(q.toTypedArray(), adding = true, clear = true, playerID, checksum))
                    }
                    SignalingTransmission.Type.STOP -> isDone = true
                }
            }
        }
    }

    /** Assumes that q has already been modified. */
    fun plansAddedRemoved(plans: List<BuildPlan>, added: Boolean, q: MutableList<BuildPlan>) {
        if (isDone) return
        comms.send(AddRemove(plans.toTypedArray(), added, clear = false, playerID, checksum(q)))
    }
}
