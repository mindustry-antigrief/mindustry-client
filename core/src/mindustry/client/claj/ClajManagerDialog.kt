package mindustry.client.claj

import arc.*
import arc.graphics.*
import arc.net.*
import arc.scene.ui.layout.*
import arc.util.*
import mindustry.*
import mindustry.client.claj.ClajSupport.createRoom
import mindustry.gen.*
import mindustry.ui.*
import mindustry.ui.dialogs.*

class ClajManagerDialog : BaseDialog("@client.claj.manage") {
    var serverIP = "148.251.184.58"
    var serverPort = 9000
    var lastPing = -1
    var lastPingAt = Time.millis() // Prevents ping during init block
    private var room: Room? = null

    init { // FINISHME: Clean up this mess. Also implement proper claj banning and such since that's currently impossible
        addCloseButton()
        cont.table { rooms: Table ->
            rooms.defaults().width(750F)
            val list = Table()
            list.update { list.cells.retainAll { cell -> cell.get() != null } } // Awful.
            list.defaults().width(750F).padBottom(8f)
            rooms.add(list).row()
            rooms.field("$serverIP:$serverPort") { address: String ->
                serverIP = address.substringBefore(':')
                serverPort = Strings.parseInt(address.substringAfter(':'))
                lastPing = -1
                lastPingAt = Time.millis() // Delay any pings by a second to prevent spam pinging while typing
            }.maxTextLength(100).valid { address -> address.contains(':') }.row()
            rooms.button("@client.claj.generate") {
                try {
                    room = Room()
                    list.add(room).row()
                } catch (ignored: Throwable) {
                    Vars.ui.showErrorMessage(ignored.message)
                }
            }.disabled { list.children.size >= 4 }.padTop(8f).row()
            rooms.label { if (Time.timeSinceMillis(lastPingAt) > 1000) { lastPingAt = Time.millis(); Vars.net.pingHost(serverIP, serverPort, { lastPing = it.ping }) { lastPing = -2 } }; "Ping: " + if (lastPing == -2) "[scarlet]Error contacting server." else if (lastPing == -1) "[orange]Not yet contacted." else "${lastPing}ms" }
        }.height(550f).row()
        val l = cont.labelWrap("@client.claj.info").labelAlign(2, 8).padTop(16f).width(400f).get()
        l.style.fontColor = Color.lightGray
        l.clicked { Menus.openURI("https://mindustry.dev/CLaJ") }
    }

    inner class Room : Table() {
        val client: Client
        var link: String = ""
        var uses = 1

        init {
            table(Tex.underline) { cont: Table ->
                cont.add(link).name("link").growX().left().fontScale(.7f).ellipsis(true)
            }.growX()
            table { btns: Table ->
                btns.defaults().size(48f).padLeft(8f)
                btns.button(Icon.copy, Styles.clearNonei) { Core.app.clipboardText = link }
                btns.button(Icon.cancel, Styles.clearNonei) { close() }
            }
            client = createRoom(serverIP, serverPort, this, null)
        }

        fun close() {
            client.close()
            remove()
        }
    }
}
