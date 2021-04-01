package com.github.blahblahbloopster.ui

import arc.Core
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.geom.Geometry
import arc.scene.ui.Image
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.Vars.player
import mindustry.client.ClientVars
import mindustry.client.utils.BiasedLevenshtein
import mindustry.ui.Cicon
import mindustry.ui.dialogs.BaseDialog
import mindustry.world.Block
import mindustry.world.Tile

object FindDialog : BaseDialog("@find") {
    private val imageTable = Table()
    private val images = List(10) { Image() }
    private val inputField = TextField()
    private var guesses: List<Block> = emptyList()

    private fun updateGuesses() {
        guesses = Vars.content.blocks().copy().toMutableList().sortedBy { BiasedLevenshtein.biasedLevenshtein(it.localizedName.toLowerCase(), inputField.text.toLowerCase()) }
    }

    init {
        for (img in images) {
            imageTable.add(img)
            imageTable.row()
        }
        cont.add(inputField)
        cont.row()
        cont.add(imageTable)

        inputField.typed {
            updateGuesses()
            if (guesses.size >= images.size) {  // You can never be too careful
                for (i in images.indices) {
                    val guess = guesses[i]
                    images[i].setDrawable(guess.icon(Cicon.medium))
                }
            }
        }

        keyDown {
            if (it == KeyCode.escape) {
                hide()
            } else if (it == KeyCode.enter) {
                val filtered = mutableListOf<Tile>()
                val block = guesses[0]
                Vars.world.tiles.eachTile { tile ->
                    if (tile.isCenter && tile.block().id == block.id && tile.team() == player.team()) {
                        filtered.add(tile)
                    }
                }
                val closest = Geometry.findClosest(player.x, player.y, filtered)
                if (closest == null) {
                    Vars.ui.chatfrag.addMessage("No ${block.localizedName} was found", "client", Color.coral.cpy().mul(0.75f))
                } else {
                    ClientVars.lastSentPos.set(closest.x.toFloat(), closest.y.toFloat())
                    //TODO: Make the line below use toasts similar to UnitPicker.java
                    Vars.ui.chatfrag.addMessage("Found ${block.localizedName} at ${closest.x},${closest.y} (!go to go there)", "client", Color.coral.cpy().mul(0.75f))
                }
                Core.app.post(this::hide)
            }
        }

        setup()
        shown(this::setup)
        addCloseButton()
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
