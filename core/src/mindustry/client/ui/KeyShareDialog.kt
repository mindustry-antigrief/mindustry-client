package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.*
import mindustry.client.utils.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*

class KeyShareDialog(private val messageCrypto: MessageCrypto) : BaseDialog("Key Share") {
    private val keys = Table()
    private lateinit var importDialog: Dialog

    init {
        build()
    }

    private fun regenerate() {
        keys.clear()
        keys.defaults().pad(5f).left()
        for (key in messageCrypto.keys) {
            val table = Table()
            table.button(Icon.cancel, Styles.settingtogglei, 16f) {
                messageCrypto.keys.remove(key)
                regenerate()
            }.padRight(7f)
            table.label(key.name)
            keys.row(table)
        }
    }

    private fun build() {
        regenerate()

        cont.table{ t ->
            t.defaults().pad(5f)

            t.pane { pane ->
                pane.add(keys)
            }.growX()

            t.row()

            t.button("Import Key") {
                importDialog = dialog("Import Key") {
                    val nameInput = TextField("")
                    var name = ""
                    nameInput.maxLength = 30
                    nameInput.messageText = "Name"
                    nameInput.setFilter { _, c -> c != ' '}
                    nameInput.setValidator { n -> n.length >= 2 && n !in messageCrypto.keys.map { it.name }.also { name = n } }
                    cont.row(nameInput).width(400f)

                    val keyInput = TextField("")
                    var key: PublicKeyPair? = null
                    keyInput.messageText = "Key"
                    keyInput.setValidator { k -> MessageCrypto.base64public(k).also { key = it }.let { it != null } }
                    cont.row(keyInput).width(400f)

                    cont.row().table{ ta ->
                        ta.defaults().width(194f).pad(3f)
                        ta.button("Import") button2@{
                            messageCrypto.keys.add(KeyHolder(key!!, name, false, messageCrypto))
                            regenerate()
                            hide()
                        }.disabled { !keyInput.isValid || !nameInput.isValid }

                        ta.button("Close") {
                            hide()
                        }
                    }
                    addCloseListener()
                }.show()
            }.growX().get().label.setWrap(false)

            t.row()

            t.button("Export Key") {
                Main.messageCrypto.keyQuad
                Core.app.clipboardText = Main.messageCrypto.base64public()
                Toast(3f).add("@copied")
            }.growX().get().label.setWrap(false)

            t.row()

            t.button("Close") {
                hide()
            }.growX().get().label.setWrap(false)
        }
        keyDown {
            if(it == KeyCode.escape || it == KeyCode.back){
                if (this::importDialog.isInitialized && importDialog.isShown) Core.app.post(importDialog::hide) // This game hates being not dumb so this is needed
                else Core.app.post(this::hide)
            }
        }
    }
}
