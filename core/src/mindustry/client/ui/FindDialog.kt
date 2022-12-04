package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.core.*
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
                val results = mutableListOf<Tile>()

                for (t in world.tiles) { // FINISHME: There are far better ways of doing this lol
                    if (t.block() == block || t.floor() == block || t.overlay() == block) {
                        if (allyOnly.isChecked && t.team().isEnemy(player.team())) continue
                        results += t
                    }
                }

                if (results.isEmpty()) {
                    ui.chatfrag.addMessage(Core.bundle.format("client.find.notfound", block.localizedName))
                } else {
                    results.sortBy { it.dst(player) }
                    val closest = results.first()
                    ClientVars.lastSentPos.set(closest.x.toFloat(), closest.y.toFloat())

//                    val text = "${Core.bundle.format("client.find.found", block.localizedName, closest.x, closest.y, results.size)} ${Iconc.left} ${Iconc.right}"
                    val text = Core.bundle.format("client.find.found", block.localizedName, closest.x, closest.y, results.size)
                    val msg = ui.chatfrag.addMsg(text)

/*                  FINISHME: This implementation will cause a mem leak as the results list will exist forever
                    val buttonIdx = msg.formattedMessage.indexOf(Iconc.left)
                    var idx = 0
                    msg.buttons.add(ChatFragment.ClickableArea(buttonIdx, buttonIdx + 1) { idx -= 5 }) // Left arrow
                    msg.buttons.add(ChatFragment.ClickableArea(buttonIdx + 2, buttonIdx + 3) { idx += 5 }) // Right arrow
                    for (i in idx until min(idx + 5, results.size)) {
                        // List the next 5 blocks or whatever
                    }
*/

                    NetClient.findCoords(msg)
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
