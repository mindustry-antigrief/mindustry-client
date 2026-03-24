package mindustry.client.antigrief

import arc.*
import arc.util.io.*
import mindustry.*
import mindustry.client.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.io.*
import mindustry.world.*
import java.io.*
import java.time.*
import kotlin.experimental.*

/** Plugin integration for tile logs */
object NetworkTileLogs {
    private val inp = ReusableByteInStream()
    private val reads = Reads(DataInputStream(inp))
    private var pendingTiles: ByteArray? = null

    fun init() {
        Events.on(EventType.ServerJoinEvent::class.java) { // Enable logging with the specified version if supported
            if (!Server.current.ghost) Call.serverPacketReliable("fooTileLogs", "1")
        }

        Events.on(EventType.ResetEvent::class.java) { // Clear pendingTiles on join
            if (!ClientVars.syncing) pendingTiles = null
        }

        Vars.netClient.addBinaryPacketHandler("fooTileLogs") {
            pendingTiles = it
        }

        Vars.netClient.addBinaryPacketHandler("fooTileLog") {
            inp.setBytes(it)
            read(reads)
        }
    }

    /** Reads tile logs FINISHME: We need to make sure we're not doing this after a potential map transition if we join just before that happens */
    fun read(reads: Reads) {
        val t = TypeIO.readTile(reads)
        receiveTileLogs(t.array())
        if (TileInfoFragment.lastPos[0] == t.pos()) TileInfoFragment.lastPos[0] = -1 // Reset cached log display if that tile is updated
        val n = reads.b().toInt()
        repeat(n) { // Read n logs
            val interactor = if (reads.bool()) NetworkInteractor(reads.str(), reads.str(), reads.i()) else NoInteractor

            val id = reads.i() // FINISHME: Use this where it's actually needed

            // Time
            val sec = reads.l()
            val nano = reads.i()
            val time = Instant.now().minusNanos(sec * 1000000000L + nano)

            val type = reads.b().toInt()
            InteractionLog.read(type, reads, interactor).also {
                it.time = time
                it.id = id
                TileRecords[t]?.add(it, t)
            }
        }
    }

    /** Mark this tile as having received logs */
    fun receiveTileLogs(packed: Int) {
        val byte = packed shr 3
        val pending = pendingTiles ?: return
        if (byte >= pending.size) return // Ignore out of bounds tile
        pending[byte] = (pending[byte] and (1 shl (packed and 0x07)).inv().toByte())
    }

    /** Check if tile is waiting for logs */
    fun logsPending(t: Tile): Boolean {
        val packed = t.array()
        val byte = packed shr 3
        val pending = pendingTiles ?: return false
        if (byte >= pending.size) return false
        return (pending[byte].toInt() and (1 shl (packed and 0x07))) != 0
    }
}