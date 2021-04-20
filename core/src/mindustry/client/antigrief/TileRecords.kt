package com.github.blahblahbloopster.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.client.ClientVars
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

        Events.on(EventType.BlockBuildEventTile::class.java) {
            if (it.newBlock == Blocks.air) {
                println("New break log")
                addLog(it.tile, TileBreakLog(it.tile, it.unit.toInteractor(), it.oldBlock))
            } else {
                println("New build log")
                addLog(it.tile, TilePlacedLog(it.tile, it.unit.toInteractor(), it.newBlock, it.config))
            }
        }

        Events.on(EventType.ConfigEvent::class.java) {
            println("New config log")
            addLog(it.tile.tile, ConfigureTileLog(it.tile.tile, it.player.toInteractor(), it.tile.block, it.tile.config()))
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            println("New pickup log")
            addLog(it.tile, BlockPayloadPickupLog(it.tile, it.unit.toInteractor(), it.building.block))
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            println("New dropoff log")
            addLog(it.tile, BlockPayloadDropLog(it.tile, it.unit.toInteractor(), it.building.block, it.building.config()))
        }
    }

    fun getLogs(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    fun getLogs(tile: Tile): TileRecord? = getLogs(tile.x.toInt(), tile.y.toInt())

    private fun addLog(tile: Tile, log: TileLog) {
        val logs = getLogs(tile) ?: run {
            println("Null logs")
            return
        }
        logs.add(log, tile)
    }
}
