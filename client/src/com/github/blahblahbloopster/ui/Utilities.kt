package com.github.blahblahbloopster.ui

import arc.scene.ui.Label
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table

fun Table.label(text: String): Cell<Label> {
    return add(Label(text))
}
