package com.github.blahblahbloopster

import arc.scene.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.serialization.*
import mindustry.core.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import java.nio.*
import java.time.*
import java.time.temporal.*
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
fun Temporal.secondsBetween(other: Temporal) = Duration.between(this, other).seconds

/** The age of this temporal. Always positive. */
fun Temporal.age() = abs(this.secondsBetween(Instant.now()))

/** Adds an element to the table followed by a row. */
fun Table.row(element: Element): Cell<Element> {
    val out = add(element)
    row()
    return out
}

inline fun UI.dialog(name: String, style: Dialog.DialogStyle = Styles.defaultDialog, dialog: BaseDialog.() -> Unit): Dialog {
    return BaseDialog(name, style).apply { clear() }.apply(dialog)
}

fun ByteArray.base64(): String = Base64Coder.encode(this).concatToString()

fun String.base64(): ByteArray? = try { Base64Coder.decode(this) } catch (e: IllegalArgumentException) { null }
