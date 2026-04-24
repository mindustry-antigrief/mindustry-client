package mindustry.client.antigrief

import arc.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.ui.layout.*
import arc.util.*
import arc.util.io.*
import mindustry.client.antigrief.TileRecords.joinTime
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.core.*
import mindustry.io.*
import mindustry.type.*
import mindustry.ui.*
import mindustry.world.*
import java.time.*

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

abstract class TileLog(override val cause: Interactor) : InteractionLog {
    /** Allows getting details about this log from the server */
    var id: Int = -1

    override var time: Instant = Instant.now()

    abstract fun apply(previous: TileState)

    abstract override fun toString(): String

    open fun add(sequence: TileLogSequence) {
        sequence.logs.add(this)
    }

    abstract fun toShortString(): String

    companion object {
        fun linkedArea(tile: Tile, size: Int): IntRectangle { // FINISHME: Remove this and replace it with something less stupid
            if (size == 1) return IntRectangle(tile.x.toInt(), tile.y.toInt(), 1, 1)

            val offsetx: Int = -(size - 1) / 2
            val offsety: Int = -(size - 1) / 2

            val worldx: Int = offsetx + tile.x
            val worldy: Int = offsety + tile.y

            return IntRectangle(worldx, worldy + size - 1, size, size)
        }
    }
}

class TileLogSequence(val snapshot: TileState, val startingIndex: Int) : Iterable<TileLog> {
    val logs = mutableListOf<TileLog>()
    val range get() = startingIndex..startingIndex + logs.size

    fun addLog(log: TileLog) {
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
    var sequences: MutableList<TileLogSequence>? = null // FINISHME: This whole "sequences" concept needs removal
    val size get() = sequences?.lastOrNull()?.range?.last ?: 0
    private val totalRange get() = 0..size

    fun add(log: TileLog, tile: Tile): TileState? {
        var state: TileState? = null
        when {
            sequences == null -> {
                sequences = mutableListOf()
                state = TileState(tile)
                state.time = joinTime
                sequences!!.add(TileLogSequence(state, 0))
            }
            sequences!!.last().logs.size > 100 -> {
                state = TileState(tile)
                sequences!!.add(TileLogSequence(state, sequences!!.last().range.last))
            }
        }
        sequences!!.last().addLog(log)
        return state
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

abstract class AbstractTileLog(cause: Interactor, val block: Block) : TileLog(cause) {
    protected fun eventPlayer(): String = cause.shortName.stripColors().shorten()
    protected fun eventTarget(): String = if (Core.settings.getBool("useiconslogs")) Fonts.getUnicodeStr(block.name) else block.localizedName
}

open class ConfigureTileLog(cause: Interactor, block: Block, val rotation: Int, var configuration: Any?) : AbstractTileLog(cause, block) {
    override fun apply(previous: TileState) {
        previous.rotation = rotation
        previous.configuration = configuration
    }

    override fun toString() = "${eventPlayer()} ${Core.bundle.get("client.configured")} ${eventTarget()}"

    private fun eventName(): String = Core.bundle.get("client.configured").let { if(Core.settings.getBool("colorizelogs")) "[accent]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = ConfigureTileLog(interactor, TypeIO.readBlock(reads), reads.b().toInt(), TypeIO.readObject(reads))
    }
}

class NodeLinkAddedTileLog(cause: Interactor, block: Block, rotation: Int, configuration: Any?) : ConfigureTileLog(cause, block, rotation, configuration) {
    override fun toString() = "${eventTarget()} ${Core.bundle.get("client.configurednodelink")}"
    private fun eventName(): String = Core.bundle.get("client.configurednodelink").let { if(Core.settings.getBool("colorizelogs")) "[accent]$it[]" else it }
    override fun toShortString() = "${eventTarget()} ${eventName()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = NodeLinkAddedTileLog(interactor, TypeIO.readBlock(reads), reads.b().toInt(), TypeIO.readObject(reads))
    }
}

open class TilePlacedLog(cause: Interactor, block: Block, var rotation: Int, var configuration: Any?, val isRootTile: Boolean): AbstractTileLog(cause, block) {
    override fun apply(previous: TileState) {
        previous.block = block
        previous.rotation = rotation
        previous.configuration = configuration
        previous.isRootTile = isRootTile
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.built")} ${block.localizedName}"
    }

    private fun eventName(): String = Core.bundle.get("client.built").let { if(Core.settings.getBool("colorizelogs")) "[green]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = TilePlacedLog(interactor, TypeIO.readBlock(reads), reads.b().toInt(), TypeIO.readObject(reads), reads.bool())
    }
}

class BlockPayloadDropLog(cause: Interactor, block: Block, rotation: Int, configuration: Any?, origin: Boolean) : TilePlacedLog(cause, block, rotation, configuration, origin) {
    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.putdown")} ${block.localizedName}"
    }

    private fun eventName(): String = Core.bundle.get("client.putdown").let { if(Core.settings.getBool("colorizelogs")) "[accent]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = BlockPayloadDropLog(interactor, TypeIO.readBlock(reads), reads.b().toInt(), TypeIO.readObject(reads), reads.bool())
    }
}

open class TileBreakLog(cause: Interactor, block: Block) : AbstractTileLog(cause, block) {
    override fun apply(previous: TileState) {
        previous.block = Blocks.air
        previous.rotation = -1
        previous.configuration = null
        previous.isRootTile = false
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.broke")} ${block.localizedName}"
    }

