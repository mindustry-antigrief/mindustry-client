@file:Suppress("UNUSED")
@file:JvmName("ClientUtils")

package mindustry.client.utils

import arc.*
import arc.files.*
import arc.graphics.*
import arc.math.*
import arc.math.geom.*
import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.Vars.*
import mindustry.ai.types.*
import mindustry.client.*
import mindustry.client.communication.*
import mindustry.core.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import mindustry.ui.fragments.ChatFragment.*
import mindustry.world.*
import java.io.*
import java.net.*
import java.nio.*
import java.security.cert.*
import java.time.*
import java.time.temporal.*
import java.util.*
import java.util.zip.*
import kotlin.contracts.*
import kotlin.math.*

/** Performs the given [block] with each element as its receiver. */
@OptIn(ExperimentalContracts::class)
inline fun <T, R>Iterable<T>.withEach(block: T.() -> R) {
    contract {
        callsInPlace(block, InvocationKind.UNKNOWN)
    }
    forEach { it.block() }
}

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
fun Long.toInstant(): Instant = try { Instant.ofEpochSecond(this) } catch (e: DateTimeException) { Instant.EPOCH }

/** Seconds between this and [other].  If [other] happened after this, it will be positive. */
fun Temporal.secondsBetween(other: Temporal) = timeSince(other, ChronoUnit.SECONDS)

fun Temporal.timeSince(other: Temporal, unit: TemporalUnit) = unit.between(this, other)

/** The age of this temporal in the given unit (by default seconds). Always positive. */
fun Temporal.age(unit: TemporalUnit = ChronoUnit.SECONDS) = abs(this.timeSince(Instant.now(), unit))

/** Adds an element to the table followed by a row. */
fun <T : Element> Table.row(element: T): Cell<T> = add(element).also { row() }

inline fun dialog(name: String, style: Dialog.DialogStyle = Styles.defaultDialog, dialog: BaseDialog.() -> Unit): Dialog {
    return BaseDialog(name, style).apply(dialog)
}

fun Cell<TextButton>.wrap(value: Boolean) { get().label.setWrap(value) }

fun ByteArray.base64(): String = Base64Coder.encode(this).concatToString()

fun String.base64(): ByteArray? = try { Base64Coder.decode(this) } catch (e: IllegalArgumentException) { null }

fun Int.toBytes() = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun Short.toBytes() = byteArrayOf((toInt() shr 8).toByte(), (this).toByte())

fun Long.toBytes() = byteArrayOf((this shr 56).toByte(), (this shr 48).toByte(), (this shr 40).toByte(), (this shr 32).toByte(), (this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), (this).toByte())

fun ByteArray.base32768(): String = Base32768Coder.encode(this)

fun String.base32768(): ByteArray? = try { Base32768Coder.decode(this) } catch (e: IOException) { null }

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

/** Flips the two values in a [Pair] */
fun <A, B> Pair<A, B>.flip() = Pair(second, first)

/** Checks equality between two [Pair] instances, ignores value order. */
infix fun <A, B> Pair<A, B>.eqFlip(other: Pair<A, B>) = this == other || this.flip() == other

/** Checks equality between a [Pair] and two other values. */
fun <A, B> Pair<A, B>.eqFlip(a: A, b: B) = this.first == a && this.second == b || this.first == b && this.second == a

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
                true
            }
            else -> {
                output.add(item)
                false
            }
        }
    }

    return output
}

fun String.bundle(): String = Core.bundle[removePrefix("@")]
operator fun String.get(vararg args: Any?): String = Core.bundle.format(removePrefix("@"), args)

val X509Certificate.readableName: String
    get() = subjectX500Principal.name.removePrefix("CN=")

fun String.ascii() = filter { it in ' '..'~' }

fun String.asciiNoSpaces() = filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' || it == '_' }

fun <T> next(event: Class<T>, repetitions: Int = 1, lambda: (T) -> Unit) {
    var i = 0
    var id = -1
    id = Events.onid(event) {
        lambda(it)
        if (i++ >= repetitions) Events.remove(event, id) // FINISHME: Is there an off by one here? Im too tired for this right now
    }
}

fun ByteBuffer.putString(string: String) { putByteArray(string.encodeToByteArray()) }

val ByteBuffer.string get() = byteArray.decodeToString()

fun ByteBuffer.putByteArray(bytes: ByteArray) {
    putInt(bytes.size)
    put(bytes)
}

val ByteBuffer.byteArray get() = bytes(int)

