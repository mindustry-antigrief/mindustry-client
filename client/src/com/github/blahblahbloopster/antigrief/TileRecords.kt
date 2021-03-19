package com.github.blahblahbloopster.antigrief

import arc.Events
import arc.util.Time
import mindustry.Vars
import mindustry.client.ClientVars
import mindustry.game.EventType
import mindustry.world.Tile

object TileRecords {
    private lateinit var records: Array<Array<TileRecord>>

    fun initialize() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                records = Array(Vars.world.height()) { Array(Vars.world.width()) { TileRecord() } }
            }
        }

        Events.on(EventType.BlockBuildEndEvent::class.java) {
            val logs = getLogs(it.tile) ?: return@on
            if (!it.breaking) {
                logs.logs.add(
                        TilePlacedLog(it.tile, it.unit.toInteractor(), it.tile.block(), it.config)
                )
            }
        }

        Events.on(EventType.BlockBreakEvent::class.java) {
            val logs = getLogs(it.tile) ?: return@on
            logs.logs.add(
                    TileBreakLog(it.tile, it.unit.toInteractor(), it.oldBlock, it.oldConfig)
            )
        }
    }

    fun getLogs(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    fun getLogs(tile: Tile): TileRecord? = getLogs(tile.x.toInt(), tile.y.toInt())
}