    private fun eventName(): String = Core.bundle.get("client.broke").let { if(Core.settings.getBool("colorizelogs")) "[red]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = TileBreakLog(interactor, TypeIO.readBlock(reads))
    }
}

class BlockPayloadPickupLog(cause: Interactor, block: Block) : TileBreakLog(cause, block) {
    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.pickedup")} ${block.localizedName}"
    }

    private fun eventName(): String = Core.bundle.get("client.pickedup").let { if(Core.settings.getBool("colorizelogs")) "[accent]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = BlockPayloadPickupLog(interactor, TypeIO.readBlock(reads))
    }
}
class TileDestroyedLog(block: Block) : TileBreakLog(NoInteractor, block) {
    override fun toString(): String {
        return "${block.localizedName} ${Core.bundle.get("client.destroyed")}"
    }

    private fun eventName(): String = Core.bundle.get("client.destroyed").let { if(Core.settings.getBool("colorizelogs")) "[red]$it[]" else it }

    override fun toShortString() = "${eventTarget()} ${eventName()}"

    companion object {
        fun read(reads: Reads) = TileDestroyedLog(TypeIO.readBlock(reads))
    }
}

class UnitDestroyedLog(cause: Interactor, val unitType: UnitType, val isPlayer: Boolean) : TileLog(cause) {
    override fun apply(previous: TileState) {
        //pass
    }

    override fun toString(): String {
        return if(isPlayer) "${cause.name.stripColors()} ${Core.bundle.get("client.playerunitdeath")} ${unitType.localizedName}" else "${cause.name.stripColors()} ${Core.bundle.get("client.unitdeath")}"
    }

    private fun eventController(): String = "${cause.shortName.stripColors().take(16)}${if (cause.shortName.stripColors().length > 16) "..." else ""}"
    private fun eventNamePlayer(): String = Core.bundle.get("client.playerunitdeath").let { if(Core.settings.getBool("colorizelogs")) "[red]$it[]" else it }
    private fun eventNameLogic(): String = Core.bundle.get("client.unitdeath").let { if(Core.settings.getBool("colorizelogs")) "[red]$it[]" else it }
    private fun eventUnit(): String = if(Core.settings.getBool("useiconslogs") && unitType.name.isNotEmpty()) Fonts.getUnicodeStr(unitType.name) else unitType.localizedName

    override fun toShortString() = if(isPlayer) "${eventController()} ${eventNamePlayer()} ${eventUnit()}" else "${eventController()} ${eventNameLogic()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = UnitDestroyedLog(interactor, TypeIO.readUnitType(reads), reads.bool())
    }
}

class RotateTileLog(cause: Interactor, block: Block, val rotation: Int, val direction: Boolean) : AbstractTileLog(cause, block) {
    override fun apply(previous: TileState) {
        previous.rotation = rotation
    }

    override fun toString(): String {
        return "${cause.name.stripColors()} ${Core.bundle.get("client.rotated")} ${block.localizedName} ${Core.bundle.get(if (direction) "client.counterclockwise" else "client.clockwise")}"
    }

    private fun eventName(): String = Core.bundle.get("client.rotated").let { if(Core.settings.getBool("colorizelogs")) "[accent]$it[]" else it }

    override fun toShortString() = "${eventPlayer()} ${eventName()} ${eventTarget()}"

    companion object {
        fun read(reads: Reads, interactor: Interactor) = RotateTileLog(interactor, TypeIO.readBlock(reads), reads.b().toInt(), reads.bool())
    }
}
