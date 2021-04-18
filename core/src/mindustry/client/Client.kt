package mindustry.client

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.math.Mathf
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import mindustry.Vars
import mindustry.client.Main.setPluginNetworking
import mindustry.client.Spectate.spectate
import mindustry.client.antigrief.PowerInfo
import mindustry.client.antigrief.TileLogs.reset
import mindustry.client.navigation.BuildPath
import mindustry.client.navigation.Markers
import mindustry.client.navigation.Navigation
import mindustry.client.ui.ChangelogDialog
import mindustry.client.utils.*
import mindustry.client.utils.completion.Completions
import mindustry.client.utils.completion.IconCompletions
import mindustry.core.NetClient
import mindustry.core.World
import mindustry.game.EventType.ClientLoadEvent
import mindustry.game.EventType.WorldLoadEvent
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.input.DesktopInput
import mindustry.net.Administration
import mindustry.type.UnitType
import kotlin.random.Random

object Client {

    fun initialize() {
        registerCommands()

        Events.on(WorldLoadEvent::class.java) {
            setPluginNetworking(false)
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                reset(Vars.world)
            }
            PowerInfo.initialize()
            Navigation.stopFollowing()
            Navigation.obstacles.clear()
            ClientVars.configs.clear()
            Vars.ui.unitPicker.found = null
            Vars.control.input.lastVirusWarning = null
            ClientVars.dispatchingBuildPlans = false
            ClientVars.hidingBlocks = false
            ClientVars.hidingUnits = false
            ClientVars.showingTurrets = false
            if (Vars.state.rules.pvp) Vars.ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
        }

        Events.on(ClientLoadEvent::class.java) {
            val changeHash = Core.files.internal("changelog").readString()
                .hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)
            if (Core.settings.getBool("debug")) Log.level =
                Log.LogLevel.debug // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) Vars.platform.startDiscord()

//            Autocomplete.autocompleters.add(BlockEmotes())
//            Autocomplete.autocompleters.add(PlayerCompletion())
//            Autocomplete.autocompleters.add(CommandCompletion())
//
//            Autocomplete.initialize()

            Completions.engines.add(IconCompletions)

            IconCompletions.initialize()

