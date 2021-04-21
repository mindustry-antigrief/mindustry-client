package mindustry.client.utils

import arc.scene.Element
import arc.scene.ui.Dialog
import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.util.Strings
import arc.util.serialization.Base64Coder
import mindustry.core.World
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import mindustry.world.Tile
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.time.temporal.TemporalUnit
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

fun Table.label(text: String): Cell<Label> {
    return add(Label(text))
}

fun ByteBuffer.remainingBytes(): ByteArray {
    return bytes(remaining())
}

fun ByteBuffer.bytes(num: Int): ByteArray {
    val bytes = ByteArray(num)
    get(bytes)
    return bytes
}

/** Converts a [Long] representing unix time in seconds to [Instant] */
fun Long.toInstant(): Instant = Instant.ofEpochSecond(this)

/** Seconds between this and [other].  If [other] happened after this, it will be positive. */
fun Temporal.secondsBetween(other: Temporal) = timeSince(other, ChronoUnit.SECONDS)

fun Temporal.timeSince(other: Temporal, unit: TemporalUnit) = unit.between(this, other)

/** The age of this temporal in the given unit (by default seconds). Always positive. */
fun Temporal.age(unit: TemporalUnit = ChronoUnit.SECONDS) = abs(this.timeSince(Instant.now(), unit))

/** Adds an element to the table followed by a row. */
fun <T : Element> Table.row(element: T): Cell<T> {
    val out = add(element)
    row()
    return out
}

inline fun dialog(name: String, style: Dialog.DialogStyle = Styles.defaultDialog, dialog: BaseDialog.() -> Unit): Dialog {
    return BaseDialog(name, style).apply { clear() }.apply(dialog)
}

fun ByteArray.base64(): String = Base64Coder.encode(this).concatToString()

fun String.base64(): ByteArray? = try { Base64Coder.decode(this) } catch (e: IllegalArgumentException) { null }

fun Int.toBytes() = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun Long.toBytes() = byteArrayOf((this shr 56).toByte(), (this shr 48).toByte(), (this shr 40).toByte(), (this shr 32).toByte(), (this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun Double.floor() = floor(this).toInt()

fun Float.floor() = floor(this).toInt()

fun Double.ceil() = ceil(this).toInt()

fun Float.ceil() = ceil(this).toInt()

fun ByteArray.buffer(): ByteBuffer = ByteBuffer.wrap(this)

object Compression {
    fun compress(input: ByteArray): ByteArray {
        val deflater = DeflaterInputStream(input.inputStream())
        val output = deflater.readBytes()
        deflater.close()
        return output
    }

    fun inflate(input: ByteArray): ByteArray {
        val inflater = InflaterInputStream(input.inputStream())
        val output = inflater.readBytes()
        inflater.close()
        return output
    }
}

fun ByteArray.compress() = Compression.compress(this)

fun ByteArray.inflate() = Compression.inflate(this)

/** Pretty slow */
fun String.restrictToAscii(): String {
    val new = StringBuilder()
    for (char in this) {
        if (char in ' '..'~') {
            new.append(char)
        }
    }
    return new.toString()
}

fun String.capLength(length: Int): String {
    if (this.length <= length) return this
    if (length <= 3) return substring(0 until length)
    return substring(0 until length - 3) + "..."
}

fun String.stripColors(): String = Strings.stripColors(this)

inline fun <T> Iterable<T>.sortedThreshold(threshold: Double, predicate: (T) -> Double): List<T> {
    return zip(map(predicate))  // Compute the predicate for each value and put it in pairs with the original item
        .filter { it.second >= threshold }  // Filter by threshold
        .sortedBy { it.second }  // Sort
        .unzip().first  // Go from a list of pairs back to a list
}

fun String.replaceLast(deliminator: String, replacement: String): String {
    val index = lastIndexOf(deliminator)
    if (index == -1) return this
    return replaceRange(index, index + deliminator.length, replacement)
}

fun String.removeLast(deliminator: String) = replaceLast(deliminator, "")

data class Point2i(val x: Int, val y: Int)

operator fun World.contains(tile: Point2i) = tile.x in 0 until width() && tile.y in 0 until height()

/** Clamped */
operator fun World.get(position: Point2i): Tile = tiles.getc(position.x, position.y)

/** Clamped */
operator fun World.get(x: Int, y: Int): Tile = tiles.getc(x, y)
