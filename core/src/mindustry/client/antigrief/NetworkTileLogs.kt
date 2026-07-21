package mindustry.client.antigrief

import arc.*
import arc.struct.*
import arc.util.*
import arc.util.io.*
import mindustry.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.io.*
import mindustry.world.*
import java.io.*
import java.time.*

/** Plugin integration for tile logs */
object NetworkTileLogs {
    private val inp = ReusableByteInStream()
    private val reads = Reads(DataInputStream(inp))
    private var upToDateTiles: Bits? = null
    private var lastBatchStart = 0
    private var wasSameMap = false

    /** Runs when the world Loads. Before ServerJoinEvent is handled. */
    fun onWorldLoad(sameMap: Boolean) {
        wasSameMap = sameMap // ServerJoinEvent only asks the server for new tiles when wasSameMap is false
        if (!sameMap) upToDateTiles = null // Clear up-to-date tiles on world load if the map is different
    }

    fun init() {
        Events.on(EventType.ServerJoinEvent::class.java) { // Enable logging with the specified version if supported
            if (Server.current.networkTileLogs) { // Request logs FINISHME: Add a setting to toggle this functionality
                Call.serverPacketReliable("fooTileLogs", "2")
                lastBatchStart = 0
            }
            // FINISHME: If wasSameMap and logs are enabled, request only the
        }

        Vars.netClient.addBinaryPacketHandler("fooTileLogs") { batch -> // Incoming batch: 1 = has logs. Up-to-date tiles: 1 = no logs pending.
            if (upToDateTiles == null) upToDateTiles = Bits(Vars.world.tiles.size())

            val upToDateTiles = upToDateTiles!!
            val size = Vars.world.tiles.size()

            for (i in batch.indices) { // Iterate bytes
                val byte = batch[i].toInt() and 0xFF
                val byteStart = lastBatchStart + (i shl 3) // i * 8

                if (byte == 0xFF) continue // Opt: All bits 1; all tiles have logs. Don't do any work.

                if (byte == 0) { // Opt: All bits 0; no tiles have logs. Set all to 1
                    upToDateTiles.set(byteStart, minOf(byteStart + 8, size))
                    continue
                }

                for (bit in 0..7) { // Mixed bits
                    val hasLogs = (byte and (1 shl bit)) != 0
                    if (!hasLogs && byteStart + bit < size) upToDateTiles.set(byteStart + bit)
                }
            }

            lastBatchStart += batch.size * 8
            Log.debug("Received network logs until $lastBatchStart")
        }

        Vars.netClient.addBinaryPacketHandler("fooTileLog") {
            inp.setBytes(it)
            runCatching { read(reads) }.onFailure { e -> Log.err("Error reading fooTileLog packet", e) }
        }
    }

    /** Reads tile logs FINISHME: We need to make sure we're not doing this after a potential map transition if we join just before that happens */
    fun read(reads: Reads) {
        val tileCount = reads.b().toInt()
        val upToDate = upToDateTiles ?: return
        repeat(tileCount) {
            val t = TypeIO.readTile(reads)
            if (upToDate.getAndSet(t.array())) return@repeat // Skip tiles that are already up to date
            if (TileInfoFragment.lastPos[0] == t.pos()) TileInfoFragment.lastPos[0] = -1 // Reset cached log display if that tile is updated
            val n = reads.b().toInt()
            repeat(n) { // Read n logs
                val interactor = if (reads.bool()) NetworkInteractor(reads.str(), reads.str(), reads.i()) else NoInteractor

                // Time
                val sec = reads.l()
                val nano = reads.i()
                val time = Instant.now().minusNanos(sec * 1000000000L + nano)

                val type = reads.b().toInt()
                InteractionLog.read(type, reads, interactor).also {
                    it.time = time
                    TileRecords[t]?.add(it, t)
                }
            }
        }
    }

    /** Check if tile is waiting for logs */
    fun logsPending(t: Tile): Boolean {
        val upToDate = upToDateTiles ?: return false
        val arrayPos = t.array()
        return !upToDate[arrayPos]
    }
}