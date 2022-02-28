package mindustry.client.communication.syncing

import mindustry.client.communication.Packets
import mindustry.client.communication.Transmission
import mindustry.client.utils.buffer
import mindustry.client.utils.remainingBytes
import mindustry.client.utils.toBytes
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import kotlin.random.Random

/**
 * Keeps [list] synced between two instances.  Use [added] and [removed] to add or remove items.  The two instances must
 * be instantiated with the same [id] for it to work!
 */
class Syncer<T>(private val serializer: (T, DataOutputStream) -> Unit, private val deserializer: (DataInputStream) -> T?, private val comms: Packets.CommunicationClient, private val id: Long = Random.nextLong()) {
    private val internalList = mutableListOf<T>()
    val list: List<T> = internalList  // outside the class, appears as an immutable list
    var isDesynced = false

    private val queued = mutableListOf<SyncerT<T>>()

    fun added(items: List<Pair<T, Int>>) {
        items.forEach { internalList.add(it.second, it.first) }
        val hash = crc(internalList)

        if (queued.lastOrNull() is SyncerT.AddT) {
            val t = queued.last() as SyncerT.AddT<T>
            t.newObjects.addAll(items)
            t.hash = hash
        }

        queued.add(SyncerT.AddT(items.toMutableList(), false, hash))
    }

    fun clear() {
        internalList.clear()
        val hash = crc(internalList)

        queued.add(SyncerT.AddT(mutableListOf(), true, hash))
    }

    fun removed(indices: List<Int>) {
        indices.forEach { internalList.removeAt(it) }
        val hash = crc(internalList)

        if (queued.lastOrNull() is SyncerT.RemoveT) {
            val t = queued.last() as SyncerT.RemoveT<T>
            t.remove.addAll(indices)
            t.hash = hash
        }

        queued.add(SyncerT.RemoveT(indices.toMutableList(), hash))
    }

    fun update() {
        if (isDesynced) queued.removeAll { it !is SyncerT.RequestT<T> }
        for (item in queued) {
            println("Sending $item...")
            comms.send(SyncerTransmission(id, item, serializer as (Any?, DataOutputStream) -> Unit))
        }
        queued.clear()
    }

    init {
        comms.addListener { transmission, _ ->
            if (transmission !is SyncerTransmission) return@addListener
            if (transmission.syncID != id) return@addListener

            val syncT = transmission.deserialize(serializer, deserializer) ?: return@addListener

            if (syncT is SyncerT.RequestT) {
                queued.add(SyncerT.AddT(internalList.zip(internalList.indices).toMutableList(), true, crc(internalList)))
                return@addListener
            }

            if (isDesynced && (syncT as? SyncerT.AddT)?.clear == true) {
                isDesynced = false
            }

            syncT.apply(internalList)

            val hash = crc(internalList)
            if (hash != syncT.hash) {
                println("Desynced, requesting...")
                isDesynced = true
                queued.add(SyncerT.RequestT())
            } else {
                println("Successfully applied '$syncT'")
            }
        }
    }

    private val crc = CRC32()

    private fun crc(items: List<T>): Int {
        crc.reset()
        items.forEach {
            crc.update(it.hashCode())
        }
        return crc.value.toInt()
    }

    class SyncerTransmission : Transmission {

        override var id = Random.nextLong()
        override val secureOnly: Boolean = false
        val content: ByteArray
        var syncID: Long

        constructor(input: ByteArray, id: Long, @Suppress("UNUSED_PARAMETER") senderID: Int) {
            this.id = id
            val buf = input.buffer()
            syncID = buf.long
            content = buf.remainingBytes()
        }

        constructor(syncID: Long, syncer: SyncerT<*>, serializer: (Any?, DataOutputStream) -> Unit) {
            this.syncID = syncID
            content = byteArrayOf(syncer.typeByte) + syncer.hash.toBytes() + syncer.serialize(serializer)
        }

        override fun serialize() = syncID.toBytes() + content

        fun <T> deserialize(serializer: (T, DataOutputStream) -> Unit, deserializer: (DataInputStream) -> T?): SyncerT<T>? {
            val inp = DataInputStream(content.inputStream())
            val typeByte = inp.readByte().toInt()
            val hash = inp.readInt()

            return when (typeByte) {
                0 -> {
                    val num = inp.readInt()
                    val lst = mutableListOf<Pair<T, Int>>()

                    repeat(num) {
                        lst.add(Pair(deserializer(inp) ?: return null, inp.readInt()))
                    }

                    SyncerT.AddT(lst, inp.readBoolean(), hash)
                }
                1 -> {
                    val num = inp.readInt()
                    val lst = mutableListOf<Int>()

                    repeat(num) {
                        lst.add(inp.readInt())
                    }

                    SyncerT.RemoveT(lst, hash)
                }
                2 -> SyncerT.RequestT()
                else -> null
            }
        }
    }

    interface SyncerT<T> {
        val hash: Int
        val typeByte: Byte

        fun apply(list: MutableList<T>)

        fun serialize(serializer: (T, DataOutputStream) -> Unit): ByteArray

        data class AddT<T>(val newObjects: MutableList<Pair<T, Int>>, val clear: Boolean, override var hash: Int) : SyncerT<T> {
            override val typeByte = 0.toByte()

            override fun apply(list: MutableList<T>) {
                if (clear) list.clear()
                for (item in newObjects) {
                    list.add(item.second, item.first)
                }
            }

            override fun serialize(serializer: (T, DataOutputStream) -> Unit): ByteArray {
                val out = ByteArrayOutputStream()
                val data = DataOutputStream(out)

                data.writeInt(newObjects.size)
                for (item in newObjects) {
                    serializer(item.first, data)
                    data.writeInt(item.second)
                }
                data.writeBoolean(clear)
                data.flush()
                return out.toByteArray()
            }
        }

        data class RemoveT<T>(val remove: MutableList<Int>, override var hash: Int) : SyncerT<T> {
            override val typeByte = 1.toByte()

            override fun apply(list: MutableList<T>) {
                for (item in remove) {
                    list.removeAt(item)
                }
            }

            override fun serialize(serializer: (T, DataOutputStream) -> Unit): ByteArray {
                val out = ByteArrayOutputStream()
                val data = DataOutputStream(out)

                data.writeInt(remove.size)
                for (item in remove) {
                    data.writeInt(item)
                }
                data.flush()
                return out.toByteArray()
            }
        }

        class RequestT<T> : SyncerT<T> {
            override val hash = -1
            override val typeByte = 2.toByte()

            override fun apply(list: MutableList<T>) {
                return
            }

            override fun serialize(serializer: (T, DataOutputStream) -> Unit): ByteArray {
                return byteArrayOf()
            }
        }
    }
}
