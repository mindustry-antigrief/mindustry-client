package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.Vars
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.entities.*
import mindustry.ui.dialogs.*
import mindustry.world.*

object FindDialog : BaseDialog("@find") {
    private val imageTable = Table()
    private val images = List(10) { Image() }
    private val inputField = TextField()
    private var guesses: List<Block> = emptyList()

    private fun updateGuesses() {
        guesses = content.blocks().copy().toMutableList().sortedBy { BiasedLevenshtein.biasedLevenshteinInsensitive(it.localizedName, inputField.text) }
    }

    init {
        var first = true
        for (img in images) {
            imageTable.add(img).size(64f).padBottom(if (first) 30f else 10f)
            imageTable.row()
            first = false
        }
        val allyOnly = CheckBox("Ally Only?")
        cont.add(allyOnly)
        cont.row()
        cont.add(inputField)
        cont.row()
        cont.add(imageTable)

        inputField.typed {
            updateGuesses()
            if (guesses.size >= images.size) {  // You can never be too careful
                for (i in images.indices) {
                    val guess = guesses[i]
                    images[i].setDrawable(guess.uiIcon)
                }
            }
        }

        keyDown {
            if (it == KeyCode.enter) {
                if (guesses.isEmpty()) return@keyDown // Pasting an emoji will cause this to crash otherwise
                val block = guesses[0]
                var closest: Tile? = null
                var dst2 = Float.MAX_VALUE
                var count = 0

                for (t in world.tiles) {
                    if (t.block() == block) {
                        if (allyOnly.isChecked && t.team().isEnemy(player.team())) continue
                        val d = t.dst2(player)
                        if (d < dst2) {
                            closest = t
                            dst2 = d
                            count++
                        }
                    }
                }

                if (closest == null) {
                    ui.chatfrag.addMessage("No ${block.localizedName} was found", ClientVars.user)
                } else {
                    ClientVars.lastSentPos.set(closest.x.toFloat(), closest.y.toFloat())
                    // FINISHME: Make the line below use toasts similar to UnitPicker.java
                    ui.chatfrag.addMessage("Found ${block.localizedName} at ${closest.x},${closest.y}, !go to go there ($count found total)", ClientVars.user)
                }
                Core.app.post(this::hide)
            }
        }

        setup()
        shown(this::setup)
        addCloseListener()
    }

    private fun setup() {
        inputField.clearText()
        Core.app.post {
            Core.app.post {
                inputField.requestKeyboard()
            }
        }

        images.forEach(Image::clear)
    }
}
