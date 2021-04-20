package mindustry.client.antigrief

import arc.scene.Element
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import mindustry.client.utils.dialog
import mindustry.client.utils.stripColors
import mindustry.content.Blocks
import mindustry.gen.Icon
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

    abstract fun toElement(): Element
}

class TileLogSequence(val snapshot: TileState, val startingIndex: Int) : Iterable<TileLog> {
    val logs = mutableListOf<TileLog>()
    val range get() = startingIndex..startingIndex + logs.size

    override fun iterator(): Iterator<TileLog> {
        return logs.iterator()
    }

    operator fun get(index: Int): TileState {
        val cpy = snapshot.clone()
        for (diff in logs.subList(0, index - startingIndex)) {
            diff.apply(cpy)
        }

        return cpy
    }
}

class TileRecord(val x: Int, val y: Int) {
    private val logs = mutableListOf<TileLogSequence>()
    val size get() = logs.lastOrNull()?.range?.last ?: 0
    var totalRange = 0 until 0

    fun add(log: TileLog, tile: Tile) {
        when {
            logs.isEmpty() -> {
                logs.add(TileLogSequence(TileState(tile), 0))
            }

            logs.last().logs.size > 100 -> {
                logs.add(TileLogSequence(TileState(tile), logs.last().range.last))
            }
        }
        logs.last().logs.add(log)
        totalRange = 0..size
    }

    operator fun get(index: Int): TileState {
        if (index !in totalRange) throw IndexOutOfBoundsException("Index $index is out of bounds! (size: $size)")
        // Get the last sequence that encompases this index
        val bestSequence = logs.singleOrNull { index in it.range } ?: throw IndexOutOfBoundsException("Tile record is empty!")
        return bestSequence[index]
    }

    fun toElement(): Element {
        val table = Table()
        table.add("Logs for ($x, $y):")

        table.pane { t ->
            var i = 0
            for (sequence in logs) {
                for (log in sequence) {
                    t.add(log.toElement())
                    t.button(Icon.eyeSmall) {
                        dialog("Log") {
                            add(get(i).toElement())
                            addCloseButton()
                        }.show()
                    }
                    t.row()
                    i++
                }
            }
        }

        return table
    }
}

class ConfigureTileLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.configuration = configuration
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} configured ${block.localizedName}")
    }
}

open class TilePlacedLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = block
        previous.configuration = configuration
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} placed ${block.localizedName}")
    }
}

class BlockPayloadDropLog(tile: Tile, cause: Interactor, block: Block, configuration: Any?) : TilePlacedLog(tile, cause, block, configuration) {
    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} picked up ${block.localizedName}")
    }
}

open class TileBreakLog(tile: Tile, cause: Interactor, val block: Block) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = Blocks.air
        previous.configuration = null
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} broke ${block.localizedName}")
    }
}

class BlockPayloadPickupLog(tile: Tile, cause: Interactor, block: Block) : TileBreakLog(tile, cause, block) {
    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} picked up ${block.localizedName}")
    }
}
