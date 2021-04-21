package mindustry.client.antigrief

import arc.Core
import arc.scene.Element
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.client.utils.Point2i
import mindustry.client.utils.dialog
import mindustry.client.utils.stripColors
import mindustry.content.Blocks
import mindustry.world.Block
import mindustry.world.Tile
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * x and y are the top left corner
 * todo: put them on bottom left corner
 */
data class IntRectangle(val x: Int, val y: Int, val width: Int, val height: Int) : Iterable<Point2i> {
    private class IntRectIterator(val intrect: IntRectangle) : Iterator<Point2i> {
        var index = 0
        override fun hasNext() = index < intrect.width * intrect.height

        override fun next(): Point2i {
            val i = index++
            return Point2i(intrect.x + (i % intrect.width), intrect.y - (i / intrect.width))
        }
    }

    override fun iterator(): Iterator<Point2i> = IntRectIterator(this)
}

abstract class TileLog(val position: IntRectangle, override val cause: Interactor) : InteractionLog {
    override val time: Instant = Instant.now()

    companion object {
        fun Tile.linkedArea(): IntRectangle {
            return linkedArea(this, block()?.size ?: return IntRectangle(x.toInt(), y.toInt(), 1, 1))
        }

        fun linkedArea(tile: Tile, size: Int): IntRectangle {
            if (size == 1) return IntRectangle(tile.x.toInt(), tile.y.toInt(), 1, 1)

            val offsetx: Int = -(size - 1) / 2
            val offsety: Int = -(size - 1) / 2

            val worldx: Int = offsetx + tile.x
            val worldy: Int = offsety + tile.y

            return IntRectangle(worldx, worldy + size - 1, size, size)
        }
    }

    constructor(tile: Tile, cause: Interactor) : this(tile.linkedArea(), cause)

    abstract fun apply(previous: TileState)

    abstract fun toElement(): Element

    open fun add(sequence: TileLogSequence) {
        sequence.logs.add(this)
    }

    abstract fun toShortString(): String
}

class TileLogSequence(val snapshot: TileState, val startingIndex: Int) : Iterable<TileLog> {
    val logs = mutableListOf<TileLog>()
    val range get() = startingIndex..startingIndex + logs.size

    override fun iterator(): Iterator<TileLog> {
        return logs.iterator()
    }

    operator fun get(index: Int): TileState {
        val cpy = snapshot.clone()
        for (diff in logs.subList(0, (index + 1) - startingIndex)) {
            diff.apply(cpy)
        }

        return cpy
    }
}

class TileRecord(val x: Int, val y: Int) {
    private val logs = mutableListOf<TileLogSequence>()
    val size get() = logs.lastOrNull()?.range?.last ?: 0
    val totalRange get() = 0..size

    fun add(log: TileLog, tile: Tile) {
        when {
            logs.isEmpty() -> {
                logs.add(TileLogSequence(TileState(tile), 0))
            }

            logs.last().logs.size > 100 -> {
                logs.add(TileLogSequence(TileState(tile), logs.last().range.last))
            }
        }
        log.add(logs.last())
    }

    operator fun get(index: Int): TileState {
        if (index !in totalRange) throw IndexOutOfBoundsException("Index $index is out of bounds! (size: $size)")
        // Get the last sequence that encompases this index
        val bestSequence = logs.singleOrNull { index in it.range } ?: throw IndexOutOfBoundsException("Tile record is empty!")
        return bestSequence[index]
    }

    fun lastLogs(count: Int): List<TileLog> {
        val num = min(count, size)

        val output = mutableListOf<TileLog>()
        var logIndex = logs.indexOfLast { size - num in it.range }
        for (i in size - num until size) {
            output.add(logs[logIndex].logs[i])
            if (i + 1 !in logs[logIndex].range) {
                logIndex++
                if (logIndex >= logs.size) {
                    break
                }
            }
        }
        return output
    }

    fun toElement(): Element {
        val table = Table()
        table.add("Logs for ($x, $y):").top()
        table.row()

        table.pane { t ->
            if (logs.isNotEmpty()) {
                t.button("Initial State") {
                    dialog("Log") {
                        add(logs[0].snapshot.toElement())
                        addCloseButton()
                    }.show()
                }.width(150f)
                t.row()
            }
            for (sequence in logs) {
                for ((index, log) in sequence.withIndex()) {
                    t.add(log.toElement()).left()
                    t.row()
                    t.button("State") {
                        dialog("Log") {
                            add(get(index + sequence.startingIndex).toElement())
                            addCloseButton()
                        }.show()
                    }.width(100f)
                    t.row()
                }
            }
        }.grow()

        return table
    }
}

class ConfigureTileLog(tile: Tile, cause: Interactor, val block: Block, var configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.configuration = configuration
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} configured ${block.localizedName}")
    }

    override fun add(sequence: TileLogSequence) {
        Core.app.post {
            configuration = Vars.world.tile(position.x, position.y)?.build?.config()
            sequence.logs.add(this)
        }
    }

    override fun toShortString() = "${cause.shortName.stripColors()} configured"
}

open class TilePlacedLog(tile: Tile, cause: Interactor, val block: Block, val configuration: Any?) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = block
        previous.configuration = configuration
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} placed ${block.localizedName}")
    }

    override fun toShortString() = "${cause.shortName.stripColors()} placed ${block.localizedName}"
}

class BlockPayloadDropLog(tile: Tile, cause: Interactor, block: Block, configuration: Any?) : TilePlacedLog(tile, cause, block, configuration) {
    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} picked up ${block.localizedName}")
    }

    override fun toShortString() = "${cause.shortName.stripColors()} picked up ${block.localizedName}"
}

open class TileBreakLog(tile: Tile, cause: Interactor, val block: Block) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        previous.block = Blocks.air
        previous.configuration = null
    }

    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} broke ${block.localizedName}")
    }

    override fun toShortString() = "${cause.shortName.stripColors()} broke ${block.localizedName}"
}

class BlockPayloadPickupLog(tile: Tile, cause: Interactor, block: Block) : TileBreakLog(tile, cause, block) {
    override fun toElement(): Element {
        return Label("${cause.name.stripColors()} picked up ${block.localizedName}")
    }

    override fun toShortString() = "${cause.shortName.stripColors()} picked up ${block.localizedName}"
}

class TileDestroyedLog(tile: Tile, block: Block) : TileBreakLog(tile, NoInteractor(), block) {
    override fun toElement(): Element {
        return Label("${block.localizedName} destroyed")
    }

    override fun toShortString() = "${block.localizedName} destroyed"
}
