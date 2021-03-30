package com.github.blahblahbloopster.antigrief

import mindustry.content.Blocks
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

    abstract fun apply(previous: TileState)
}

class TileLogSequence(val snapshot: TileState, val startingIndex: Int) {
    val logs = mutableListOf<TileLog>()
    val lastIndex get() = logs.size + startingIndex
}

class TileRecord {
    private val logs = mutableListOf<TileLogSequence>()
    val size get() = logs.lastOrNull()?.lastIndex ?: 0

    fun add(log: TileLog, tile: Tile) {
        when {
            logs.isEmpty() -> {
                logs.add(TileLogSequence(TileState(tile), 0))
            }

            logs.last().logs.size > 100 -> {
                logs.add(TileLogSequence(TileState(tile), logs.last().lastIndex))
            }

            else -> {
                logs.last().logs.add(log)
            }
        }
    }

    operator fun get(index: Int): TileState {
        if (index >= size) throw IndexOutOfBoundsException("Index $index is out of bounds! (size: $size)")
        // Get the last sequence that encompases this index
        val bestSequence = logs.lastOrNull { it.lastIndex <= index } ?: throw IndexOutOfBoundsException("Tile record is empty!")
        // Make a copy so the base of the sequence isn't modified
        val cpy = bestSequence.snapshot.clone()
        // Apply diffs
        for (diff in bestSequence.logs.subList(0, index - bestSequence.startingIndex)) {
            diff.apply(cpy)
        }

        return cpy
    }
}

class ConfigureTileLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.configuration = configuration
    }
}

open class TilePlacedLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = block
        previous.configuration = configuration
    }
}

class BlockPayloadDropLog(tile: Tile, cause: Interactor, block: Block, configuration: Any?) : TilePlacedLog(tile, cause, block, configuration)

open class TileBreakLog(tile: Tile, cause: Interactor, val block: Block) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = Blocks.air
        previous.configuration = null
    }
}

class BlockPayloadPickupLog(tile: Tile, cause: Interactor, block: Block) : TileBreakLog(tile, cause, block)
