package com.github.blahblahbloopster.ui

import arc.Core
import com.github.blahblahbloopster.Main
import com.github.blahblahbloopster.crypto.KeyFolder
import com.github.blahblahbloopster.crypto.MessageCrypto
import mindustry.Vars
import mindustry.client.Client
import mindustry.gen.Icon
import mindustry.ui.dialogs.BaseDialog

class KeyShareDialog : BaseDialog("Key Share") {

    init {
        build()
    }

    private fun build() {
        cont.labelWrap("To exchange keys with someone, export your key and have them import it, and vise versa.")  // todo: in game chat warning
        cont.row()
        cont.labelWrap("Note: While it is okay to share keys over a publicly-visible medium, DO NOT share keys in-game." +
                "  It can lead to security problems.")
        cont.row()
        cont.labelWrap("After exchanging keys, you will be able to securely chat with the person using the !e <name> [message...] command," +
                "and your regular messages will be verified for people with your key.")
        cont.row()
        cont.button("Import Key") {
            KeyImportDialog().show()
        }
        cont.row()
        cont.button("Export Key") {
            Main.messageCrypto.keyQuad ?: run {
                Client.mapping.generateKey()
            }
            Core.app.clipboardText = Main.messageCrypto.base64public() ?: ""
            Vars.ui.announce("Copied key to clipboard.\nHave the other person import it.")
        }
    }

    private class KeyImportDialog : BaseDialog("Key Import") {

        init {
            build()
        }

        private fun build() {
            KeyFolder.folder ?: run {hide(); return}
            val fldr = KeyFolder.folder!!
            cont.label("Imports someone else's key.  You can then receive verified messages from them," +
                    " and if they have your key you can chat with encryption.")
            cont.row()
            val name = cont.field("Easy-to-type name (no spaces)") {}.get()
            name.setValidator {
                it.length in 1..20 && !it.contains(" ")
            }

            cont.row()
            val keyInput = cont.field("Key (from the other person's \"export key\")") {}.get()
            cont.row()
            button(Icon.ok) {
                if (name.isValid) {
                    try {
                        MessageCrypto.base64public(name.text) ?: return@button
                        fldr.child("${name.text}.txt").writeString(keyInput.text)
                        hide()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            button("close") {
                hide()
            }
        }
    }
}
