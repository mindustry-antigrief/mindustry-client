package mindustry.client.antigrief

import arc.Core
import arc.scene.Element
import arc.scene.ui.layout.Table
import mindustry.client.utils.capLength
import mindustry.client.utils.label
import mindustry.client.utils.restrictToAscii
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

    constructor(x: Int, y: Int, block: Block, rotation: Int, configuration: Any?, time: Instant) {
        this.x = x
        this.y = y
        this.block = block
        this.configuration = configuration
        this.rotation = rotation
        this.time = time
    }

    constructor(tile: Tile, block: Block, rotation: Int, configuration: Any?, time: Instant) {
        this.x = tile.x.toInt()
        this.y = tile.y.toInt()
        this.block = block
        this.rotation = rotation
        this.configuration = configuration
        this.time = time
    }

    constructor(tile: Tile) : this(tile, tile.block(), tile.build?.rotation ?: 0, tile.build?.config(), Instant.now())

    fun clone(): TileState {
        return TileState(x, y, block, rotation, configuration, time)
    }

    fun toElement(): Element {
        val table = Table()

        table.label(time.toString())  // ISO 8601 time/date formatting
        table.row()

        table.image(block.icon(Cicon.medium))
        table.row()

        table.label("Facing " + when (abs(rotation % 4)) {
            0 -> "north"
            1 -> "east"
            2 -> "south"
            3 -> "west"
            else -> "unknown"
        })
        table.row()

        table.label("config:")
        table.row()
        when (val config = configuration) {
            is Item -> table.image(config.icon(Cicon.medium))

            is Liquid -> table.image(config.icon(Cicon.medium))

            is Block -> table.image(config.icon(Cicon.medium))

            else -> table.label(config.toString().restrictToAscii().capLength(20)).table.button(Icon.copy) { Core.app.clipboardText = config.toString() }
        }

        return table
    }
}
