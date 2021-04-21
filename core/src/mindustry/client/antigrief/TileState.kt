package mindustry.client.antigrief

import arc.Core
import arc.math.geom.Point2
import arc.scene.Element
import arc.scene.ui.layout.Table
import mindustry.client.utils.capLength
import mindustry.client.utils.label
import mindustry.client.utils.restrictToAscii
import mindustry.game.Team
import mindustry.gen.Icon
import mindustry.type.Item
import mindustry.type.Liquid
import mindustry.ui.Cicon
import mindustry.world.Block
import mindustry.world.Tile
import java.time.Instant
import kotlin.math.abs

class TileState {
    val x: Int
    val y: Int
    var block: Block
    var rotation: Int
    var configuration: Any?
    var time: Instant
    var team: Team

    constructor(x: Int, y: Int, block: Block, rotation: Int, configuration: Any?, team: Team, time: Instant) {
        this.x = x
        this.y = y
        this.block = block
        this.configuration = configuration
        this.rotation = rotation
        this.time = time
        this.team = team
    }

    constructor(tile: Tile, block: Block, rotation: Int, configuration: Any?, team: Team, time: Instant) {
        this.x = tile.x.toInt()
        this.y = tile.y.toInt()
        this.block = block
        this.rotation = rotation
        this.configuration = configuration
        this.team = team
        this.time = time
    }

    constructor(tile: Tile) : this(tile, tile.block(), tile.build?.rotation ?: 0, tile.build?.config(), tile.team(), Instant.now())

    fun clone(): TileState {
        return TileState(x, y, block, rotation, configuration, team, time)
    }

    fun toElement(): Element {
        val table = Table()

        table.label(Instant.ofEpochSecond(time.toEpochMilli() / 1000).toString())  // ISO 8601 time/date formatting

        table.row()
        table.add("Team: ${team.name}")

        table.row()

        if (block.isAir) {
            table.add("Empty")
        } else {
            table.image(block.icon(Cicon.medium))
        }
        table.row()

        table.label("Facing " + when (abs(rotation % 4)) {
            0 -> "north"
            1 -> "east"
            2 -> "south"
            3 -> "west"
            else -> "unknown"
        })
        table.row()

        if (configuration != null && (configuration as? Array<*>)?.isNotEmpty() == true) {
            table.label("config:")
            table.row()
            val config = configuration
            @Suppress("UNCHECKED_CAST")
            when {

                config is Item -> table.image(config.icon(Cicon.medium))

                config is Liquid -> table.image(config.icon(Cicon.medium))

                config is Block -> table.image(config.icon(Cicon.medium))

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
