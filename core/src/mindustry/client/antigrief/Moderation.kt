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
        init {
            Vars.netClient.addPacketHandler("playerdata") { // Handles autostats from plugins FINISHME: This is server-specific code. Treat it as such.
                if (Server.corium()) {
                    val json = JsonReader().parse(it)
                    if (Core.settings.getBool("logplayerdata")) Log.debug(json)

                    fun String.i() = json.getInt(this, Int.MAX_VALUE)
                    fun String.s() = json.getString(this, "unknown")
                    fun String.b() = json.getBoolean(this)

                    val id = "id".i()
                    val player = Groups.player.getByID(id) ?: return@addPacketHandler
                    player.serverID = "playercode".s()
                    player.serverFrozen = "frozen".b()
                    player.serverMuted = "muted".b()
                    val firstKnownModState = player.serverModerationStateKnown
                    player.serverModerationStateKnown = true

                    val rank = "rank".i() // Server-specific rank. 0 is unranked.
                    if (player == Vars.player) { // Set rank accordingly
                        if (rank != ClientVars.rank && rank >= 5 && (Server.current.freeze.canRun() || Server.current.mute.canRun())) {
                            Groups.player.each { p -> if (p != Vars.player) Server.current.getStats(p, true) }
                        }
                        ClientVars.rank = rank
                        Server.current.updateRank()
                    }
                    else if (firstKnownModState && rank == 0) { // If they're unranked, check if they're new
                        val games = "games".i()
                        val buildings = "buildings".i()
                        val time = "playtime".i()
                        val name = "realname".s()

                        if (games < 3 || buildings < 1000 || time < 60) { // Low-stat player; show a warning FINISHME: Settings for these values
                            fun Int.s() = if (this == Int.MAX_VALUE) "unknown" else toString()
                            Vars.ui.chatfrag.addMsg("[scarlet]Player [stat]$name [white](${player.serverID}) [scarlet]has [stat]${games.s()}[] games, [stat]${buildings.s()}[] builds, [stat]${time.s()}[] mins")
                                .addButton(name) { Spectate.spectate(player) }
                                .addButton(player.serverID) { Call.sendChatMessage("/stats ${player.id}") }
                        }
                    }
                }
            }

            Events.on(EventType.PlayerJoin::class.java) { e ->
                playerJoin(e.player)
            }

            Events.on(EventType.ServerJoinEvent::class.java) {
                rank = -1 // reset rank on server join
                Groups.player.each { it.serverModerationStateKnown = false }
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
            Server.current.getStats(player, Server.current.freeze.canRun() || Server.current.mute.canRun())
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
            val mainColor = if (info.timesJoined == 1) "scarlet" else "#777777"
            Vars.player.sendMessage("[stat]${player.plainName()} [$mainColor]has joined [stat]${info.timesJoined-1}[] times before, they have been kicked [stat]${info.timesKicked}[] times")
        }

        // These next three lines are the laziest way of deduplicating the messages, but it works, so we don't really care.
        val ids = ObjectSet<String>()
        val ips = ObjectSet<String>()
        val names = ObjectSet<String>()
        for (n in traces.size - 1 downTo 0) {
            val i = traces[n]
            if (i.trace.ip == info.uuid || i.trace.ip == info.ip) { // Update info
                if (i.trace.uuid != info.uuid && ids.add(i.trace.uuid)) Vars.player.sendMessage("[stat]${player.plainName()} [scarlet]has changed UUID: [stat]${i.trace.uuid}[] -> [stat]${info.uuid}[]")
                if (i.trace.ip != info.ip && ips.add(i.trace.ip)) Vars.player.sendMessage("[stat]${player.plainName()} [scarlet]has changed IP: [stat]${i.trace.ip}[] -> [stat]${info.ip}[]")
                if (i.name != player.name && names.add(i.name)) Vars.player.sendMessage("[stat]${player.plainName()} [scarlet]has changed name, was previously: [stat]${i.name}[]")
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
