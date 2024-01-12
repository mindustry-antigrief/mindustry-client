package mindustry.client.antigrief

import arc.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.*
import mindustry.client.antigrief.TileRecords.joinTime
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.core.*
import mindustry.gen.Unit
import mindustry.ui.*
import mindustry.world.*
import java.time.*
import kotlin.math.*

// FINISHME: The string truncation is done in the most convoluted way imaginable
data class IntRectangle(val x: Int, val y: Int, val width: Int, val height: Int) : Iterable<Point2> { // Finishme: This class is entirely useless
    private class IntRectIterator(val intrect: IntRectangle) : Iterator<Point2> {
        var index = 0
        override fun hasNext() = index < intrect.width * intrect.height

        override fun next(): Point2 {
            val i = index++
            return Point2(intrect.x + (i % intrect.width), intrect.y - (i / intrect.width))
        }
    }

    override fun iterator(): Iterator<Point2> = IntRectIterator(this)
}

private var lastID: Long = 0
abstract class TileLog(val position: IntRectangle, override val cause: Interactor) : InteractionLog {
    val id: Long = lastID++

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

    abstract override fun toString(): String

    open fun add(sequence: TileLogSequence) {
        sequence.logs.add(this)
    }

    abstract fun toShortString(): String
}

class TileLogSequence(val snapshot: TileState, val startingIndex: Int) : Iterable<TileLog> {
    val logs = mutableListOf<TileLog>()
    val range get() = startingIndex..startingIndex + logs.size

    fun addLog(log: TileLog){
        logs.add(log)
    }

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

    fun after(index: Int): List<TileLog> {
        return logs.slice((index - startingIndex).coerceIn(logs.indices.apply { if (isEmpty()) return emptyList() /*idk either*/ }) until logs.size)
    }
}

class TileRecord(val x: Int, val y: Int) {
    var sequences: MutableList<TileLogSequence>? = null
    val size get() = sequences?.lastOrNull()?.range?.last ?: 0
    private val totalRange get() = 0..size

    fun add(log: TileLog, tile: Tile) {
        when {
            sequences == null -> {
                sequences = mutableListOf()
                val state = TileState(tile)
                state.time = joinTime
                sequences!!.add(TileLogSequence(state, 0))
            }
            sequences!!.last().logs.size > 100 -> {
                sequences!!.add(TileLogSequence(TileState(tile), sequences!!.last().range.last))
            }
        }
        sequences!!.last().addLog(log)
    }

    operator fun get(index: Int): TileState? {
        if (index !in totalRange) throw IndexOutOfBoundsException("Index $index is out of bounds! (size: $size)")
        // Get the last sequence that encompasses this index
        val bestSequence = sequences?.singleOrNull { index in it.range }
        return bestSequence?.get(index)
    }

    // FINISHME: This breaks when there are over 100 logs on the tile.
    fun lastLogs(count: Int): List<TileLog> {
        val startingIndex = (size - count).coerceAtLeast(0)
        val output = mutableListOf<TileLog>()
        for (item in sequences ?: return emptyList()) {
            output.addAll(item.after(startingIndex))
        }
        return output
    }

    /** Returns the last TileLogSequence before a certain time - that is, time will be within returned sequence **/
    fun lastSequence(time: Instant): TileLogSequence? {
        return sequences?.asReversed()?.first { it.snapshot.time <= time }
    }

    fun oldestLog(sequence: TileLogSequence): TileLog? {
        return if (sequence.logs.isNotEmpty()) sequence.logs[0] else null
    }

    fun oldestSequence(): TileLogSequence? {
        return sequences?.getOrNull(0)  // should never be null but you never know
    }

    fun toElement(): Element {
        val table = Table()
        table.add(Core.bundle.format("client.logfor", x, y)).top()
        table.row()

        table.pane { t ->
            if (sequences == null) return@pane

            if (sequences?.any() == true) {
                t.button("@client.initialstate") {
                    dialog("@client.log") {
                        cont.add(sequences!![0].snapshot.toElement())
                        addCloseButton()
                    }.show()
                }.wrap(false)
                t.row()
            }
            for (sequence in sequences!!) {
                for ((index, log) in sequence.withIndex()) {
                    t.add(log.toString() + " (" + UI.formatTime((Time.timeSinceMillis(log.time.toEpochMilli()) / 16.667).toFloat()) + ")").left()
                    t.row()
                    t.button("@client.state") {
                        dialog("@client.log") {
                            cont.add(get(index + sequence.startingIndex)?.toElement()) //FINISHME: Change the time that is displayed
                            addCloseButton()
                        }.show()
                    }.wrap(false)
                    t.row()
                }
            }
        }.grow()

        return table
    }
}

private const val MAX_NAME_LENGTH = 16

private fun String.shorten() = take(MAX_NAME_LENGTH).let {
    if (length > MAX_NAME_LENGTH) "$it..." else it
}

