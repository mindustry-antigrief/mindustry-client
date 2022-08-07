package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.ui.dialogs.*
import mindustry.world.*

object FindDialog : BaseDialog("@client.find") {
    private val imageTable = Table()
    private val images = MutableList(10) { Image() }
    private val inputField = TextField()
    private var guesses: MutableList<Block> = mutableListOf()

    private fun updateGuesses() {
        guesses = content.blocks().copy().toMutableList().apply { sortBy { BiasedLevenshtein.biasedLevenshteinInsensitive(it.name, inputField.text) } }
    }

    private fun updateImages() {
        for ((i, img) in images.withIndex()) {
            if (i >= guesses.size) break
            val guess = guesses[i]
            img.setDrawable(guess.uiIcon)
        }
    }

    init {
        for ((i, img) in images.withIndex()) {
            img.clicked { // When image clicked, select it
                val gi = guesses[i]
                guesses[i] = guesses[0]
                guesses[0] = gi

                updateImages()
            }
            imageTable.add(img).size(48f).padBottom(if (i == 0) 30f else 10f)
            if (i != images.size - 1) imageTable.row()
        }
        val allyOnly = CheckBox("@client.find.allyonly")
        cont.row(allyOnly)
        cont.row(inputField)
        cont.add(imageTable)

        inputField.typed {
            updateGuesses()
            updateImages()
        }

        keyDown {
            if (it == KeyCode.enter) {
                if (guesses.isEmpty()) return@keyDown // Pasting an emoji will cause this to crash otherwise
                val block = guesses[0]
                var closest: Tile? = null
                var dst2 = Float.MAX_VALUE
                var count = 0

                for (t in world.tiles) {
                    if (t.block() == block || t.floor() == block || t.overlay() == block) {
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
                    ui.chatfrag.addMessage(Core.bundle.format("client.find.notfound", block.localizedName))
                } else {
                    ClientVars.lastSentPos.set(closest.x.toFloat(), closest.y.toFloat())
                    // FINISHME: Make the line below use toasts similar to UnitPicker.java
                    ui.chatfrag.addMessage(Core.bundle.format("client.find.found", block.localizedName, closest.x, closest.y, count))
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
    }
}
