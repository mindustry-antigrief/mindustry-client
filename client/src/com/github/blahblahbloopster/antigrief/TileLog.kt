package com.github.blahblahbloopster.antigrief

import mindustry.world.Block
import mindustry.world.Tile
import java.time.Instant

/** x and y are the top left corner */
data class IntRectangle(val x: Int, val y: Int, val width: Int, val height: Int)

abstract class TileLog(val position: IntRectangle, override val cause: Interactor) : InteractionLog {
    override val time: Instant = Instant.now()

    companion object {
        fun Tile.linkedArea(): IntRectangle {
            if (block().isMultiblock) return IntRectangle(x.toInt(), y.toInt(), 1, 1)
            return IntRectangle(x - (block().size / 2), y - (block().size / 2), block().size, block().size)
        }
    }

    constructor(tile: Tile, cause: Interactor) : this(tile.linkedArea(), cause)
}

class ConfigureTileLog(tile: Tile, cause: Interactor, val oldConfiguration: Any?, val newConfiguration: Any?) : TileLog(tile, cause)

open class TilePlacedLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause)

class TileReplacedLog(tile: Tile, cause: Interactor, oldBlock: Block, oldConfiguration: Any?, newBlock: Block, val newConfiguration: Any?) : TilePlacedLog(tile, cause, oldBlock, oldConfiguration)

class BlockPayloadDropLog(tile: Tile, cause: Interactor, block: Block, configuration: Any?) : TilePlacedLog(tile, cause, block, configuration)

open class TileBreakLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause)

class BlockPayloadPickupLog(tile: Tile, cause: Interactor, block: Block, configuration: Any?) : TileBreakLog(tile, cause, block, configuration)
