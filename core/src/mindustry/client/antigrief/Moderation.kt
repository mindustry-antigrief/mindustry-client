package mindustry.client.antigrief

import arc.*
import arc.struct.*
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
        @JvmField var freezePlayer: Player? = null
        @JvmField var freezeState: Boolean = false
        @JvmField var mutePlayer: Player? = null
        @JvmField var muteState: Boolean = false
        init {
            Vars.netClient.addPacketHandler("playerdata") { // Handles autostats from plugins FINISHME: This is server-specific code. Treat it as such.
                if (Server.io() || Server.corium()) {
                    val json = JsonReader().parse(it)
                    if (Core.settings.getBool("logplayerdata")) Log.debug(json)

                    fun String.i() = json.getInt(this, Int.MAX_VALUE)
                    fun String.s() = json.getString(this, "unknown")
                    fun String.b() = json.getBoolean(this)

                    val id = "id".i()
                    val player = Groups.player.getByID(id) ?: return@addPacketHandler
                    player.serverID = "playercode".s()

                    if (player === freezePlayer) { // FINISHME: Use callbacks instead of this jank.
                        freezeState = "frozen".b()
                        if (freezeState) Server.current.thaw.invoke(player)
                        else Server.current.freeze.invoke(player)
                        freezePlayer = null
                    }
                    if (player === mutePlayer) {
                        muteState = "muted".b()
                        if (muteState) Server.current.unmute.invoke(player)
                        else Server.current.mute.invoke(player)
                        mutePlayer = null
                    }

                    val rank = "rank".i() // Server-specific rank. 0 is unranked.
                    if (player == Vars.player) { // Set rank accordingly
                        ClientVars.rank = rank
                        Server.current.updateRank()
                    }
                    else if (rank == 0) { // If they're unranked, check if they're new
                        val games = "games".i()
                        val buildings = "buildings".i()
                        val time = "playtime".i()
                        val name = "realname".s()

                        if (games < 3 || buildings < 1000 || time < 60) { // Low-stat player; show a warning FINISHME: Settings for these values
                            fun Int.s() = if (this == Int.MAX_VALUE) "unknown" else toString()
                            Vars.ui.chatfrag.addMsg("[scarlet]Player $name [scarlet](${player.serverID}) has ${games.s()} games, ${buildings.s()} builds, ${time.s()} mins")
                                .addButton(name) { Spectate.spectate(player) }
                                .addButton(player.serverID) { Call.sendChatMessage("/stats ${player.id}") }
                        }
                    }
                }
            }

            Vars.netClient.addPacketHandler("freeze_confirm") {
                val json = JsonReader().parse(it)
                if (Core.settings.getBool("logfreeze_confirm")) Log.debug(json)

                val player = Groups.player.getByID(json.getInt("id", Int.MAX_VALUE)) ?: return@addPacketHandler
                Vars.ui.chatfrag.addMsg("[accent]${player.coloredName()}[accent]'s freeze state was updated to: ${json.getString("frozen", "unknown")}")
            }

            Events.on(EventType.PlayerJoin::class.java) { e ->
                playerJoin(e.player)
            }

            Events.on(EventType.ServerJoinEvent::class.java) {
                rank = -1 // reset rank on server join
                Server.current.getStats(Vars.player, true) // Stat trace self to get rank info
            }

            /** We need to pull stats to get server id every time the world is reloaded as players are readded. This is janky but it's easier than the alternative of trying to maintain a cache on our end. */
            Events.on(EventType.WorldLoadEvent::class.java) { // FINISHME: Implement proper caching. This is not sustainable.
                Time.run(60F) { Groups.player.each { if (it != Vars.player) playerJoin(it) } }
            }
        }

        /** Called on player join. Also called on every player on first join */
        fun playerJoin(player: Player?) {
            if (player == null || player == Vars.player) return

            if (Core.settings.getBool("seer-enabled")) Seer.registerPlayer(player)
            // If admin and enabled, trace every non-admin
            if (Core.settings.getBool("modenabled") && Server.current.adminui() && !player.admin) {
                silentTrace++
                Call.adminRequest(player, Packets.AdminAction.trace, null)
            }
            // Get stats for all players
            Server.current.getStats(player)
        }
    }

    init {
        Events.on(EventType.PlayerLeave::class.java) { e ->
            e.player ?: return@on
            e.player.trace ?: return@on

//            traces.forEach { p -> if (p.trace.uuid == e.player.trace.uuid || p.trace.ip == e.player.trace.ip) traces.remove(p) } FINISHME: Remove dupe traces and add the relevant info to the new trace
            while (traces.size >= Core.settings.getInt("leavecount")) traces.removeAt(0) // Keep 100 latest leaves
            traces.add(e.player)
        }
    }

    fun addInfo(player: Player, info: Administration.TraceInfo) {
        // FINISHME: Integrate these with join/leave messages
        if (Time.timeSinceMillis(lastJoinTime) > 10000 && player.trace == null) {
            // Don't send in admin chat as it can get spammy
//            if (info.timesJoined > 10 && info.timesKicked < 3) Vars.player.sendMessage("[accent]${player.name}[accent] has joined ${info.timesJoined-1} times before, they have been kicked ${info.timesKicked} times")
//            else sendMessage("/a [scarlet]${player.name}[scarlet] has joined ${info.timesJoined-1} times before, they have been kicked ${info.timesKicked} times")
            Vars.player.sendMessage("[scarlet]${player.name} [scarlet]has joined ${info.timesJoined-1} times before, they have been kicked ${info.timesKicked} times")
        }

        // These next three lines are the laziest way of deduplicating the messages but it works so we don't really care.
        val ids = ObjectSet<String>()
        val ips = ObjectSet<String>()
        val names = ObjectSet<String>()
        for (n in traces.size - 1 downTo 0) {
            val i = traces[n]
            if (i.trace.ip == info.uuid || i.trace.ip == info.ip) { // Update info
                if (i.trace.uuid != info.uuid && ids.add(i.trace.uuid)) Vars.player.sendMessage("[scarlet]${player.name} [scarlet]has changed UUID: ${i.trace.uuid} -> ${info.uuid}")
                if (i.trace.ip != info.ip && ips.add(i.trace.ip)) Vars.player.sendMessage("[scarlet]${player.name} [scarlet]has changed IP: ${i.trace.ip} -> ${info.ip}")
                if (i.name != player.name && names.add(i.name)) Vars.player.sendMessage("[scarlet]${player.name} [scarlet]has changed name, was previously: ${i.name}")
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
