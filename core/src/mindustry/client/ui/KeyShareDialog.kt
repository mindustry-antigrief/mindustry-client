package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.client.Main
import mindustry.client.crypto.KeyHolder
import mindustry.client.crypto.MessageCrypto
import mindustry.client.utils.dialog
import mindustry.client.utils.label
import mindustry.client.utils.row
import mindustry.gen.*
import mindustry.ui.dialogs.*

class KeyShareDialog(val messageCrypto: MessageCrypto) : BaseDialog("Key Share") {
    private val keys = Table()

    init {
        build()
    }

    private fun regenerate() {
        keys.clear()
        for (key in messageCrypto.keys) {
            val table = Table()
            table.label(key.name).left()
            table.button(Icon.cancel) {
                messageCrypto.keys.remove(key)
                regenerate()
            }.right()
            keys.row(table)
        }
    }

    private fun build() {
        clear()

        regenerate()

        pane {
            it.add(keys)
        }

        row()

        button("Import Key") {
            dialog("Import Key") {
                val nameInput = TextField("Name")
                nameInput.maxLength = 30
                row(nameInput)

                val keyInput = TextField("Key")
                row(keyInput)

                button("Import") button2@{
                    val key = MessageCrypto.base64public(keyInput.text) ?: return@button2
                    val name = nameInput.text
                    if (name.length !in 2..30 || name in messageCrypto.keys.map { it.name }) return@button2

                    messageCrypto.keys.add(KeyHolder(key, name, false, messageCrypto))
                    regenerate()
                    hide()
                }
                row()
                addCloseButton()
            }.show()
        }.growX()
        button("Export Key") {
            Main.messageCrypto.keyQuad
            Core.app.clipboardText = Main.messageCrypto.base64public()
            Vars.ui.showInfoFade("@copied")
        }.growX()
        button("Close") {
            hide()
        }.growX()
        keyDown(KeyCode.escape) { hide() } // Why is kotlin so ${adjective}?
    }
}