fun ByteBuffer.putInstantSeconds(instant: Instant) { putLong(instant.epochSecond) }

val ByteBuffer.instant get() = long.toInstant()

fun pixmapFromClipboard(): Pixmap? {
    try {
        val tkClass = Class.forName("java.awt.Toolkit")
        val tk = tkClass.getMethod("getDefaultToolkit").invoke(null)

        val clipboard = tkClass.getMethod("getSystemClipboard").invoke(tk)
        val clipboardClass = Class.forName("java.awt.datatransfer.Clipboard")

        val content = clipboardClass.getMethod("getContents", java.lang.Object::class.java)
            .invoke(clipboard, null)

        val flavorClass = Class.forName("java.awt.datatransfer.DataFlavor")
        val transferClass = Class.forName("java.awt.datatransfer.Transferable")
        val img = transferClass.getMethod("getTransferData", flavorClass)
            .invoke(content, flavorClass.getField("imageFlavor").get(null))

        val width = img::class.java.getMethod("getWidth").invoke(img) as Int
        val height = img::class.java.getMethod("getHeight").invoke(img) as Int

        val array = IntArray(width * height)

        img::class.java.getMethod(
            "getRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        ).invoke(img, 0, 0, width, height, array, 0, width)

        val buffer = ByteBuffer.allocateDirect(4 * width * height)

        for (item in array) {
            buffer.put((item shr 16).toByte())
            buffer.put((item shr 8).toByte())
            buffer.put(item.toByte())
            buffer.put((item shr 24).toByte())
        }

        return Pixmap(buffer, width, height)
    } catch (e: Exception) {
        return null
    }
}

inline fun <T : Disposable, V> T.use(lambda: T.() -> V) = lambda().also { this.dispose() }

private val bytes = ByteArrayOutputStream()

fun compressImage(img: Pixmap): ByteArray {
    try {
        if (ClientVars.jpegQuality == 0f) {
            throw ClassNotFoundException("I am lazy so we might use an already-implemented function")
        }
        val imgIO = Class.forName("javax.imageio.ImageIO")
        val writers =
            imgIO.getMethod("getImageWritersByFormatName", String::class.java).invoke(null, "jpeg") as Iterator<*>
        val writer = writers.next()
        val writerCls = Class.forName("javax.imageio.ImageWriter")
        bytes.reset()
        val memCacheOutCls = Class.forName("javax.imageio.stream.MemoryCacheImageOutputStream")
        val out = memCacheOutCls.getConstructor(OutputStream::class.java).newInstance(bytes)
        writerCls.getMethod("setOutput", java.lang.Object::class.java).invoke(writer, out)

        val bufImCls = Class.forName("java.awt.image.BufferedImage")
        val im = bufImCls.getConstructor(Int::class.java, Int::class.java, Int::class.java)
            .newInstance(img.width, img.height, bufImCls.getField("TYPE_INT_RGB").get(null))

        val imArray = IntArray(img.width * img.height)
        for (x in 0 until img.width) {
            for (y in 0 until img.height) {
                val rgb = img[x, y]
                imArray[x + (y * img.width)] = (rgb ushr 8) or (rgb shl (32 - 8))
            }
        }
        bufImCls.getMethod(
            "setRGB",
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            IntArray::class.java,
            Int::class.java,
            Int::class.java
        )
            .invoke(im, 0, 0, img.width, img.height, imArray, 0, img.width)

        val jpgParamCls = Class.forName("javax.imageio.plugins.jpeg.JPEGImageWriteParam")
        val param = jpgParamCls.getConstructor(Locale::class.java).newInstance(Locale.US)

        jpgParamCls.getMethod("setCompressionMode", Int::class.java)
            .invoke(param, Class.forName("javax.imageio.ImageWriteParam").getField("MODE_EXPLICIT").get(null))
        jpgParamCls.getMethod("setCompressionQuality", Float::class.java).invoke(param, ClientVars.jpegQuality)

        val imgTypeSpec = Class.forName("javax.imageio.ImageTypeSpecifier")
        val paramCls = Class.forName("javax.imageio.ImageWriteParam")

        val defMetadata = writerCls.getMethod("getDefaultImageMetadata", imgTypeSpec, paramCls)

        val renderedImg = Class.forName("java.awt.image.RenderedImage")
        val spec = imgTypeSpec.getMethod("createFromRenderedImage", renderedImg).invoke(null, im)

        val metadata = defMetadata.invoke(writer, spec, param)
        val metadataCls = Class.forName("javax.imageio.metadata.IIOMetadata")
        val iioimgCls = Class.forName("javax.imageio.IIOImage")

        val iioimg = iioimgCls.getConstructor(renderedImg, List::class.java, metadataCls)
            .newInstance(im, emptyList<Any>(), metadata)

        writerCls.getMethod("write", metadataCls, iioimgCls, paramCls).invoke(writer, metadata, iioimg, param)
        writerCls.getMethod("dispose").invoke(writer)
        memCacheOutCls.getMethod("flush").invoke(out)

        return bytes.toByteArray()
    } catch (e: ClassNotFoundException) {
        bytes.reset()
        PixmapIO.PngWriter().use { write(bytes, img.flipY()) } // PNG is somehow flipped vertically when transferred to baos
        return bytes.toByteArray()
    }
}

fun inflateImage(array: ByteArray, offset: Int, length: Int): Pixmap? {
    return try { Pixmap(array, offset, length) } catch (e: Exception) { null }
}

inline fun circle(x: Int, y: Int, radius: Float, cons: (Tile?) -> Unit) {
    // x^2 + y^2 = r^2
    // x = sqrt(r^2 - y^2)
    val tr = radius / tilesize
    val r2 = tr * tr
    val h = 0 until world.height()
    val w = 0 until world.width()
    for (yo in -tr.floor()..tr.ceil()) {
        val ty = yo + y
        if (ty !in h) continue
        val diff = sqrt(r2 - (yo * yo)).ceil()
        for (tx in (x - diff)..(x + diff)) {
            if (tx !in w) continue
            cons(world.tiles[tx, ty])
        }
    }
}

/** Send a signed message to chat. */
fun sendMessage(msg: String) = Call.sendChatMessage(Main.sign(msg))

fun getName(builder:mindustry.gen.Unit?):String {
    return if(builder == null){
        "null unit"
    } else if (builder.isPlayer) {
        Strings.stripColors(builder.player.name)
//    } else if (builder.controller() is FormationAI) {
//        Strings.stripColors((builder.controller() as FormationAI).leader.player.name)
    } else if (builder.controller() is LogicAI){
        val controller = (builder.controller() as LogicAI).controller
        Strings.format(
            "@ controlled by @ last configured by @ at (@, @)",
            builder.type.toString(), controller.displayName,
            if(controller.lastAccessed == null) "[unknown]" else Strings.stripColors(controller.lastAccessed),
            controller.tileX(), controller.tileY()
        )
    } else if (builder.controller() != null){
        builder.type.toString()
    } else "unknown"
}

fun getPlayer(unit: mindustry.gen.Unit?): Player? {
    return if (unit == null) null
    else if (unit.isPlayer) {
        unit.player
//    } else if ((unit.controller() as? FormationAI)?.leader?.isPlayer == true) {
//        (unit.controller() as FormationAI).leader.playerNonNull()
    } else if ((unit.controller() as? LogicAI)?.controller != null) {
        Groups.player.find{ p -> p.name.equals((unit.controller() as LogicAI).controller.lastAccessed)}
    } else null
}

fun toggleMutePlayer(player: Player) {
    val match = ClientVars.mutedPlayers.firstOrNull { p -> p.second == player.id || (p.first != null && p.first == player) }
    if (match == null) {
        ClientVars.mutedPlayers.add(Pair(player, player.id))
        ui.chatfrag.addMessage(Core.bundle.format("client.command.mute.success", player.coloredName(), player.id))
    } else {
        ClientVars.mutedPlayers.remove(match)
        Vars.player.sendMessage(Core.bundle.format("client.command.unmute.success", player.coloredName(), player.id))
    }
}

fun isDeveloper() = Main.keyStorage.cert() in Main.keyStorage.builtInCerts

//inline fun <T> Seq<out T>.forEach(consumer: (T?) -> Unit) {
//    for (i in 0 until size) consumer(items[i])
//}
//
//inline fun <T> Seq<out T>.forEach(pred: (T?) -> Boolean, consumer: (T?) -> Unit) {
//    for (i in 0 until size) {
//        if (pred(items[i])) consumer(items[i])
//    }
//}

fun ChatMessage.findCoords(): ChatMessage = NetClient.findCoords(this)

fun ChatMessage.findLinks(start: Int = 0): ChatMessage = NetClient.findLinks(this, start)

fun findItem(arg: String): Item = content.items().min { b -> biasedLevenshtein(arg, b.localizedName) }

fun findUnit(arg: String): UnitType = content.units().min { b -> biasedLevenshtein(arg, b.localizedName) }

fun findBlock(arg: String): Block = content.blocks().min { b -> biasedLevenshtein(arg, b.localizedName) }

fun findTeam(arg: String): Team = if (arg.toIntOrNull() in 0 until Team.all.size) Team.all[arg.toInt()] else Team.all.minBy { t -> if (t.name == null) Float.MAX_VALUE else biasedLevenshtein(arg, t.localized()) }

fun parseBool(arg: String) = arg.lowercase().startsWith("y") || arg.lowercase().startsWith("t") // FINISHME: This should probably just spit out an error on non y/n input

/** Returns true if right, false if left. */
fun rotationDirection(old: Int, new: Int) = old < new && (old != 0 || new != 3) || old == 3 && new == 0

fun restartGame() = openJar("-jar", Fi.get(ClientVars::class.java.protectionDomain.codeSource.location.toURI().path).absolutePath())

fun openJar(vararg extraArgs: String) {
    try {
        val args = mutableListOf(javaPath)
        args.addAll(System.getProperties().entries.map { "-D$it" }.toTypedArray())
        if (OS.isMac) args.add("-XstartOnFirstThread")
        args.addAll(extraArgs)
        Runtime.getRuntime().exec(args.toTypedArray())
        Core.app.exit()
    } catch (e: Exception) {
        when (e) {
            is IOException, is URISyntaxException -> { // Kotlin is strange and doesn't allow multi-catch
                Core.app.post {
                    val dialog = BaseDialog("@client.installjava")
                    dialog.cont.clearChildren()
                    dialog.cont.add("@client.nojava").row()
                    dialog.cont.button("@client.installjava") { Core.app.openURI("https://adoptium.net/index.html?variant=openjdk17&jvmVariant=hotspot") }.size(210f, 64f)
                    dialog.show()
                    dialog.addCloseButton()
                    dialog.hidden(Core.app::exit)
                }
            }
            else -> throw ArcRuntimeException(e)
        }
    }
}

@Suppress("NAME_SHADOWING")
@JvmOverloads
fun biasedLevenshtein(x: String, y: String, caseSensitive: Boolean = false, lengthIndependent: Boolean = false): Float {
    var x = x
    var y = y
    if (!caseSensitive) {
        x = x.lowercase()
        y = y.lowercase()
    }
    if (lengthIndependent) return biasedLevenshteinLengthIndependent(x, y)

    val dp = Array(x.length + 1) { IntArray(y.length + 1) }
    for (i in 0..x.length) {
        for (j in 0..y.length) {
            if (i == 0) {
                dp[i][j] = j
            } else if (j == 0) {
                dp[i][j] = i
            } else {
                dp[i][j] = minOf(
                    (dp[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1),
                    (dp[i - 1][j] + 1),
                    (dp[i][j - 1] + 1)
                )
            }
        }
    }
    val output = dp[x.length][y.length]
    if (y.startsWith(x) || x.startsWith(y)) {
        return output / 3f
    }
    return if (y.contains(x) || x.contains(y)) {
        output / 1.5f
    } else output.toFloat()
}

// FINISHME: This should be merged with the function above
@Suppress("NAME_SHADOWING")
private fun biasedLevenshteinLengthIndependent(x: String, y: String): Float {
    var x = x
    var y = y
    if (x.length > y.length) x = y.apply { y = x } // Y will be the longer of the two

    val xl = x.length
    val yl = y.length
    val yw = yl + 1
    val dp = IntArray(2 * yw)
    for (j in 0..yl) dp[j] = 0 // Insertions at the beginning are free
    var prev = yw
    var curr = 0
    var temp: Int
    for (i in 1..xl) {
        temp = prev
        prev = curr
        curr = temp
        dp[curr] = i
        for (j in 1..yl) {
            dp[curr + j] = minOf(
                dp[prev + j - 1] + Mathf.num(x[i - 1] != y[j - 1]),
                dp[prev + j] + 1,
                dp[curr + j - 1] + 1,
            )
        }
    }

    // startsWith
    if (dp[curr + xl] == 0) return 0f
    // Disregard insertions at the end - if it made it it made it
    var output = xl
    for (i in curr until curr + yl) {
        output = min(output, dp[i])
    }
    // contains
    return if (output == 0) 0.5f else output.toFloat() // Prefer startsWith
}