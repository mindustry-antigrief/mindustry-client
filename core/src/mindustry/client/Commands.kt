package mindustry.client

import arc.*
import arc.graphics.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.CommandHandler.*
import mindustry.*
import mindustry.ai.types.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.core.*
import mindustry.entities.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.input.*
import mindustry.logic.*
import mindustry.world.blocks.logic.*
import mindustry.world.blocks.power.*
import java.io.*
import java.math.*
import java.security.cert.*
import kotlin.math.*
import kotlin.random.*

fun setup() {
    register("help [page]", Core.bundle.get("client.command.help.description")) { args, player ->
        if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
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
        val result = buildString {
            append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", page + 1, pages))
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(ClientVars.clientCommandHandler.commandList.size)) {
                val command = ClientVars.clientCommandHandler.commandList[i]
                append("[orange] !").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n")
            }
        }
        player.sendMessage(result)
    }

    register("unit <unit-type>", Core.bundle.get("client.command.unit.description")) { args, _ ->
        Vars.ui.unitPicker.pickUnit(Vars.content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) })
    }

    register("count <unit-type>", Core.bundle.get("client.command.count.description")) { args, player ->
        val type = Vars.content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) }
        val cap = Units.getStringCap(player.team()); var total = 0; var free = 0; var flagged = 0; var unflagged = 0; var players = 0; var command = 0; var logic = 0; var freeFlagged = 0; var logicFlagged = 0

        (player.team().data().unitCache(type) ?: Seq.with()).withEach {
            total++
            val ctrl = sense(LAccess.controlled).toInt()
            if (flag == 0.0) unflagged++
            else {
                flagged++
                if (ctrl == 0) freeFlagged++
            }
            when (ctrl) {
                GlobalVars.ctrlPlayer -> players++
                GlobalVars.ctrlCommand -> command++
                GlobalVars.ctrlProcessor -> {
                    if (flag != 0.0) logicFlagged++
                    logic++
                }
                else -> free++
            }
        }

        player.sendMessage("""
            [accent]${type.localizedName}:
            Total(Cap): $total($cap)
            Free(Free Flagged): $free($freeFlagged)
            Flagged(Unflagged): $flagged($unflagged)
            Players(Command): $players($command)
            Logic(Logic Flagged): $logic($logicFlagged)
            """.trimIndent())
    }

    // FINISHME: Add unit control/select command(s)

    register("spawn <type> [team] [x] [y] [count]", Core.bundle.get("client.command.spawn.description")) { args, player ->
        val type = Vars.content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) }
        val team = if (args.size < 2) player.team() else if (args[1].toIntOrNull() in 0 until 255) Team.all[args[1].toInt()] else Team.all.minBy { t -> if (t.name == null) Float.MAX_VALUE else BiasedLevenshtein.biasedLevenshteinInsensitive(args[1], t.name) }
        val x = if (args.size < 3 || !Strings.canParsePositiveFloat(args[2])) player.x else args[2].toFloat() * Vars.tilesizeF
        val y = if (args.size < 4 || !Strings.canParsePositiveFloat(args[3])) player.y else args[3].toFloat() * Vars.tilesizeF
        val count = if (args.size < 5 || !Strings.canParsePositiveInt(args[4])) 1 else args[4].toInt()

        if (Vars.net.client()) Call.sendChatMessage("/js for(let i = 0; i < $count; i++) UnitTypes.$type.spawn(Team.all[${team.id}], $x, $y)")
        else repeat(count) {
            type.spawn(team, x, y)
        }
    }

    register("go [x] [y]", Core.bundle.get("client.command.go.description")) { args, player ->
        try {
            if (args.size == 2) ClientVars.lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            else throw IOException()
            Navigation.navigateTo(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", ClientVars.clientCommandHandler.prefix + "go"))
        }
    }

    register("lookat [x] [y]", Core.bundle.get("client.command.lookat.description")) { args, player ->
        try {
            (Vars.control.input as? DesktopInput)?.panning = true
            if (args.size == 2) ClientVars.lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            Spectate.spectate(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", ClientVars.clientCommandHandler.prefix + "lookat"))
        }
    }

    register("tp [x] [y]", Core.bundle.get("client.command.tp.description")) { args, player ->
        try {
            if (args.size == 2) ClientVars.lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            NetClient.setPosition(
                ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()).x, ClientVars.lastSentPos.cpy().scl(
                    Vars.tilesize.toFloat()).y)
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", ClientVars.clientCommandHandler.prefix + "tp"))
        }
    }

    register("here [message...]", Core.bundle.get("client.command.here.description")) { args, player ->
        sendMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", player.tileX(), player.tileY()))
    }

    register("cursor [message...]", Core.bundle.get("client.command.cursor.description")) { args, _ ->
        sendMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", Vars.control.input.rawTileX(), Vars.control.input.rawTileY()))
    }

    register("builder [options...]", Core.bundle.get("client.command.builder.description")) { args, _: Player ->
        Navigation.follow(BuildPath(if (args.isEmpty()) "" else args[0]))
    } // FINISHME: This is so scuffed lol

    register("miner [options...]", Core.bundle.get("client.command.miner.description")) { args, _: Player ->
        Navigation.follow(MinePath(if (args.isEmpty()) "" else args[0]))
    } // FINISHME: This is so scuffed lol

    register(" [message...]", Core.bundle.get("client.command.!.description")) { args, _ ->
        sendMessage("!" + if (args.size == 1) args[0] else "")
    }

    register("shrug [message...]", Core.bundle.get("client.command.shrug.description")) { args, _ ->
        sendMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "")
    }

    register("login [name] [pw]", Core.bundle.get("client.command.login.description")) { args, _ ->
        if (args.size == 2) Core.settings.put("cnpw", "${args[0]} ${args[1]}")
        else sendMessage("/login ${Core.settings.getString("cnpw", "")}")
    }

    register("marker <name> [x] [y]", Core.bundle.get("client.command.marker.description")) { args, player ->
        val x = if (args.size == 3) args[1].toIntOrNull() ?: player.tileX() else player.tileX()
        val y = if (args.size == 3) args[2].toIntOrNull() ?: player.tileY() else player.tileY()
        val color = Color.HSVtoRGB(Random.nextFloat() * 360, 75f, 75f)
        Markers.add(Markers.Marker(x, y, args[0], color))
        player.sendMessage(Core.bundle.format("client.command.marker.added", x, y))
    }

    register("js <code...>", Core.bundle.get("client.command.js.description")) { args, player: Player ->
        player.sendMessage("[accent]${Vars.mods.scripts.runConsole(args[0])}")
    }

    // Removed as the dependency was like 50MB. If i ever add this back, it will probably just download the jar when needed and then cache it between client builds so that each update isn't massive.
