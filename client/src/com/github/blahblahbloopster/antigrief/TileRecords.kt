package com.github.blahblahbloopster.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.world.Tile

object TileRecords {
    private lateinit var records: Array<Array<TileRecord>>

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                records = Array(Vars.world.height()) { Array(Vars.world.width()) { TileRecord() } }
            }
        }

        Events.on(EventType.BlockBuildEventTile::class.java) {
            if (it.newBlock == Blocks.air) {
                addLog(it.tile, TileBreakLog(it.tile, it.unit.toInteractor(), it.oldBlock))
            } else {
                addLog(it.tile, TilePlacedLog(it.tile, it.unit.toInteractor(), it.newBlock, it.config))
            }
        }

        Events.on(EventType.ConfigEvent::class.java) {
            addLog(it.tile.tile, ConfigureTileLog(it.tile.tile, it.player.toInteractor(), it.tile.block, it.tile.config()))
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            addLog(it.tile, BlockPayloadPickupLog(it.tile, it.unit.toInteractor(), it.building.block))
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            addLog(it.tile, BlockPayloadDropLog(it.tile, it.unit.toInteractor(), it.building.block, it.building.config()))
        }
    }

    private fun getLogs(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    private fun getLogs(tile: Tile): TileRecord? = getLogs(tile.x.toInt(), tile.y.toInt())

    private fun addLog(tile: Tile, log: TileLog) {
        val logs = getLogs(tile) ?: return
        logs.add(log, tile)
    }
}
