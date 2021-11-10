package mindustry.client.antigrief

import arc.*
import arc.math.Mathf
import arc.util.Log
import mindustry.*
import mindustry.ai.types.*
import mindustry.client.*
import mindustry.client.antigrief.TileLog.Companion.linkedArea
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.*
import mindustry.world.blocks.*

object TileRecords {
    private var records: Array<Array<TileRecord>> = arrayOf(arrayOf())

    fun initialize() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (!ClientVars.syncing) records = Array(Vars.world.width()) { x -> Array(Vars.world.height()) { y -> TileRecord(x, y) } }
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
            if (!Vars.state.rules.editor) {
                forArea(it.tile.tile) { tile ->
                    addLog(tile, ConfigureTileLog(tile, it.player.toInteractor(), tile.block(), it.value))
                }
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
            if (it.tile.team() != Vars.player.team()) return@on // Couldn't care less about enemies, especially in flood
            forArea(it.tile) { tile ->
                addLog(tile, TileDestroyedLog(tile,
                    if (tile.build is ConstructBlock.ConstructBuild) (tile.build as ConstructBlock.ConstructBuild).current ?:
                    (tile.build as ConstructBlock.ConstructBuild).previous
                    else tile.block() ?: Blocks.air))
            }
        }

        Events.on(EventType.UnitDeadEvent::class.java) {
            if(it.unit == null || it.unit.team() != Vars.player.team() || it.unit.tileOn() == null) return@on
            val controller = it.unit.controller()
            if(controller !is LogicAI && controller !is FormationAI && controller !is Player) return@on
            forArea(it.unit.tileOn(), Mathf.ceil(it.unit.type.hitSize / Vars.tilesize)) { tile ->
                addLog(tile, UnitDestroyedLog(tile, it.unit.toInteractor(), it.unit, controller is Player))
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
        val logs = this[tile] ?: return
        logs.add(log, tile)
    }

    fun show(tile: Tile) {
        dialog("Logs") {
            cont.add(TileRecords[tile]?.toElement())
            addCloseButton()
        }.show()
    }
}
