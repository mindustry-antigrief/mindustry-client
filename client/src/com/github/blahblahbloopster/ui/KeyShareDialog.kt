package com.github.blahblahbloopster.ui

import arc.Core
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import com.github.blahblahbloopster.Main
import com.github.blahblahbloopster.crypto.KeyHolder
import com.github.blahblahbloopster.crypto.MessageCrypto
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog

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

        labelWrap("To exchange keys have both people import each other's key.  Do not do this in in-game chat, " +
                "because it can be altered by servers, introducing security problems.")

        regenerate()

        pane {
            it.add(keys)
        }

        row()

        button("Import Key") {
            Vars.ui.dialog("Import Key") {
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
            Core.app.clipboardText = Main.messageCrypto.base64public()
            Vars.ui.showInfoFade("@copied")
        }.growX()
        button("Close") {
            hide()
        }.growX()
    }
}
