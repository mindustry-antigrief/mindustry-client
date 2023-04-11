package mindustry.client.claj

import arc.*
import mindustry.*
import mindustry.client.claj.ClajSupport.joinRoom
import mindustry.client.claj.ClajSupport.parseLink
import mindustry.ui.*
import mindustry.ui.dialogs.*

class ClajJoinDialog : BaseDialog("@client.claj.join") {
    var lastLink = "CLaJLink#ip:port"
    var valid = false
    var output: String? = null

    init {
        cont.add("@client.claj.link").padRight(5f).right()
        cont.field(lastLink) { link -> setLink(link) }.size(550f, 54f).maxTextLength(100).valid { link -> setLink(link) }.left() // FINISHME: Cursed
        cont.row().label { if (lastLink.isBlank()) "[grey]${Core.bundle["client.claj.info"]}" else output }.fillX().wrap().center().colspan(2).get().clicked { Menus.openURI("https://mindustry.dev/CLaJ") }
        addCloseButton()
        buttons.button("@ok") {
            try {
                if (Vars.player.name.isBlank()) {
                    Vars.ui.showInfo("@noname")
                    return@button
                }
                val (key, ip, port) = parseLink(lastLink)
                joinRoom(ip, port, key) {
                    Vars.ui.join.hide()
                    hide()
                }
                Vars.ui.loadfrag.show("@connecting")
                Vars.ui.loadfrag.setButton {
                    Vars.ui.loadfrag.hide()
                    Vars.netClient.disconnectQuietly()
                }
            } catch (e: Throwable) {
                Vars.ui.showErrorMessage(e.message)
            }
        }.disabled { lastLink.isEmpty() || Vars.net.active() }
    }

    private fun setLink(link: String): Boolean {
        if (lastLink == link) return valid
        try {
            parseLink(link)
            output = "@join.valid"
            valid = true
        } catch (ignored: Throwable) {
            output = ignored.message
            valid = false
        }
        lastLink = link
        return valid
    }
}
