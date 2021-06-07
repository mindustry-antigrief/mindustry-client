package mindustry.client

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.antigrief.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.game.*

/** WIP client logic class, similar to [mindustry.core.Logic] but for the client.
 * Handles various events and such.
 * TODO: Move the 9000 different bits of code throughout the client to here */
class ClientLogic {
    /** Create event listeners */
    init {
        Events.on(EventType.ServerJoinEvent::class.java) { // Run when the player joins a server
            Main.setPluginNetworking(false)
            Spectate.pos = null
        }

        Events.on(EventType.WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            ClientVars.lastJoinTime = Time.millis()
            PowerInfo.initialize()
            Navigation.stopFollowing()
            Navigation.obstacles.clear()
            ClientVars.configs.clear()
            Vars.ui.unitPicker.type = null
            Vars.control.input.lastVirusWarning = null
            ClientVars.dispatchingBuildPlans = false
            ClientVars.hidingBlocks = false
            ClientVars.hidingUnits = false
            ClientVars.showingTurrets = false
            if (Vars.state.rules.pvp) Vars.ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
        }

        Events.on(EventType.ClientLoadEvent::class.java) { // Run when the client finishes loading
            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("debug")) Log.level = Log.LogLevel.debug // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) Vars.platform.startDiscord()
            if (Core.settings.getBool("mobileui")) Vars.mobile = !Vars.mobile

            Autocomplete.autocompleters.add(BlockEmotes())
            Autocomplete.autocompleters.add(PlayerCompletion())
            Autocomplete.autocompleters.add(CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()

            Core.settings.getBoolOnce("client730") { Core.settings.put("disablemonofont", true) } // TODO: Remove later
        }

        Events.on(EventType.PlayerJoin::class.java) { e -> // Run when a player joins the server
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (Vars.ui.chatfrag.messages.isEmpty || !Strings.stripColors(Vars.ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has connected.")) && Time.timeSinceMillis(ClientVars.lastJoinTime) > 10000)
                Vars.player.sendMessage(Core.bundle.format("client.connected", e.player.name))
        }

        Events.on(EventType.PlayerLeave::class.java) { e -> // Run when a player leaves the server
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (Vars.ui.chatfrag.messages.isEmpty || !Strings.stripColors(Vars.ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has disconnected.")))
                Vars.player.sendMessage(Core.bundle.format("client.disconnected", e.player.name))
        }
    }
}