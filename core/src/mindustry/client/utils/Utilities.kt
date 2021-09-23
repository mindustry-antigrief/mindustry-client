@file:Suppress("UNUSED")

package mindustry.client.utils

import arc.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.client.communication.*
import mindustry.core.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import mindustry.world.*
import java.io.*
import java.nio.*
import java.security.cert.*
import java.time.*
import java.time.temporal.*
import java.util.zip.*
import kotlin.math.*

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
    return BaseDialog(name, style).apply(dialog)
}

fun Cell<TextButton>.wrap(value: Boolean) { get().label.setWrap(value) }

fun ByteArray.base64(): String = Base64Coder.encode(this).concatToString()

fun String.base64(): ByteArray? = try { Base64Coder.decode(this) } catch (e: IllegalArgumentException) { null }

fun Int.toBytes() = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun Short.toBytes() = byteArrayOf((toInt() shr 8).toByte(), (this).toByte())

fun Long.toBytes() = byteArrayOf((this shr 56).toByte(), (this shr 48).toByte(), (this shr 40).toByte(), (this shr 32).toByte(), (this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun ByteArray.base32678(): String = Base32768Coder.encode(this)

fun String.base32678(): ByteArray? = try { Base32768Coder.decode(this) } catch (e: IOException) { null }

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

operator fun World.contains(tile: Point2) = tile.x in 0 until width() && tile.y in 0 until height()

/** Clamped */
operator fun World.get(position: Point2): Tile = tiles.getc(position.x, position.y)

/** Clamped */
operator fun World.get(x: Int, y: Int): Tile = tiles.getc(x, y)

// x^2 + y^2 = r^2
// x^2 + y^2 - r^2 = 0
// x^2 - r^2 = -y^2
inline fun circle(x: Int, y: Int, radius: Float, block: (x: Int, y: Int) -> Unit) {
    val r2 = radius * radius
//    for (x1 in (x - radius)..(x + radius)) {
//        val n = r2 - sq(x1 - x)
//        for (y1 in (y - n)..(y + n)) {
//            block(x1, y1)
//        }
//    }
    for (x1 in floor(x - radius).toInt()..ceil(x + radius).toInt()) {
        for (y1 in floor(y - radius).toInt()..ceil(y + radius).toInt()) {
            if (sq(x1 - x) + sq(y1 - y) < r2) block(x1, y1)
        }
    }
}

fun sq(inp: Int) = inp * inp

/** Flips the two values in a [kotlin.Pair] */
fun <A, B> kotlin.Pair<A, B>.flip() = kotlin.Pair(second, first)

/** Checks equality between two [kotlin.Pair] instances, ignores value order. */
infix fun <A, B> kotlin.Pair<A, B>.eqFlip(other: kotlin.Pair<A, B>) = this == other || this.flip() == other

/** Checks equality between a [kotlin.Pair] and two other values. */
fun <A, B> kotlin.Pair<A, B>.eqFlip(a: A, b: B) = this.first == a && this.second == b || this.first == b && this.second == a

fun <T> Iterable<T>.escape(escapement: T, vararg escape: T): List<T> {
    val output = mutableListOf<T>()
    for (item in this) {
        if (item in escape || item == escapement) {
            output.add(escapement)
        }
        output.add(item)
    }
    return output
}

fun <T> Iterable<T>.unescape(escapement: T, vararg escape: T): List<T> {
    val output = mutableListOf<T>()
    var previousWasEscapement = false
    for (item in this) {
        previousWasEscapement = when {
            previousWasEscapement -> {
                if (item in escape || item == escapement) {
                    output.add(item)
                }
                false
            }
            item == escapement -> {
                false
            }
            else -> {
                output.add(item)
                false
            }
        }
    }

    return output
}

fun String.bundle(): String? = Core.bundle[removePrefix("@")]

val X509Certificate.readableName: String
    get() = subjectX500Principal.name.removePrefix("CN=")

fun String.asciiNoSpaces() = filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' || it == '_' }

fun <T> next(event: Class<T>, repetitions: Int = 1, lambda: (T) -> Unit) {
    var i = 0
    Events.on(event) {
        lambda(it)
        if (i++ >= repetitions) {
            Events.remove(event, lambda)
        }
    }
}

/** Whether we are connected to a .io server */
fun io() = Vars.net.client() && Vars.ui.join.commmunityHosts.contains { it.group == "io" && it.address == Vars.ui.join.lastHost?.address }

/** Whether the current gamemode is flood */
fun flood() = (Vars.net.client() && Vars.ui.join.lastHost?.modeName == "Flood") || Vars.state.rules.modeName == "Flood"

/** Whether the current gamemode is tower defense */
fun defense() = (Vars.net.client() && Vars.ui.join.lastHost?.modeName == "Defense") || Vars.state.rules.modeName == "Defense"