package mindustry.client.ui

import arc.graphics.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.Vars.ui
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.gen.*
import mindustry.ui.dialogs.*
import kotlin.random.*

object MarkerDialog : BaseDialog("Markers") {
    val pane = Table()
    init {
        cont.add("Minimap Markers").center().top()
        cont.row()
        cont.pane(pane).width(800f).grow()
        pane.add().width(pane.width)
        cont.row()
        buttons.button(Icon.add) { MarkerAddDialog().show() }.growX()
        buttons.button("Close") { hide() }.growX()
        addCloseListener()
        updatePane()

        shown { updatePane() }
    }

    private fun updatePane() {
        pane.clearChildren()

        for (marker in Markers) {
            val table = Table()
            table.margin(15f)
            table.width = pane.width
            table.image(marker.shape).left().pad(5f)//.width(20f)

            table.stack(Image(Tex.alphaBg), Image(Tex.whiteui).apply {
                update { setColor(marker.color) }
            }).size(50f).pad(5f).left().get().clicked {
                ui.picker.show(marker.color) { color -> marker.color = color }
            }

            table.add(marker.name).left().pad(5f).growX().get().clicked {
                ui.showTextInput("Name", "Name", marker.name) {
                    if (it.isNotBlank()) {
                        marker.name = it
                    }
                }
            }

            table.add().growX()

            table.add("(${marker.x}, ${marker.y})").right().pad(5f).get().clicked {
                ui.showTextInput("Coordinates", "Coordinates", "${marker.x}, ${marker.y}") {
                    if (it.matches("\\(?\\d+, ?\\d+\\)?".toRegex())) {
                        val matches = "\\d+".toRegex().findAll(it)
                        marker.x = matches.first().value.toInt()
                        marker.y = matches.last().value.toInt()
                        updatePane()
                    }
                }
            }

            table.button(Icon.trash) {
                Markers.remove(marker)
                updatePane()
            }.right().pad(5f)//.width(50f)

            pane.row(table).growX()
        }
    }

    class MarkerAddDialog : BaseDialog("Add Marker") {
        init {
            val nameField = TextField("")
            nameField.messageText = "Name"
            cont.row(nameField)

            val xField = cont.field("") {}.valid(Strings::canParsePositiveInt).growX().get()
            xField.messageText = "X"
            val yField = cont.field("") {}.valid(Strings::canParsePositiveInt).growX().get()
            yField.messageText = "Y"

            cont.row()

            var color = Color.HSVtoRGB(Random.nextFloat() * 360, 75f, 75f)
            cont.stack(Image(Tex.alphaBg), Image(Tex.whiteui).apply {
                update { setColor(color) }
            }).size(50f).pad(5f).left().get().clicked {
                ui.picker.show(color) { col -> color = col }
            }

            buttons.button("Ok") {
                if (xField.isValid && yField.isValid && nameField.text.isNotBlank()) {
                    Markers.add(
                        Markers.Marker(xField.text.toInt(), yField.text.toInt(), nameField.text, color, Icon.star)
                    )
                    updatePane()
                    hide()
                }
            }.growX()

            buttons.button("@back", Icon.left, this::hide).growX()
        }
    }
}
