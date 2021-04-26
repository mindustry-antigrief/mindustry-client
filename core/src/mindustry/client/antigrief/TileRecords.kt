package mindustry.client.antigrief

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.antigrief.TileLog.Companion.linkedArea
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.world.*

object TileRecords {
    private var records: Array<Array<TileRecord>> = arrayOf(arrayOf())

    fun initialize() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                records = Array(Vars.world.width()) { x -> Array(Vars.world.height()) { y -> TileRecord(x, y) } }
            }
        }

        Events.on(EventType.BlockBuildBeginEventBefore::class.java) {
            if (it.newBlock == null || it.newBlock == Blocks.air) {
                it.tile.getLinkedTiles { tile ->
                    addLog(tile, TileBreakLog(tile, it.unit.toInteractor(), tile.block()))
                }
            } else {
                forArea(it.tile, it.newBlock.size) { tile ->
                    addLog(tile, TilePlacedLog(tile, it.unit.toInteractor(), it.newBlock, tile.build?.config()))
                }
            }
        }

        Events.on(EventType.ConfigEventBefore::class.java) {
            forArea(it.tile.tile) { tile ->
                addLog(tile, ConfigureTileLog(tile, it.player.toInteractor(), tile.block(), it.value))
            }
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            forArea(it.tile) { tile ->
                addLog(tile, BlockPayloadPickupLog(tile, it.unit.toInteractor(), it.building.block))
            }
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            forArea(it.tile, it.building.block.size) { tile ->
                addLog(tile, BlockPayloadDropLog(tile, it.unit.toInteractor(), it.building.block, it.building.config()))
            }
        }

        Events.on(EventType.BlockDestroyEvent::class.java) {
            forArea(it.tile) { tile ->
                addLog(tile, TileDestroyedLog(tile, tile.block()))
            }
        }
    }

    private inline fun forArea(tile: Tile, size: Int = tile.block().size, block: (Tile) -> Unit) {
        for (point in linkedArea(tile, size)) {
            if (point in Vars.world) {
                block(Vars.world[point])
            }
        }
    }

    operator fun get(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    operator fun get(tile: Tile): TileRecord? = this[tile.x.toInt(), tile.y.toInt()]

    private fun addLog(tile: Tile, log: TileLog) {
        val logs = this[tile] ?: run {
            println("Null logs")
            return
        }
        logs.add(log, tile)
    }

    fun show(tile: Tile) {
        dialog("Logs") {
            add(TileRecords[tile]?.toElement())
            addCloseButton()
        }.show()
    }
}
