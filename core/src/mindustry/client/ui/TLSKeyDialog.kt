package mindustry.client.ui

import arc.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.client.*
import mindustry.client.crypto.*
import mindustry.client.utils.*
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*
import java.security.cert.*

class TLSKeyDialog : BaseDialog("@client.certs.manage.title") {
    private val keys = Table()
    private lateinit var importDialog: Dialog
    private lateinit var aliasDialog: Dialog
    private var built = false

    init {
        shown(::build)
    }

    private fun regenerate() {
        keys.clear()
        keys.defaults().pad(5f).left()
        val store = Main.keyStorage
        for (cert in store.trusted()) {
            val table = Table()
            table.button(Icon.cancel, Styles.settingTogglei, 16f) {
                if (Main.keyStorage.builtInCerts.contains(cert)) return@button
                store.untrust(cert)
                regenerate()
            }.padRight(20f).tooltip(if (Main.keyStorage.builtInCerts.contains(cert)) "@client.cert.cantdelete" else "@save.delete").disabled(Main.keyStorage.builtInCerts.contains(cert))

            table.button(Icon.edit, Styles.settingTogglei, 16f) {
                aliasDialog = dialog("@client.cert.alias") {
                    addCloseListener()
                    val aliasInput = TextField("")
                    aliasInput.setFilter { _, c -> c.isLetterOrDigit() }
                    aliasInput.messageText = "@client.cert.noalias"
                    cont.row(aliasInput).width(400f)

                    cont.row().table { ta ->
                        ta.defaults().width(194f).pad(3f)
                        ta.button("@ok"){
                            if (aliasInput.text.isBlank()) {
                                store.removeAlias(cert)
                                regenerate()
                                hide()
                                return@button
                            }
                            if ((store.trusted().any { it.readableName.equals(aliasInput.text, true) }) || store.aliases().any { it.second.equals(aliasInput.text, true) }) {
                                Toast(3f).label("@client.cert.aliastaken")
                                return@button
                            }
                            store.alias(cert, aliasInput.text)
                            regenerate()
                            hide()
                        }

                        ta.button("@close", ::hide)
                    }
                }.show()
            }.padRight(10f).tooltip("@edit")
            table.label(cert.readableName).padRight(10f)
            table.label("(${store.alias(cert) ?: "client.cert.noalias".bundle()})").right()
            keys.row(table)
        }
    }

    private fun build() {
        if (built) return
        val store = Main.keyStorage

        if (!store.loaded()) {
            Core.app.post(::hide)
            Vars.ui.showText("@client.certs.loading.title", "@client.certs.loading.text")
            return
        }

        if (store.cert() == null || store.key() == null || store.chain() == null) { // Create cert
            Core.app.post { // Post is needed otherwise this will show up behind the key dialog
                Vars.ui.showTextInput("@client.cert.name.title", "@client.cert.name.text", 32, Core.settings.getString("name", ""), false, { text -> // On submission
                    if (text.length < 2) {
                        hide()
                        return@showTextInput
                    }
                    if (text.asciiNoSpaces() != text) {
                        hide()
                        Vars.ui.showInfoFade("@client.keyprincipalspaces")
                        return@showTextInput
                    }

                    val key = genKey()
                    val cert = genCert(key, null, text)

                    store.cert(cert)
                    store.key(key, listOf(cert))

                    build()
                }, ::hide) // Hide on close
            }
            return
        }

        built = true
        addCloseButton()
        regenerate()

        cont.table{ t ->
            t.defaults().pad(5f)

            t.pane { pane ->
                pane.add(keys)
            }.growX()

            t.row()

            t.button("@client.importkey") {
                importDialog = dialog("@client.importkey") {
                    addCloseListener()
                    val keyInput = TextField("")
                    keyInput.messageText = "@client.cert.word"
                    keyInput.setValidator { k -> k.isNotEmpty() }
                    cont.row(keyInput).width(400f)

                    cont.row().table{ ta ->
                        ta.defaults().width(194f).pad(3f)
                        ta.button("@client.importkey") button2@{
                            val factory = CertificateFactory.getInstance("X509")
                            val stream = keyInput.text.replace(" ", "").base64()?.inputStream() ?: return@button2
                            try {
                                val cert = factory.generateCertificate(stream) as? X509Certificate ?: return@button2
                                if (cert.readableName.asciiNoSpaces() != cert.readableName) {
                                    Vars.ui.showInfoFade("@client.keyprincipalspaces")  // spaces break the commands
                                    return@button2
                                }

                                Vars.ui.showConfirm("@client.importkey", cert.readableName) {
                                    store.trust(cert)
                                    regenerate()
                                }

                            } catch (e: CertificateException) { return@button2 }
                            hide()
                        }.disabled { !keyInput.isValid }

                        ta.button("@close") {
                            hide()
                        }
                    }
                }.show()
            }.growX().wrapLabel(false)

            t.row()

            t.button("@client.exportkey") {
                Core.app.clipboardText = store.cert()?.encoded?.base64() ?: return@button
                Toast(3f).add("@copied")
            }.growX().get().label.setWrap(false)

            t.row()

            t.button("@close") {
                hide()
            }.growX().get().label.setWrap(false)
        }
        keyDown {
            if(it == KeyCode.escape || it == KeyCode.back){
                if (this::importDialog.isInitialized && importDialog.isShown) Core.app.post(importDialog::hide) // This game hates being not dumb so this is needed
                else if (this::aliasDialog.isInitialized && aliasDialog.isShown) Core.app.post(aliasDialog::hide) // Same as above
                else Core.app.post(this::hide)
            }
        }
    }
}