//        val kts by lazy { ScriptEngineManager().getEngineByExtension("kts") }
//        register("kts <code...>", Core.bundle.get("client.command.kts.description")) { args, player: Player -> // FINISHME: Bundle
//            player.sendMessage("[accent]${try{ kts.eval(args[0]) }catch(e: ScriptException){ e.message }}")
//        }

    register("/js <code...>", Core.bundle.get("client.command.serverjs.description")) { args, player ->
        player.sendMessage("[accent]${Vars.mods.scripts.runConsole(args[0])}")
        sendMessage("/js ${args[0]}")
    }

    register("networking", Core.bundle.get("client.command.networking.description")) { _, player ->
        player.sendMessage(
            if (ClientVars.pluginVersion != -1) "[accent]Using plugin communication" else // FINISHME: Bundle
                BlockCommunicationSystem.findProcessor()?.run { "[accent]Using a logic block at (${tileX()}, ${tileY()})" } ?: // FINISHME: Bundle
                BlockCommunicationSystem.findMessage()?.run { "[accent]Using a message block at (${tileX()}, ${tileY()})" } ?: // FINISHME: Bundle
                "[accent]Using buildplan-based networking (slow, recommended to use a processor for buildplan dispatching)" // FINISHME: Bundle
        )
    }

    register("fixpower [c]", Core.bundle.get("client.command.fixpower.description")) { args, player ->
        val diodeLinks = PowerDiode.connections(player.team()) // Must be run on the main thread
        val grids = PowerInfo.graphs.select { it.team == player.team() }.associate { it.id to it.all.copy() }
        val confirmed = args.any() && args[0] == "c" // Don't configure by default
        val inProgress = !ClientVars.configs.isEmpty()
        var n = 0
        val newLinks = IntMap<IntSet>()
        val tmp = mutableListOf<ConfigRequest>()
        for ((grid, buildings) in grids) { // This is horrible but works somehow
            for (nodeBuild in buildings) {
                val nodeBlock = nodeBuild.block as? PowerNode ?: continue
                var links = nodeBuild.power.links.size
                nodeBlock.getPotentialLinks(nodeBuild.tile, player.team()) { link ->
                    val min = min(grid, link.power.graph.id)
                    val max = max(grid, link.power.graph.id)
                    if (diodeLinks.any { it[0] == min && it[1] == max }) return@getPotentialLinks // Don't connect across diodes
                    if (++links > nodeBlock.maxNodes) return@getPotentialLinks // Respect max links
                    val t = newLinks.get(grid) { IntSet.with(grid) }
                    val l = newLinks.get(link.power.graph.id, IntSet())
                    if (l.add(grid) && t.add(link.power.graph.id)) {
                        l.addAll(t)
                        newLinks.put(link.power.graph.id, l)
                        if (confirmed && !inProgress) tmp.add(ConfigRequest(nodeBuild.tileX(), nodeBuild.tileY(), link.pos()))
                        n++
                    }
                }
            }
        }
        ClientVars.configs.addAll(tmp)
        @Suppress("CAST_NEVER_SUCCEEDS") val msg = Vars.ui.chatfrag.addMessage("", null as? Color)
        msg.message = when {
            confirmed && inProgress -> Core.bundle.format("client.command.fixpower.inprogress", ClientVars.configs.size, n)
            confirmed -> { // Actually fix the connections
                ClientVars.configs.add { // This runs after the connections are made
                    msg.message = Core.bundle.format("client.command.fixpower.success", n, PowerInfo.graphs.select { it.team == player.team() }.size)
                    msg.format()
                }
                Core.bundle.format("client.command.fixpower.confirmed", n)
            }
            else -> Core.bundle.format("client.command.fixpower.confirm", n, grids.size)
        }
        msg.format()
    }

    @Suppress("unchecked_cast")
    register("fixcode [c]", "Fixes problematic \"attem >= 83\" flagging logic") { args, player -> // FINISHME: Bundle
        val builds = Vars.player.team().data().buildings.filterIsInstance<LogicBlock.LogicBuild>() // Must be done on the main thread
        clientThread.post {
            val confirmed = args.any() && args[0] == "c" // Don't configure by default
            val inProgress = !ClientVars.configs.isEmpty()
            var n = 0

            if (confirmed && !inProgress) {
                Log.debug("Patching!")
                builds.forEach {
                    val patched = ProcessorPatcher.patch(it.code)
                    if (patched != it.code) {
                        Log.debug("${it.tileX()} ${it.tileY()}")
                        ClientVars.configs.add(ConfigRequest(it.tileX(), it.tileY(), LogicBlock.compress(patched, it.relativeConnections())))
                        n++
                    }
                }
            }
            Core.app.post {
                if (confirmed) {
                    if (inProgress) player.sendMessage("[scarlet]The config queue isn't empty, there are ${ClientVars.configs.size} configs queued, there are ${ProcessorPatcher.countProcessors(builds)} processors to reconfigure.") // FINISHME: Bundle
                    else player.sendMessage("[accent]Successfully reconfigured $n/${builds.size} processors")
                } else {
                    player.sendMessage("[accent]Run [coral]!fixcode c[] to reconfigure ${ProcessorPatcher.countProcessors(builds)}/${builds.size} processors")
                }
            }
        }
    }

    register("distance [distance]", "Sets the assist distance multiplier distance (default is 1.5)") { args, player -> // FINISHME: Bundle
        if (args.size != 1) player.sendMessage("[accent]The distance multiplier is ${Core.settings.getFloat("assistdistance", 1.5f)} (default is 1.5)")
        else {
            Core.settings.put("assistdistance", abs(Strings.parseFloat(args[0], 1.5f)))
            player.sendMessage("[accent]The distance multiplier is now ${Core.settings.getFloat("assistdistance")} (default is 1.5)")
        }
    }

    register("admin [option]", "Access moderation commands and settings") { args, player -> // FINISHME: Bundle
        val arg = if (args.isEmpty()) "" else args[0]
        when (arg.lowercase()) {
            "s", "settings" -> {
                Vars.ui.settings.show()
                Vars.ui.settings.visible(4)
            }
            "l", "leaves" -> Client.leaves?.leftList() ?: player.sendMessage("[scarlet]Leave logs are disabled")
            else -> player.sendMessage("[scarlet]Invalid option specified, options are:\nSettings, Leaves")
        }
    }

    register("clearghosts [c]", "Removes the ghosts of blocks which are in range of enemy turrets, useful to stop polys from building forever") { args, player -> // FINISHME: Bundle
        val confirmed = args.any() && args[0].startsWith("c") // Don't clear by default
        val all = confirmed && Main.keyStorage.builtInCerts.contains(Main.keyStorage.cert()) && args[0] == "clear"
        val plans = mutableListOf<Int>()

        for (plan in Vars.player.team().data().plans) {
            val block = Vars.content.block(plan.block.toInt())
            if (!(all || Navigation.getTree().any(plan.x * Vars.tilesizeF, plan.y * Vars.tilesizeF, block.size * Vars.tilesizeF, block.size * Vars.tilesizeF))) continue

            plans.add(Point2.pack(plan.x.toInt(), plan.y.toInt()))
        }

        if (confirmed) {
            plans.chunked(100) { Call.deletePlans(player, it.toIntArray()) }
            player.sendMessage("[accent]Removed ${plans.size} plans, ${Vars.player.team().data().plans.size} remain")
        } else player.sendMessage("[accent]Found ${plans.size} (out of ${Vars.player.team().data().plans.size}) block ghosts within turret range, run [coral]!clearghosts c[] to remove them")
    }

    register("e <certname> <message...>", "Sends an encrypted message over TLS.") { args, _ -> // FINISHME: Bundle
        val certname = args[0]
        val msg = args[1]

        connectTls(certname) { comms, cert ->
            comms.send(MessageTransmission(msg))
            Vars.ui.chatfrag.addMessage(
                msg,
                "[coral]${Main.keyStorage.cert()?.readableName ?: "you"} [white]-> ${Main.keyStorage.aliasOrName(cert)}",
                ClientVars.encrypted
            )
            ClientVars.lastCertName = cert.readableName
        }
    }

    register("stoppathing <name/id...>", "Stop someone from pathfinding.") { args, _ -> // FINISHME: Bundle
        val name = args.joinToString(" ")
        val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { Strings.levenshtein(
            Strings.stripColors(it.name), name) }!!
        Main.send(CommandTransmission(CommandTransmission.Commands.STOP_PATH, Main.keyStorage.cert() ?: return@register, player))
        // FINISHME: success message
    }

    register("c <message...>", "Send a message to other client users.") { args, _ ->  // FINISHME: Bundle
        Main.send(ClientMessageTransmission(args[0]).apply { addToChatfrag() })
    }

    register("mapinfo", "Lists various useful map info.") { _, player -> // FINISHME: Bundle
        player.sendMessage(with(Vars.state) {
            """
            [accent]Name: ${map.name()}[accent] (by: ${map.author()}[accent])
            Map Time: ${UI.formatTime(tick.toFloat())}
            Build Speed (Unit Factories): ${rules.buildSpeedMultiplier}x (${rules.unitBuildSpeedMultiplier}x)
            Build Cost (Refund): ${rules.buildCostMultiplier}x (${rules.deconstructRefundMultiplier}x)
            Core Capture: ${rules.coreCapture}
            Core Incinerates: ${rules.coreIncinerates}
            Core Modifies Unit Cap: ${rules.unitCapVariable}
            """.trimIndent()
        })
    }

    register("binds <type>", "") { args, player -> // FINISHME: Bundle
        val type = Vars.content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) }

        player.team().data().unitCache(type)
            ?.filter { it.controller() is LogicAI }
            ?.groupBy { (it.controller() as LogicAI).controller }
            ?.forEach { (build, units) ->
                player.sendMessage("x${units.size} [accent](${build.tileX()}, ${build.tileY()})")
            }
    }
}

