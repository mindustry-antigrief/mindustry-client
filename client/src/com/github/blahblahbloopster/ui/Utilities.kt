package com.github.blahblahbloopster.ui

import arc.scene.Element
import arc.scene.ui.Dialog
import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import mindustry.core.UI
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.temporal.Temporal

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

fun Temporal.age() = this.secondsBetween(Instant.now())

/** Adds an element to the table followed by a row. */
fun Table.row(element: Element): Cell<Element> {
    val out = add(element)
    row()
    return out
}

inline fun UI.dialog(name: String, style: Dialog.DialogStyle = Styles.defaultDialog, dialog: BaseDialog.() -> Unit): Dialog {
    return BaseDialog(name, style).apply(dialog)
}
