package mindustry.client.antigrief

import arc.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.ui.layout.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.antigrief.TileRecords.isOrigin
import mindustry.client.utils.*
import mindustry.content.Blocks
import mindustry.core.*
import mindustry.entities.units.BuildPlan
import mindustry.game.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.*
import mindustry.world.blocks.ConstructBlock
import java.time.*
import kotlin.math.*

class TileState {
    val x: Int
    val y: Int
    var block: Block
    var rotation: Int
    var configuration: Any?
    var time: Instant
    var team: Team
    var isRootTile: Boolean

    constructor(x: Int, y: Int, block: Block, rotation: Int, configuration: Any?, team: Team, time: Instant, isRootTile: Boolean = isOrigin(world.tile(x, y))) {
        this.x = x
        this.y = y
        this.block = block
        this.configuration = configuration
        this.rotation = rotation
        this.team = team
        this.time = time
        this.isRootTile = isRootTile
    }

    constructor(tile: Tile, block: Block, rotation: Int, configuration: Any?, team: Team, time: Instant, isRootTile: Boolean = isOrigin(tile)) {
        this.x = tile.x.toInt()
        this.y = tile.y.toInt()
        this.block = block
        this.rotation = rotation
        this.configuration = configuration
        this.team = team
        this.time = time
        this.isRootTile = isRootTile
    }

    constructor(tile: Tile, time: Instant = Instant.now()) : this(tile, tile.block(), tile.build?.rotation ?: -1, tile.build?.config(), tile.team(), time)

    fun clone(): TileState {
        return TileState(x, y, block, rotation, configuration, team, time, isRootTile)
    }

    /**
     * This creates a BuildPlan if a BuildPlan is required to return the block to this state.
     * Note that this does not return a ConfigRequest if that is all is needed.
     **/
    fun restoreState(tile: Tile, planSeq: Seq<BuildPlan>, toBreak: IntSet) {
        if (!isRootTile && block !== Blocks.air) return
        if (tile.block() === block) {
            if (block === Blocks.air) return
            planSeq.add(BuildPlan(x, y, rotation, block, configuration))
            return
        }
        if (block === Blocks.air) {
            val rootTile = world.tile(x, y).build?.tile ?: return
            if (toBreak.add(rootTile.pos())) {
                planSeq.add(BuildPlan(rootTile.x.toInt(), rootTile.y.toInt())) // Break existing block
            }
            return
        }
        planSeq.add(BuildPlan(x, y, rotation, block, configuration)) // Simple build
    }

    fun toElement(): Element {
        val table = Table()

        table.label(Instant.ofEpochSecond(time.toEpochMilli() / 1000).toString() + " (" + UI.formatTime((Time.timeSinceMillis(time.toEpochMilli()) / 16.667).toFloat()) + ")")  // ISO 8601 time/date formatting

        table.row()
        table.add("${Core.bundle.get("client.team")}: ${team.name}")

        table.row()
        if (block.isAir) {
            table.add("@block.air.name")
        } else {
            table.image(block.uiIcon)
        }

        table.row()
        table.label(Core.bundle.get("client.facing") + ": " + Core.bundle.get("client." + when (abs(rotation % 4)) {
            0 -> "right"
            1 -> "up"
            2 -> "left"
            3 -> "down"
            else -> "unknown"
        }))
        table.row()

        if (configuration != null && (configuration as? Array<*>)?.isNotEmpty() == true) {
            table.label("config:")
            table.row()
            val config = configuration
            @Suppress("UNCHECKED_CAST")
            when {

                config is Item -> table.image(config.uiIcon)

                config is Liquid -> table.image(config.uiIcon)

                config is Block -> table.image(config.uiIcon)

                config is Array<*> && config.getOrNull(0) is Point2 -> {
                    // shut up I promise it's an array of point2s
                    val array = config as Array<Point2>
                    table.add("${array.size} blocks: " + array.joinToString(limit = 5) { "(${it.x + x}, ${it.y + y})" })
                    table.button(Icon.copySmall) { Core.app.clipboardText = array.joinToString { "(${it.x + x}, ${it.y + y})" } }
                }

                else -> table.label(config.toString().restrictToAscii().capLength(20)).table.button(Icon.copy) { Core.app.clipboardText = config.toString() }
            }
        }

        return table
    }
}
