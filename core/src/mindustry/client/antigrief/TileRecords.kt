package mindustry.client.antigrief

import arc.*
import arc.math.*
import arc.util.*
import mindustry.*
import mindustry.ai.types.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.*
import mindustry.world.blocks.*
import mindustry.world.blocks.power.*
import java.time.*
import kotlin.math.*

object TileRecords {
    private var records: Array<Array<TileRecord>> = arrayOf(arrayOf()) // FINISHME: Use 1d array
    var joinTime: Instant = Instant.EPOCH

    fun init() {
        NetworkTileLogs.init()

        Events.on(EventType.WorldLoadEvent::class.java) {
            val startTime = Time.globalTime / 60.0 - Vars.state.tick / 60.0
            var sameMap = abs(ClientVars.lastServerStartTime - startTime) < 10 // if start time of map is within 10s of the previous start time
            sameMap = sameMap && records.isNotEmpty() && Vars.state.map.name() == ClientVars.lastServerName &&
                    Vars.world.width() == records.size && Vars.world.height() == records[0].size

            ClientVars.lastServerStartTime = startTime
            ClientVars.lastServerName = Vars.state.map.name()
            if (!ClientVars.syncing) {
                if (!sameMap) {
                    records = Array(Vars.world.width()) { x -> Array(Vars.world.height()) { y -> TileRecord(x, y) } }
                    joinTime = Instant.now()
                }
                NetworkTileLogs.onWorldLoad(sameMap)
            }
        }

        Events.on(EventType.BlockBuildBeginEventBefore::class.java) {
            if (it.newBlock == null || it.newBlock == Blocks.air) {
                it.tile.getLinkedTiles { tile ->
                    addLog(tile, TileBreakLog(it.unit.toInteractor(), tile.block()))
                }
            } else { // FINISHME: slightly very inefficient?
                it.tile.getLinkedTilesAs(it.newBlock) { tile ->
                    val log = TilePlacedLog(it.unit.toInteractor(), it.newBlock, it.rotation, null, tile == it.tile)
                    addLog(tile, log)?.apply {
                        team = it.team
                    }
                }
            }
        }

        Events.on(EventType.BlockBuildBeginEvent::class.java) { // Used for non instant builds, adds the config once the user starts working on it. This only works on the server side but that's fine
            if (it.breaking || it.tile.build !is ConstructBlock.ConstructBuild) return@on
            Core.app.post {
                val config = (it.tile.build as? ConstructBlock.ConstructBuild)?.lastConfig ?: return@post
                it.tile.getLinkedTiles { tile ->
                    val sequence = this[tile]?.sequences ?: return@getLinkedTiles
                    (sequence.last().logs.lastOrNull() as? TilePlacedLog)?.configuration = config
                }
            }
        }

        Events.on(EventType.BlockBuildEndEvent::class.java) { // Used for instantBuild == true
            if (it.breaking) return@on
            it.tile.getLinkedTiles { tile ->
                val sequence = this[tile]?.sequences ?: return@getLinkedTiles
                (sequence.last().logs.lastOrNull() as? TilePlacedLog)?.configuration = it.tile.build?.config() ?: return@getLinkedTiles // FINISHME: Build is nullable for some reason (see https://discord.com/channels/965438060508631050/965438061003550722/1039950910295658600)
            }
        }

        Events.on(EventType.ConfigEvent::class.java) { // FINISHME: Check if last was NodeLinkAddedTileLog and combine as needed.
            val build = it.tile // Horrible misnomer
            val constructor = if (it.player == null && build.tile.block() is PowerNode) ::NodeLinkAddedTileLog else ::ConfigureTileLog
            build.tile.getLinkedTiles { tile ->
                addLog(tile, constructor(it.player.toInteractor(), tile.block(),build.rotation, it.value))?.apply {
                    configuration = it.previous
                }
            }
        }

        Events.on(EventType.BuildPayloadPickup::class.java) {
            it.tile.getLinkedTiles { tile ->
                addLog(tile, BlockPayloadPickupLog(it.unit.toInteractor(), it.building.block))
            }
        }

        Events.on(EventType.BuildPayloadDrop::class.java) {
            it.tile.getLinkedTilesAs(it.building.block) { tile ->
                addLog(tile, BlockPayloadDropLog(it.unit.toInteractor(), it.building.block, it.building.rotation, it.building.config(), isOrigin(tile)))
            }
        }

        Events.on(EventType.BlockDestroyEvent::class.java) {
            if (it.tile.team() !== Vars.player.team()) return@on // Couldn't care less about enemies, especially in flood
            it.tile.getLinkedTiles { tile ->
                val cb = tile.build as? ConstructBlock.ConstructBuild
                addLog(tile, TileDestroyedLog(cb?.current ?: cb?.previous ?: tile.block() ?: Blocks.air)) // FINISHME: Is this ever actually null?
            }
        }

        Events.on(EventType.UnitDeadEvent::class.java) {
            if(it.unit == null || it.unit.team() != Vars.player.team() || it.unit.tileOn() == null) return@on

            if(it.unit.controller() is MissileAI) return@on

            val threshold = it.unit.type.hitSize * it.unit.type.hitSize + 0.01f
            // FINISHME: linkedArea replacement
            for (point in TileLog.linkedArea(it.unit.tileOn(), Mathf.ceil(it.unit.type.hitSize / Vars.tilesize))) {
                if (point in Vars.world && it.unit.within(Vars.world[point], threshold)) {
                    val tile = Vars.world[point]
                    addLog(tile, UnitDestroyedLog(it.unit.toInteractor(), it.unit.type, it.unit.controller() is Player))
                }
            }
        }

        Events.on(EventType.BuildRotateEvent::class.java) {
            val player = it.unit?.player ?: return@on
            val direction = rotationDirection(it.previous, it.build.rotation)
            it.build.tile.getLinkedTiles { tile ->
                addLog(tile, RotateTileLog(player.toInteractor(), it.build.block, it.build.rotation, direction))?.apply {
                    rotation = it.previous
                }
            }
        }
    }

    operator fun get(x: Int, y: Int): TileRecord? = records.getOrNull(x)?.getOrNull(y)

    operator fun get(tile: Tile): TileRecord? = this[tile.x.toInt(), tile.y.toInt()]

    private fun addLog(tile: Tile, log: TileLog): TileState? {
        val logs = this[tile] ?: return null
        return logs.add(log, tile)
    }

    fun show(tile: Tile) {
        dialog("Logs") {
            cont.add(TileRecords[tile]?.toElement())
            addCloseButton()
        }.show()
    }

    fun isOrigin(tile: Tile) = tile.build?.tile == tile // Not quite tile.isCenter but probably close enough that it would work...

    /** Returns true if right, false if left. */
    fun rotationDirection(old: Int, new: Int) = old < new && (old != 0 || new != 3) || old == 3 && new == 0
}
