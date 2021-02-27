package com.github.blahblahbloopster.ui

import arc.scene.Element
import arc.scene.ui.Dialog
import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.util.serialization.Base64Coder
import mindustry.core.UI
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.temporal.Temporal
import kotlin.math.abs

fun Table.label(text: String): Cell<Label> {
    return add(Label(text))
}

fun ByteBuffer.remainingBytes(): ByteArray {
    val array = ByteArray(remaining())
    get(array)
    return array
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
