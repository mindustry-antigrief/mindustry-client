package mindustry.client.antigrief

import arc.*
import mindustry.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.net.*
import mindustry.ui.*

// FINISHME: Heavily work in progress leave logs
// FINISHME: Add a TraceInfo var to the player class
class LeaveLog {
    private val left = mutableListOf<Player>() // 100 last people to leave

    init {
        Events.on(EventType.PlayerLeave::class.java) { e ->
            e.player ?: return@on
            e.player.trace ?: return@on

            left.forEach { p -> if (p.trace.uuid == e.player.uuid()) left.remove(p) }
            while (left.size >= 100) left.removeFirst() // Keep 100 latest leaves
            left.add(e.player)
        }

        Events.on(EventType.PlayerJoin::class.java) { e -> // Trace players when they join, also traces all players on join
            if (!Vars.player.admin || e.player == null || e.player == Vars.player || e.player.admin) return@on

            Call.adminRequest(e.player, Packets.AdminAction.trace)
        }
    }

    fun addInfo(player: Player, info: Administration.TraceInfo) {

        for (n in left.size - 1 downTo 0) {
            val i = left[n]
            if (i.trace.ip == info.uuid || i.trace.ip == info.ip) { // Update info
                left.remove(i)
                if (i.trace.uuid != info.uuid) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed UUID: ${i.trace.uuid} -> ${info.uuid}")
                if (i.trace.ip != info.ip) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed IP: ${i.trace.ip} -> ${info.ip}")
                if (i.name != player.name) Vars.player.sendMessage("[scarlet]${player.name}[scarlet] has changed name, was previously: ${i.name}")
            }
        }

        player.trace = info
        left.add(player)
    }

    fun leftList() {
        dialog("Leaves, newest first") {
            for (i in left.size - 1 downTo 0) {
                val player = left[i]
                cont.button(player.name, Styles.nonet) { Vars.ui.traces.show(player, player.trace) }.wrapLabel(false)
                cont.row()
            }
            addCloseButton()
        }.show()
    }
}