abstract class AbstractTileLog(tile: Tile, cause: Interactor, val block: Block) : TileLog(tile, cause) {
    protected val eventPlayer: String = cause.shortName.stripColors().shorten()
    protected val eventTarget: String = if (Core.settings.getBool("useblockicon")) Fonts.getUnicodeStr(block.name) else block.localizedName
}

class ConfigureTileLog(tile: Tile, cause: Interactor, block: Block, val rotation: Int, var configuration: Any?) : AbstractTileLog(tile, cause, block) {
    override fun apply(previous: TileState) {
        previous.rotation = rotation
        previous.configuration = configuration
    }

    override fun toString() = "$eventPlayer ${Core.bundle.get("client.configured")} $eventTarget"
    override fun add(sequence: TileLogSequence) {
        Core.app.post {
            configuration = Vars.world.tile(position.x, position.y)?.build?.config()
            super.add(sequence)
        }
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[accent]${Core.bundle.get("client.configured")}[]" else Core.bundle.get("client.configured")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}

open class TilePlacedLog(tile: Tile, cause: Interactor, block: Block, var rotation: Int = tile.build?.rotation ?: 0, var configuration: Any?, val isRootTile: Boolean) : AbstractTileLog(tile, cause, block) {
    override fun apply(previous: TileState) {
        previous.block = block
        previous.rotation = rotation
        previous.configuration = configuration
        previous.isRootTile = isRootTile
    }

    fun updateLog(rotation: Int?, configuration: Any?) {
        if (rotation != null) this.rotation = rotation
        if (configuration != null) this.configuration = configuration
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.built")} ${block.localizedName}"
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[green]${Core.bundle.get("client.built")}[]" else Core.bundle.get("client.built")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}

class BlockPayloadDropLog(tile: Tile, cause: Interactor, block: Block, rotation: Int, configuration: Any?, origin: Boolean) : TilePlacedLog(tile, cause, block, rotation, configuration, origin) {
    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.putdown")} ${block.localizedName}"
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[accent]${Core.bundle.get("client.putdown")}[]" else Core.bundle.get("client.putdown")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}

open class TileBreakLog(tile: Tile, cause: Interactor, block: Block) : AbstractTileLog(tile, cause, block) {
    override fun apply(previous: TileState) {
        previous.block = Blocks.air
        previous.rotation = -1
        previous.configuration = null
        previous.isRootTile = false
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.broke")} ${block.localizedName}"
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[red]${Core.bundle.get("client.broke")}[]" else Core.bundle.get("client.broke")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}

class BlockPayloadPickupLog(tile: Tile, cause: Interactor, block: Block) : TileBreakLog(tile, cause, block) {
    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.pickedup")} ${block.localizedName}"
    }

    private val eventName: String = if (Core.settings.getBool("colorizelogs")) "[accent]${Core.bundle.get("client.pickedup")}[]" else Core.bundle.get("client.pickedup")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}
class TileDestroyedLog(tile: Tile, block: Block) : TileBreakLog(tile, NoInteractor(), block) {
    override fun toString(): String {
        return "${block.localizedName} ${Core.bundle.get("client.destroyed")}"
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[red]${Core.bundle.get("client.destroyed")}[]" else Core.bundle.get("client.destroyed")

    override fun toShortString() = "$eventTarget $eventName"
}

class UnitDestroyedLog(val tile: Tile, cause: Interactor, val unit: Unit, val isPlayer: Boolean) : TileLog(tile, cause) {
    override fun apply(previous: TileState) {
        //pass
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.playerunitdeath")} ${unit.type?.localizedName ?: "null unit"}"
    }

    private val eventPlayer: String = "${cause.shortName.stripColors().take(16)}${if (cause.shortName.stripColors().length > 16) "..." else ""}"
    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[red]${Core.bundle.get("client.playerunitdeath")}[]" else Core.bundle.get("client.playerunitdeath")
    private val eventUnit: String = if(Core.settings.getBool("useblockicon") && unit.type.name.isNotEmpty()) Fonts.getUnicodeStr(unit.type.name) else unit.type?.localizedName ?: "null unit"

    override fun toShortString(): String {
        return "$eventPlayer $eventName $eventUnit"
    }
}

class RotateTileLog(tile: Tile, cause: Interactor, block: Block, val rotation: Int, val direction: Boolean) : AbstractTileLog(tile, cause, block) {
    override fun apply(previous: TileState) {
        previous.rotation = rotation
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.rotated")} ${block.localizedName} ${Core.bundle.get(if (direction) "client.counterclockwise" else "client.clockwise")}"
    }

    private val eventName: String = if(Core.settings.getBool("colorizelogs")) "[accent]${Core.bundle.get("client.rotated")}[]" else Core.bundle.get("client.rotated")

    override fun toShortString() = "$eventPlayer $eventName $eventTarget"
}
