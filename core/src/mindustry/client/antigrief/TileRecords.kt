package mindustry.client.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.client.utils.dialog
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.world.Tile

object TileRecords {
    private var records: Array<Array<TileRecord>> = arrayOf(arrayOf())

    fun initialize() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                records = Array(Vars.world.width()) { x -> Array(Vars.world.height()) { y -> TileRecord(x, y) } }
            }
        }

        Events.on(EventType.BlockBuildBeginEventBefore::class.java) {
            if (it.newBlock == Blocks.air || it.newBlock == null) {
                addLog(it.tile, TileBreakLog(it.tile, it.unit.toInteractor(), it.tile.block()))
            } else {
                addLog(it.tile, TilePlacedLog(it.tile, it.unit.toInteractor(), it.newBlock, it.tile?.build?.config()))
            }
        }

        Events.on(EventType.ConfigEventBefore::class.java) {
            addLog(it.tile.tile, ConfigureTileLog(it.tile.tile, it.player.toInteractor(), it.tile.block, it.tile.config()))
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            addLog(it.tile, BlockPayloadPickupLog(it.tile, it.unit.toInteractor(), it.building.block))
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            addLog(it.tile, BlockPayloadDropLog(it.tile, it.unit.toInteractor(), it.building.block, it.building.config()))
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