/** Registers a command.
 *
 * @param format The format of the command, basically name and parameters together. Example:
 *      "help [page]"
 * @param description The description of the command.
 * @param runner A lambda to run when the command is invoked.
 */
fun register(format: String, description: String = "", runner: CommandRunner<Player>) {
    val args = if (format.contains(' ')) format.substringAfter(' ') else ""
    ClientVars.clientCommandHandler.register(format.substringBefore(' '), args, description, runner)
}

private fun connectTls(certname: String, onFinish: (Packets.CommunicationClient, X509Certificate) -> Unit) { // FINISHME: Bundle
    val cert = Main.keyStorage.aliases().firstOrNull { it.second.equals(certname, true) }
        ?.run { Main.keyStorage.findTrusted(BigInteger(first)) }
        ?: Main.keyStorage.trusted().firstOrNull { it.readableName.equals(certname, true) }

    cert ?: run {
        Vars.player.sendMessage("[scarlet]Couldn't find a certificate called or aliased to '$certname'")
        return
    }

    if (cert == Main.keyStorage.cert()) {
        Vars.player.sendMessage("[scarlet]Can't establish a connection to yourself")
        return
    }

    val preexistingConnection = Main.tlsPeers.firstOrNull { it.second.peer.expectedCert.encoded.contentEquals(cert.encoded) }

    if (preexistingConnection != null) {
        if (preexistingConnection.second.peer.handshakeDone) {
            onFinish(preexistingConnection.first, cert)
        } else {
            Vars.player.sendMessage("[scarlet]Handshake is not completed!")
        }
    } else {
        Vars.player.sendMessage("[accent]Sending TLS request...")
        Main.connectTls(cert, {
            Vars.player.sendMessage("[accent]Connected!")
            // delayed to make sure receiving end is ready
            Timer.schedule({ onFinish(it, cert) }, .1F)
        }, { Vars.player.sendMessage("[scarlet]Make sure a processor/message block is set up for communication!") })
    }
}