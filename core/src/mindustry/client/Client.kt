package mindustry.client

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.math.Mathf
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import mindustry.Vars
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.Main.setPluginNetworking
import mindustry.client.Spectate.spectate
import mindustry.client.antigrief.PowerInfo
import mindustry.client.navigation.BuildPath
import mindustry.client.navigation.Markers
import mindustry.client.navigation.Navigation
import mindustry.client.ui.ChangelogDialog
import mindustry.client.ui.Toast
import mindustry.client.utils.*
import mindustry.content.Blocks
import mindustry.core.NetClient
import mindustry.core.World
import mindustry.entities.Units
import mindustry.entities.units.UnitCommand
import mindustry.game.EventType
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
            PowerInfo.initialize()
            Navigation.stopFollowing()
            Navigation.obstacles.clear()
            configs.clear()
            ui.unitPicker.found = null
            control.input.lastVirusWarning = null
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            showingTurrets = false
            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
        }

        Events.on(ClientLoadEvent::class.java) {
            val changeHash = Core.files.internal("changelog").readString()
                .hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)
            if (Core.settings.getBool("debug")) Log.level =
                Log.LogLevel.debug // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) platform.startDiscord()

            Autocomplete.autocompleters.add(BlockEmotes())
            Autocomplete.autocompleters.add(PlayerCompletion())
            Autocomplete.autocompleters.add(CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()
        }

        Events.on(EventType.PlayerJoin::class.java) { e ->
            if (e.player == null) return@on

            val message = "[accent]${e.player.name}[accent] has connected."
            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !ui.chatfrag.messages.first().message.equals(
                    message
                )) && Time.timeSinceMillis(lastJoinTime) > 10000
            ) player.sendMessage(message)
        }

        Events.on(EventType.PlayerLeave::class.java) { e ->
            if (e.player == null) return@on

            val message = "[accent]${e.player.name}[accent] has disconnected."
            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !ui.chatfrag.messages.first().message.equals(
                    message
                ))
            ) player.sendMessage(message)
        }
    }

    fun update() {
        Navigation.update()
        PowerInfo.update()
        Spectate.update()
        if (!configs.isEmpty) {
            try {
                if (configRateLimit.allow(
                        Administration.Config.interactRateWindow.num() * 1000L,
                        Administration.Config.interactRateLimit.num()
                    )
                ) {
                    val req = configs.removeLast()
                    val tile = world.tile(req.x, req.y)
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
        register("help [page]", "Lists all client commands.") { args, player ->
            if (args.isNotEmpty() && !Strings.canParseInt(
                    args[0]
                )
            ) {
                player.sendMessage("[scarlet]'page' must be a number.")
                return@register
            }
            val commandsPerPage = 6
            var page = if (args.isNotEmpty()) Strings.parseInt(args[0]) else 1
            val pages = Mathf.ceil(clientCommandHandler.commandList.size.toFloat() / commandsPerPage)
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
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(clientCommandHandler.commandList.size)) {
                val command = clientCommandHandler.commandList[i]
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText)
                    .append("[lightgray] - ").append(command.description).append("\n")
            }
            player.sendMessage(result.toString())
        }

        register("unit <unit-name>", "Swap to specified unit") { args, _: Player ->
            ui.unitPicker.findUnit(content.units().copy().sort { b: UnitType ->
                BiasedLevenshtein.biasedLevenshtein(
                    args[0], b.name
                )
            }.first())
        }

        register("go [x] [y]", "Navigates to (x, y) or the last coordinates posted to chat") { args, player: Player ->
            try {
                if (args.size == 2) lastSentPos[args[0].toFloat()] = args[1].toFloat()
                Navigation.navigateTo(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            }
        }

        register("lookat [x] [y]", "Moves camera to (x, y) or the last coordinates posted to chat") { args, player: Player ->
            try {
                DesktopInput.panning = true
                if (args.size == 2) lastSentPos[args[0].toFloat()] = args[1].toFloat()
                spectate(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            }
        }

        register("here [message...]", "Prints your location to chat with an optional message") { args, player: Player ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.isEmpty()) "" else args[0] + " ",
                    player.tileX(),
                    player.tileY()
                )
            )
        }

        register("cursor [message...]", "Prints cursor location to chat with an optional message") { args, _: Player ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.isEmpty()) "" else args[0] + " ",
                    control.input.rawTileX(),
                    control.input.rawTileY()
                )
            )
        }

        register("builder [options...]", "Starts auto build with optional arguments, prioritized from first to last.") { args, _: Player ->
            Navigation.follow(BuildPath(if (args.isEmpty()) "" else args[0]))
        } // TODO: This is so scuffed lol

        register("tp <x> <y>", "Teleports to (x, y), only works on servers without strict mode enabled.") { args, player ->
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
        clientCommandHandler.register(
            "", "[message...]", "Lets you start messages with an !"
        ) { args, _: Player -> Call.sendChatMessage("!" + if (args.size == 1) args[0] else "") }

        register("shrug [message...]", "Sends the shrug unicode emoji with an optional message") { args, _ ->
            Call.sendChatMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "")
        }

        register("login [name] [pw]", "Used for CN. [scarlet]Don't use this if you care at all about security.") { args, _ ->
            if (args.size == 2) Core.settings.put(
                "cnpw",
                args[0] + " " + args[1]
            ) else Call.sendChatMessage("/login " + Core.settings.getString("cnpw", ""))
        }

        register("js <code...>", "Runs JS on the client.") { args, player: Player ->
            player.sendMessage(
                "[accent]" + mods.scripts.runConsole(
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

        register("/js <code...>", "Runs JS on the client as well as the server.") { args, player ->
            player.sendMessage("[accent]" + mods.scripts.runConsole(args[0]))
            Call.sendChatMessage("/js " + args[0])
        }

        register("cc [setting]", "Configure your team's command center easily.") { args, player ->
            if (args.size != 1 || !args[0].matches("(?i)^[ari].*".toRegex())) {
                player.sendMessage("[scarlet]Invalid setting specified, valid options are: Attack (a), Rally (r), Idle (i)")
                return@register
            }
            for (tile in world.tiles) {
                if (tile?.build == null || tile.build.team != player.team() || tile.block() != Blocks.commandCenter) continue
                Call.tileConfig(player, tile.build, when (args[0].toLowerCase()[0]) {
                    'a' -> UnitCommand.attack
                    'r' -> UnitCommand.rally
                    else -> UnitCommand.idle
                })
                Toast(3f).add("Successfully set the command center to " + args[0] + ".")
                return@register
            }
            Toast(3f).add("No command center was found on your team, one is required for this to work.")
        }

        register("count <unit-type>", "Counts how many of a certain unit are alive.") { args, player ->
            val unit = content.units().copy().sort { b ->
                BiasedLevenshtein.biasedLevenshtein(args[0], b.name)
            }.first()

            player.sendMessage(Strings.format(
                    "[accent]@: @/@",
                    unit.localizedName,
                    player.team().data().countType(unit),
                    Units.getCap(player.team())
                )
            ) // TODO: Make this check each unit to see if it is a player/formation unit, display that info
        }

        register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
            Call.sendChatMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
        }

        register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
            Call.sendChatMessage(
                "\"In short, silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance.\" They are not the same, please get it right!"
            )
        }

        register("togglesign", "Toggles the signing of messages on and off (whether or not your messages can be green for other players)") { _, _ ->
            signMessages = !signMessages
            player.sendMessage("Successfully toggled signing of messages " + if (signMessages) "on" else "off")
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
        val args = if (format.contains(' ')) format.substringAfter(' ') else ""
        clientCommandHandler.register(format.substringBefore(' '), args, description, runner)
    }
}
