package mindustry.client.antigrief

import arc.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.client.*
import mindustry.client.ClientVars.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.net.*
import mindustry.ui.*
import java.util.concurrent.*

class Moderation {
    private val traces = CopyOnWriteArrayList<Player>() // last people to leave

    companion object {
        init {
            Vars.netClient.addPacketHandler("playerdata") { // Handles autostats from plugins
                if (io() || phoenix()) {
                    val json = JsonReader().parse(it)
                    Log.debug(json)

                    fun String.i() = json.getInt(this, Int.MAX_VALUE)
                    fun String.s() = json.getString(this, "unknown")

                    val player = Groups.player.getByID("id".i()) ?: return@addPacketHandler
                    val rank = "rank".i() // 0 for unranked, 1 for active, 2 for veteran etc
                    if (player == Vars.player) ClientVars.rank = rank // Set rank var accordingly
                    else if (rank == 0) { // If they're unranked, check if they're new
                        val games = "games".i()
                        val buildings = "buildings".i()
                        val time = "playtime".i()
                        val name = "realname".s()
                        val serverid = "playercode".s()

                        if (games < 3 || buildings < 1000 || time < 60) { // Low stat player; show a warning FINISHME: Settings for these values
                            fun Int.s() = if (this == Int.MAX_VALUE) "unknown" else toString()
                            Vars.ui.chatfrag.addMsg("[scarlet]Player $name [scarlet]($serverid) has ${games.s()} games, ${buildings.s()} builds, ${time.s()} mins")
                                .addButton(name) { Spectate.spectate(player) }
                                .addButton(serverid) { Call.sendChatMessage("/stats ${player.id}") }
                        }
                    }
                }
            }

            Events.on(EventType.PlayerJoin::class.java) { e ->
                if (e.player == Vars.player) return@on

                if (Core.settings.getBool("autostats") && (io() || phoenix())) { // Makes use of a custom packet on io
                    Call.serverPacketReliable("playerdata_by_id", e.player.id.toString())
                }
            }

            Events.on(EventType.ServerJoinEvent::class.java) {
                rank = -1 // reset rank on server join
                if (io() || phoenix()) Call.serverPacketReliable("playerdata_by_id", Vars.player.id.toString()) // Stat trace self to get rank info
            }
        }
    }

    init {
        Events.on(EventType.PlayerLeave::class.java) { e ->
            e.player ?: return@on
            e.player.trace ?: return@on

//            traces.forEach { p -> if (p.trace.uuid == e.player.trace.uuid || p.trace.ip == e.player.trace.ip) traces.remove(p) } FINISHME: Remove dupe traces and add the relevant info to the new trace
            while (traces.size >= Core.settings.getInt("leavecount")) traces.removeFirst() // Keep 100 latest leaves
            traces.add(e.player)
        }

        Events.on(EventType.PlayerJoin::class.java) { e -> // Trace players when they join, also traces all players on join
            if (e.player == null || e.player == Vars.player ||!Core.settings.getBool("modenabled")) return@on
            Seer.registerPlayer(e.player)
            if (!Vars.player.admin ||  e.player.admin) return@on

            silentTrace++
            Call.adminRequest(e.player, Packets.AdminAction.trace)
        }
    }

    fun addInfo(player: Player, info: Administration.TraceInfo) {
        // FINISHME: Integrate these with join/leave messages
        if (Time.timeSinceMillis(lastJoinTime) > 10000 && player.trace == null) {
            if (info.timesJoined > 10 && info.timesKicked < 3) Vars.player.sendMessage("[accent]${player.name}[accent] has joined ${info.timesJoined-1} times before, they have been kicked ${info.timesKicked} times")
            else sendMessage("/a [scarlet]${player.name}[scarlet] has joined ${info.timesJoined-1} times before, they have been kicked ${info.timesKicked} times")
        }

        for (n in traces.size - 1 downTo 0) {
            val i = traces[n]
            if (i.trace.ip == info.uuid || i.trace.ip == info.ip) { // Update info
                if (i.trace.uuid != info.uuid) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed UUID: ${i.trace.uuid} -> ${info.uuid}")
                if (i.trace.ip != info.ip) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed IP: ${i.trace.ip} -> ${info.ip}")
                if (i.name != player.name) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed name, was previously: ${i.name}")
            }
        }

        player.trace = info
    }

    fun leftList() {
        dialog("Leaves, newest first") {
            cont.pane {
                for (player in traces.asReversed()) {
                    it.button(player.name, Styles.nonet) { Vars.ui.traces.show(player, player.trace, true) }.wrapLabel(false).minWidth(100f)
                    it.row()
                }
            }.growY()
            addCloseButton()
        }.show()
    }
}