            Navigation.navigator.init()
        }
    }

    fun update() {
        Navigation.update()
        PowerInfo.update()
        Spectate.update()
        if (!ClientVars.configs.isEmpty) {
            try {
                if (ClientVars.configRateLimit.allow(
                        Administration.Config.interactRateWindow.num() * 1000L,
                        Administration.Config.interactRateLimit.num()
                    )
                ) {
                    val req = ClientVars.configs.removeLast()
                    val tile = Vars.world.tile(req.x, req.y)
                    if (tile != null) {
//                            Object initial = tile.build.config();
                        req.run()
                        //                            Timer.schedule(() -> {
//                                 if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
//                                 if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
//                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
                    }
                }
            } catch (e: Exception) {
                Log.err(e)
            }
        }
    }

    private fun registerCommands() {
        register(
            "help [page]", "Lists all client commands."
        ) { args, player ->
            if (args.isNotEmpty() && !Strings.canParseInt(
                    args[0]
                )
            ) {
                player.sendMessage("[scarlet]'page' must be a number.")
                return@register
            }
            val commandsPerPage = 6
            var page = if (args.isNotEmpty()) Strings.parseInt(args[0]) else 1
            val pages = Mathf.ceil(ClientVars.clientCommandHandler.commandList.size.toFloat() / commandsPerPage)
            page--
            if (page >= pages || page < 0) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] $pages[scarlet].")
                return@register
            }
            val result = StringBuilder()
            result.append(
                Strings.format(
                    "[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n",
                    page + 1,
                    pages
                )
            )
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(ClientVars.clientCommandHandler.commandList.size)) {
                val command = ClientVars.clientCommandHandler.commandList[i]
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText)
                    .append("[lightgray] - ").append(command.description).append("\n")
            }
            player.sendMessage(result.toString())
        }

        register(
            "unit <unit-name>", "Swap to specified unit"
        ) { args, _: Player ->
            Vars.ui.unitPicker.findUnit(Vars.content.units().copy().sort { b: UnitType ->
                BiasedLevenshtein.biasedLevenshtein(
                    args[0], b.name
                )
            }.first())
        }

        register(
            "go [x] [y]",
            "Navigates to (x, y) or the last coordinates posted to chat"
        ) { args, player: Player ->
            try {
                if (args.size == 2) ClientVars.lastSentPos[args[0].toFloat()] = args[1].toFloat()
                Navigation.navigateTo(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            }
        }

        register(
            "lookat [x] [y]",
            "Moves camera to (x, y) or the last coordinates posted to chat"
        ) { args, player: Player ->
            try {
                DesktopInput.panning = true
                if (args.size == 2) ClientVars.lastSentPos[args[0].toFloat()] = args[1].toFloat()
                spectate(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            }
        }

        register(
            "here [message...]", "Prints your location to chat with an optional message"
        ) { args, player: Player ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.isEmpty()) "" else args[0] + " ",
                    player.tileX(),
                    player.tileY()
                )
            )
        }

        register(
            "cursor [message...]", "Prints cursor location to chat with an optional message"
        ) { args, _: Player ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.isEmpty()) "" else args[0] + " ",
                    Vars.control.input.rawTileX(),
                    Vars.control.input.rawTileY()
                )
            )
        }

        register("builder [options...]", "Starts auto build with optional arguments, prioritized from first to last."
        ) { args, _: Player ->
            Navigation.follow(BuildPath(if (args.isEmpty()) "" else args[0]))
        } // TODO: This is so scuffed lol

        register("tp <x> <y>",
            "Teleports to (x, y), only works on servers without strict mode enabled."
        ) { args, player ->
            try {
                NetClient.setPosition(
                    World.unconv(args[0].toFloat()), World.unconv(
                        args[1].toFloat()
                    )
                )
            } catch (e: Exception) {
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300")
            }
        }

        // Not sure if the register function can handle this case
        ClientVars.clientCommandHandler.register(
            "", "[message...]", "Lets you start messages with an !"
        ) { args, _: Player -> Call.sendChatMessage("!" + if (args.size == 1) args[0] else "") }

        register(
            "shrug [message...]", "Sends the shrug unicode emoji with an optional message"
        ) { args, _: Player -> Call.sendChatMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "") }

        register("login [name] [pw]", "Used for CN. [scarlet]Don't use this if you care at all about security.") { args, _: Player ->
            if (args.size == 2) Core.settings.put(
                "cnpw",
                args[0] + " " + args[1]
            ) else Call.sendChatMessage("/login " + Core.settings.getString("cnpw", ""))
        }

        register("js <code...>", "Runs JS on the client.") { args, player: Player ->
            player.sendMessage(
                "[accent]" + Vars.mods.scripts.runConsole(
                    args[0]
                )
            )
        }

        register("marker <name> [x] [y]", "Adds a marker with <name> at x, y, or your current position if x and y are not specified.") { args, player ->
            if (args.isEmpty()) return@register
            val x = if (args.size == 3) args[1].toIntOrNull() ?: player.tileX() else player.tileX()
            val y = if (args.size == 3) args[2].toIntOrNull() ?: player.tileY() else player.tileY()
            val color = Color.HSVtoRGB(Random.nextFloat() * 360, 75f, 75f)
            Markers.add(Markers.Marker(x, y, args[0], color))
        }
    }

    /** Registers a command.
     *
     * @param format The format of the command, basically name and parameters together. Example:
     *      "help <page>"
     * @param description The description of the command.
     * @param runner A lambda to run when the command is invoked.
     */
    fun register(format: String, description: String = "", runner: (args: Array<String>, player: Player) -> Unit) {
        ClientVars.clientCommandHandler.register(format.substringBefore(' '), format.substringAfter(' '), description, runner)
    }
}
