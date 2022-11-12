package mindustry.client

import arc.Core
import arc.func.Prov
import arc.graphics.Color
import arc.math.Mathf
import arc.math.geom.Point2
import arc.struct.*
import arc.util.*
import arc.util.CommandHandler.CommandRunner
import mindustry.Vars
import mindustry.Vars.*
import mindustry.ai.types.LogicAI
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.follow
import mindustry.client.navigation.Navigation.navigator
import mindustry.client.ui.SeerDialog
import mindustry.client.utils.*
import mindustry.content.Blocks
import mindustry.core.NetClient
import mindustry.core.UI
import mindustry.entities.Units
import mindustry.entities.units.BuildPlan
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.input.DesktopInput
import mindustry.logic.GlobalVars
import mindustry.logic.LAccess
import mindustry.net.Host
import mindustry.world.Block
import mindustry.world.blocks.distribution.DirectionalUnloader
import mindustry.world.blocks.distribution.DirectionalUnloader.DirectionalUnloaderBuild
import mindustry.world.blocks.environment.Prop
import mindustry.world.blocks.logic.MessageBlock
import mindustry.world.blocks.power.PowerDiode
import mindustry.world.blocks.power.PowerNode
import mindustry.world.blocks.sandbox.PowerVoid
import mindustry.world.blocks.storage.StorageBlock
import mindustry.world.blocks.storage.Unloader.UnloaderBuild
import java.io.IOException
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.*
import kotlin.random.Random


fun setup() {
    register("help [page/command]", Core.bundle.get("client.command.help.description")) { args, player ->
        if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
            val command = ClientVars.clientCommandHandler.commandList.find { it.text == args[0] }
            if (command != null) {
                player.sendMessage(Strings.format("[orange] !@[white] @[lightgray] - @", command.text, command.paramText, command.description))
                return@register
            }
            player.sendMessage("[scarlet]input must be a number or command.")
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
        val result = buildString {
            append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", page + 1, pages))
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(clientCommandHandler.commandList.size)) {
                val command = clientCommandHandler.commandList[i]
                append("[orange] !").append(command.text).append("[white] ").append(command.paramText).append("[lightgray] - ").append(command.description).append("\n")
            }
        }
        player.sendMessage(result)
    }

    register("unit-old <unit-type>", Core.bundle.get("client.command.unit.description")) { args, _ ->
        ui.unitPicker.pickUnit(content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.name) })
    }

    register("unit <unit-type>", Core.bundle.get("client.command.unit.description")) { args, _ ->
        ui.unitPicker.pickUnit(findUnit(args[0]))
    }

    register("uc <unit-type>", Core.bundle.get("client.command.unitcursor.description")) { args, _ ->
        ui.unitPicker.pickUnit(findUnit(args[0]), Core.input.mouseWorldX(), Core.input.mouseWorldY(), true)
    }

    register("count <unit-type>", Core.bundle.get("client.command.count.description")) { args, player ->
        val type = findUnit(args[0])
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

        player.sendMessage(
            """
            [accent]${type.localizedName}:
            Total(Cap): $total($cap)
            Free(Free Flagged): $free($freeFlagged)
            Flagged(Unflagged): $flagged($unflagged)
            Players(Command): $players($command)
            Logic(Logic Flagged): $logic($logicFlagged)
            """.trimIndent()
        )
    }

    // FINISHME: Add unit control/select command(s)

    register("spawn <type> [team] [x] [y] [count]", Core.bundle.get("client.command.spawn.description")) { args, player ->
        val type = findUnit(args[0])
        val team = if (args.size < 2) player.team() else findTeam(args[1])
        val x = if (args.size < 3 || !Strings.canParsePositiveFloat(args[2])) player.x else args[2].toFloat() * Vars.tilesizeF
        val y = if (args.size < 4 || !Strings.canParsePositiveFloat(args[3])) player.y else args[3].toFloat() * Vars.tilesizeF
        val count = if (args.size < 5 || !Strings.canParsePositiveInt(args[4])) 1 else args[4].toInt()

        if (net.client()) Call.sendChatMessage("/js for(let i = 0; i < $count; i++) UnitTypes.$type.spawn(Team.all[${team.id}], $x, $y)")
        else repeat(count) {
            type.spawn(team, x, y)
        }
    }

    register("go [x] [y]", Core.bundle.get("client.command.go.description")) { args, player ->
        try {
            if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            else throw IOException()
            Navigation.navigateTo(lastSentPos.cpy().scl(tilesize.toFloat()))
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "go"))
        }
    }

    register("lookat [x] [y]", Core.bundle.get("client.command.lookat.description")) { args, player ->
        try {
            (control.input as? DesktopInput)?.panning = true
            if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            Spectate.spectate(lastSentPos.cpy().scl(tilesize.toFloat()))
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "lookat"))
        }
    }

    register("tp [x] [y]", Core.bundle.get("client.command.tp.description")) { args, player ->
        try {
            if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
            NetClient.setPosition(
                lastSentPos.cpy().scl(tilesize.toFloat()).x, lastSentPos.cpy().scl(
                    tilesize.toFloat()).y)
        } catch (e: Exception) {
            player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "tp"))
        }
    }

    register("here [message...]", Core.bundle.get("client.command.here.description")) { args, player ->
        sendMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", player.tileX(), player.tileY()))
    }

    register("cursor [message...]", Core.bundle.get("client.command.cursor.description")) { args, _ ->
        sendMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", control.input.rawTileX(), control.input.rawTileY()))
    }

    register("builder [options...]", Core.bundle.get("client.command.builder.description")) { args, _: Player ->
        follow(BuildPath(if (args.isEmpty()) "" else args[0]))
    } // FINISHME: This is so scuffed lol

    register("miner [options...]", Core.bundle.get("client.command.miner.description")) { arguments, _: Player ->
        follow(MinePath(args = if (arguments.isEmpty()) "" else arguments[0]))
    } // FINISHME: This is so scuffed lol

    register("buildmine", Core.bundle.get("client.command.buildmine.description")) {_, _ -> // FINISHME: Hrnghrng properly word the bundle
        follow(BuildMinePath())
    }

    register("buildmine", Core.bundle.get("client.command.buildmine.description")) {_, _: Player ->
        follow(BuildMinePath())
    }

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
        player.sendMessage("[accent]${mods.scripts.runConsole(args[0])}")
    }

    // Removed as the dependency was like 50MB. If i ever add this back, it will probably just download the jar when needed and then cache it between client builds so that each update isn't massive.
//        val kts by lazy { ScriptEngineManager().getEngineByExtension("kts") }
//        register("kts <code...>", Core.bundle.get("client.command.kts.description")) { args, player: Player -> // FINISHME: Bundle
//            player.sendMessage("[accent]${try{ kts.eval(args[0]) }catch(e: ScriptException){ e.message }}")
//        }

    register("/js <code...>", Core.bundle.get("client.command.serverjs.description")) { args, player ->
        player.sendMessage("[accent]${mods.scripts.runConsole(args[0])}")
        sendMessage("/js ${args[0]}")
    }

    // Removed in Erekir
//    register("cc [setting]", Core.bundle.get("client.command.cc.description")) { args, player ->
//        if (args.size != 1 || !args[0].matches("(?i)^[ari].*".toRegex())) {
//            player.sendMessage(Core.bundle.format("client.command.cc.invalid", player.team().data().command.localized()))
//            return@register
//        }
//
//        val cc = Units.findAllyTile(player.team(), player.x, player.y, Float.MAX_VALUE / 2) { it is CommandCenter.CommandBuild }
//        if (cc != null) {
//            Call.tileConfig(player, cc, when (args[0].lowercase()[0]) {
//                'a' -> UnitCommand.attack
//                'r' -> UnitCommand.rally
//                else -> UnitCommand.idle
//            })
//            player.sendMessage(Core.bundle.format("client.command.cc.success", args[0]))
//        } else player.sendMessage(Core.bundle.get("client.command.cc.notfound"))
//    }

    register("networking", Core.bundle.get("client.command.networking.description")) { _, player ->
        player.sendMessage(
            if (pluginVersion != -1) "[accent]Using plugin communication" else // FINISHME: Bundle
                BlockCommunicationSystem.findProcessor()?.run { "[accent]Using a logic block at (${tileX()}, ${tileY()})" } ?: // FINISHME: Bundle
                BlockCommunicationSystem.findMessage()?.run { "[accent]Using a message block at (${tileX()}, ${tileY()})" } ?: // FINISHME: Bundle
                "[accent]Using buildplan-based networking (slow, recommended to use a processor for buildplan dispatching)" // FINISHME: Bundle
        )
    }

    register("fixpower [c]", Core.bundle.get("client.command.fixpower.description")) { args, player ->
        val diodeLinks = PowerDiode.connections(player.team()) // Must be run on the main thread
        val grids = PowerInfo.graphs.select { it.team == player.team() }.associate { it.id to it.all.copy() }
        val confirmed = args.any() && args[0] == "c" // Don't configure by default
        val inProgress = !configs.isEmpty()
        var n = 0
        val newLinks = IntMap<IntSet>()
        for ((grid, buildings) in grids) { // This is horrible but works somehow FINISHME: rewrite this to work in realtime so that its not cursed
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
                        if (confirmed && !inProgress) configs.add(ConfigRequest(nodeBuild.tileX(), nodeBuild.tileY(), link.pos()))
                        n++
                    }
                }
            }
        }

        val msg = ui.chatfrag.addMsg("")

        msg.message = when {
            confirmed && inProgress -> Core.bundle.format("client.command.fixpower.inprogress", configs.size, n)
            confirmed -> { // Actually fix the connections
                configs.add { // This runs after the connections are made
                    msg.message = Core.bundle.format("client.command.fixpower.success", n, PowerInfo.graphs.select { it.team == player.team() }.size)
                    msg.format()
                }
                Core.bundle.format("client.command.fixpower.confirmed", n)
            }
            else -> Core.bundle.format("client.command.fixpower.confirm", n, grids.size)
        }
        msg.format()
    }

    register("fixcode [c/r/l]", Core.bundle.get("client.command.fixcode.description")) { args, player ->
        ProcessorPatcher.fixCode(args[0])
    }

    register("distance [distance]", Core.bundle.get("client.command.distance.description")) { args, player ->
        if (args.size != 1) player.sendMessage("[accent]The distance multiplier is ${Core.settings.getFloat("assistdistance", 1.5f)} (default is 1.5)")
        else {
            Core.settings.put("assistdistance", abs(Strings.parseFloat(args[0], 1.5f)))
            player.sendMessage("[accent]The distance multiplier is now ${Core.settings.getFloat("assistdistance")} (default is 1.5)")
        }
    }

    register("circleassist [speed]", Core.bundle.get("client.command.circleassist.description")) { args, player ->
        if (args.size != 1) player.sendMessage("[accent]The circle assist speed is ${Core.settings.getFloat("circleassistspeed", 0.05f)} (default is 0.05)")
        else {
            if(args[0] == "0"){
                Core.settings.put("circleassist", false);
                if(Navigation.currentlyFollowing is AssistPath) (Navigation.currentlyFollowing as AssistPath).circling = false;
            } else {
                Core.settings.put("circleassist", true);
                if(Navigation.currentlyFollowing is AssistPath) (Navigation.currentlyFollowing as AssistPath).circling = true;
                Core.settings.put("circleassistspeed", Strings.parseFloat(args[0], 0.05f));
            }
            player.sendMessage(Core.bundle.format("client.command.circleassist.success", Core.settings.getFloat("circleassistspeed")))
        }
    }

    register("clearghosts [c]", Core.bundle.get("client.command.clearghosts.description")) { args, player -> 
        val confirmed = args.any() && args[0].startsWith("c") // Don't clear by default
        val all = confirmed && Main.keyStorage.builtInCerts.contains(Main.keyStorage.cert()) && args[0] == "clear"
        val plans = mutableListOf<Int>()

        for (plan in player.team().data().plans) {
            val block = content.block(plan.block.toInt())
            if (!(all || Navigation.getTree().any(plan.x * tilesizeF, plan.y * tilesizeF, block.size * tilesizeF, block.size * tilesizeF))) continue

            plans.add(Point2.pack(plan.x.toInt(), plan.y.toInt()))
        }

        if (confirmed) {
            plans.chunked(100) { Call.deletePlans(player, it.toIntArray()) }
            player.sendMessage("[accent]Removed ${plans.size} plans, ${player.team().data().plans.size} remain")
        } else player.sendMessage("[accent]Found ${plans.size} (out of ${player.team().data().plans.size}) block ghosts within turret range, run [coral]!clearghosts c[] to remove them")
    }

    register("e <certname> <message...>", Core.bundle.get("client.command.e.description")) { args, _ ->
        val certname = args[0]
        val msg = args[1]

        connectTls(certname) { comms, cert ->
            comms.send(MessageTransmission(msg))
            ui.chatfrag.addMessage(
                msg,
                "[coral]${Main.keyStorage.cert()?.readableName ?: "you"} [white]-> ${Main.keyStorage.aliasOrName(cert)}",
                encrypted,
                "",
                msg
            )
            lastCertName = cert.readableName
        }
    }

    register("stoppathing <name/id...>", Core.bundle.get("client.command.stoppathing.description")) { args, _ ->
        val name = args.joinToString(" ")
        val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { Strings.levenshtein(
            Strings.stripColors(it.name), name) }!!
        Main.send(CommandTransmission(CommandTransmission.Commands.STOP_PATH, Main.keyStorage.cert() ?: return@register, player))
        // FINISHME: Force stop instead of prompt
        // FINISHME: success message
    }

    register("c <message...>", Core.bundle.get("client.command.c.description")) { args, _ -> 
        Main.send(ClientMessageTransmission(args[0]).apply { addToChatfrag() })
    }

    register("mapinfo [team]", Core.bundle.get("client.command.mapinfo.description")) { args, player -> 
        val team = if (args.isEmpty()) player.team() else findTeam(args[0])
        player.sendMessage(with(Vars.state) {
            """
            [accent]Name: ${map.name()}[accent] (by: ${map.author()}[accent])
            Team: ${team.name}
            Map Time: ${UI.formatTime(tick.toFloat())}
            Build Speed (Unit Factories): ${rules.buildSpeed(team)}x (${rules.unitBuildSpeed(team)}x)
            Build Cost (Refund): ${rules.buildCostMultiplier}x (${rules.deconstructRefundMultiplier}x)
            Block Health (Damage): ${rules.blockHealth(team)}x (${rules.blockDamage(team)}x)
            Unit Damage: ${rules.unitDamage(team)}x
            Core Capture: ${rules.coreCapture}
            Core Incinerates: ${rules.coreIncinerates}
            Core Modifies Unit Cap: ${rules.unitCapVariable}
            """.trimIndent()
        })
    }

    register("binds <type>", Core.bundle.get("client.command.binds.description")) { args, player -> 
        val type = findUnit(args[0])

        player.team().data().unitCache(type)
            ?.filter { it.controller() is LogicAI }
            ?.groupBy { (it.controller() as LogicAI).controller }
            ?.forEach { (build, units) ->
                val txt = "x${units.size} [accent](${build.tileX()}, ${build.tileY()})"
                val msg = ui.chatfrag.addMessage(txt, null, null, "", txt)
                NetClient.findCoords(msg)
            }
    }

    register("unloaders <item> [enabledOnly] [setOnly]", Core.bundle.get("client.command.unloaders.description")) { args, player -> 
        val item = findItem(args[0])
        val enabledOnly = args.size < 2 || parseBool(args[1])
        val setOnly = args.size < 3 || parseBool(args[2])

        val linkedCores = ObjectSet<Building>()
        player.team().cores().forEach { core ->  // Add all cores and their adjacent containers/vaults to the list
            linkedCores.add(core)
            core.proximity.forEach {
                if ((it.block as? StorageBlock)?.coreMerge == true) linkedCores.add(it)
            }
        }

        val coords = ObjectSet<Building>()
        for (core in linkedCores) { // Iterate through proximity of all cores & adjacent vaults looking for unloaders of the specified type
            core.proximity.forEach {
                if (it !is UnloaderBuild && !(it is DirectionalUnloaderBuild && (it.block as? DirectionalUnloader)?.allowCoreUnload == true)) return@forEach
                if (!it.enabled && enabledOnly) return@forEach
                if (it.config() != item && (setOnly || it.config() != null)) return@forEach
                if (it is DirectionalUnloaderBuild && !linkedCores.contains(it.back())) return@forEach // Erekir unloaders only unload from the tile behind
                if (!coords.add(it)) return@forEach // We have already printed this tile's coords

                ui.chatfrag.addMsg("[accent](${it.tileX()}, ${it.tileY()})").findCoords()
            }
        }
    }

    register("blank", Core.bundle.get("client.command.blank.description")) { _, _ ->
        sendMessage("\u200B")
    }

    register("replacemessage <from> <to> [useRegex=t]", Core.bundle.get("client.command.replacemessage.description")) { args, player ->
        if (args[0].length < 3) {
            player.sendMessage("[scarlet]That might not be a good idea...")
            return@register
        }
        val useRegex = args.size > 2 && args[2] == "t"
        replaceMsg(args[0], useRegex, args[0], useRegex, args[1])
    }

    register(
        "replacemsgif <matches> <from> <to> [useMatchRegex=t] [useFromRegex=t]",
        Core.bundle.get("client.command.replacemsgif.description")
    ) { args, player ->
        if (args[0].length < 3) {
            player.sendMessage("[scarlet]That might not be a good idea...")
            return@register
        }
        replaceMsg(args[0], args.size > 3 && args[3] == "t", args[1], args.size > 4 && args[4] == "t", args[2])
    }

//    register("phasei <interval>", Core.bundle.get("client.command.phasei.description")) { args, player -> FINISHME: Needs to be reimplemented
//        try{
//            val interval = Integer.parseInt(args[0])
//            val maxInterval = (Blocks.phaseConveyor as ItemBridge).range
//            if(interval < 1 || interval > maxInterval){
//                player.sendMessage("[scarlet]Interval must be within 1 and $maxInterval!")
//                return@register
//            }
//            ItemBridge.phaseWeaveInterval = interval
//            Core.settings.put("weaveEndInterval", interval)
//            player.sendMessage("[accent]Successfully set interval to $interval.")
//        } catch (e : Exception){
//            player.sendMessage("[scarlet]Failed to parse integer!")
//        }
//    }

    register("pathing", Core.bundle.get("client.command.pathing.description")) { _, player ->
        if (navigator is AStarNavigator) {
            navigator = AStarNavigatorOptimised
            player.sendMessage("[accent]Using [green]improved[] algorithm")
        } else if (navigator is AStarNavigatorOptimised) {
            navigator = AStarNavigator
            player.sendMessage("[accent]Using [gray]classic[] algorithm")
        }
    }

    register("pic [quality]", Core.bundle.get("client.command.pic.description")) { args, player ->
        if (args.isEmpty()) {
            player.sendMessage(Core.bundle.format("client.command.pic.invalidargs", jpegQuality, if (jpegQuality == 0f) "png" else ""))
            return@register
        }
        try {
            val quality = args[0].toFloat()
            if (quality !in 0f .. 1f) {
                player.sendMessage(Core.bundle.format("client.command.pic.invalidargs", jpegQuality, if (jpegQuality == 0f) "png" else ""))
                return@register
            }
            jpegQuality = quality
            Core.settings.put("commpicquality", quality)
            player.sendMessage(Core.bundle.format("client.command.pic.success", quality, if (quality == 0f) "png" else ""))
        } catch (e: Exception) {
            Log.err(e)
            if (e is NumberFormatException) player.sendMessage(Core.bundle.format("client.command.pic.invalidargs", jpegQuality, if (jpegQuality == 0f) "png" else ""))
            else player.sendMessage(Core.bundle.get("client.command.pic.error"))
        }
    }

    register("procfind [option] [argument]", Core.bundle.get("client.command.procfind.description")) { args, player ->
        val newArgs = args.joinToString(" ").split(" ").toTypedArray() // FINISHME: fix the command arguments. this is beyond cursed

        when (newArgs[0]) {
            "query" -> {
                if (newArgs.size < 2) {
                    player.sendMessage(Core.bundle.get("client.command.procfind.query.empty"))
                    return@register
                }
                val queryRegex = newArgs.drop(1).joinToString(" ").toRegex()
                ProcessorFinder.search(queryRegex)
            }
            "queries" -> {
                val sb = StringBuilder(Core.bundle.get("client.command.procfind.queries")).append("\n")
                ProcessorFinder.queries.forEach { r -> sb.append("\n").append(r.toPattern().pattern()) }
                player.sendMessage(sb.toString())
            }
            "searchall" -> ProcessorFinder.searchAll()
            "clear" -> {
                player.sendMessage(Core.bundle.format("client.command.procfind.clear", ProcessorFinder.getCount()))
                ProcessorFinder.clear()
            }
            "list" -> ProcessorFinder.list()
            else -> player.sendMessage(Core.bundle.get("client.command.procfind.help")) // This one looks long and cursed on the bundle
        }
    }

    register("voids [count]", Core.bundle.get("client.command.voids.description")) { args, player ->
        var count = 1
        if (args.isNotEmpty()) {
            try {
                count = args[0].toInt()
            }
            catch (e: Exception) {
                player.sendMessage(Core.bundle.get("client.command.voids.invalidargs"))
            }
        }
        if (count == 0) count = -1

        clientThread.post {
            val voids = Seq<Building>()
            for (tile in world.tiles) {
                if (tile.block() is PowerVoid) voids.add(tile.build)
            }
            voids.sort { c -> player.dst(c) }

            if (voids.size > 0) {
                val sb = StringBuilder()
                if (count > 0) sb.append(Core.bundle.format("client.command.voids.list", voids.size, count))
                else sb.append(Core.bundle.format("client.command.voids.listall", voids.size))
                for (void in voids) {
                    if (count == 0) break
                    sb.append(String.format("(%d,%d) ", void.tileX(), void.tileY()))
                    if (count > 0) count--
                }
                Core.app.post { player.sendMessage(sb.toString()) }
            }
            else Core.app.post { player.sendMessage(Core.bundle.get("client.command.voids.novoids")) }
        }
    }

    register("gamejointext [text...]", Core.bundle.get("client.command.gamejointext.description")) { args, player ->
        if (args.isEmpty() || args[0] == "") player.sendMessage(Core.bundle.get("client.command.gamejointext.clear"))
        else {
            Core.settings.put("gamejointext", args[0])
            player.sendMessage(Core.bundle.format("client.command.gamejointext.success", args[0]))
        }
    }

    register("gamewintext [text...]", Core.bundle.get("client.command.gamewintext.description")) {args, player ->
        if (args.isEmpty() || args[0] == "") player.sendMessage(Core.bundle.get("client.command.gamewintext.success"))
        else {
            Core.settings.put("gamewintext", args[0])
            player.sendMessage(Core.bundle.format("client.command.gamewintext.success", args[0]))
        }
    }

    register("gamelosetext [text...]", Core.bundle.get("client.command.gamelosetext.description")) {args, player ->
        if (args.isEmpty() || args[0] == "") player.sendMessage(Core.bundle.get("client.command.gamelosetext.clear"))
        else {
            Core.settings.put("gamelosetext", args[0])
            player.sendMessage(Core.bundle.get("client.command.gamelosetext.success"))
        }
    }

    register("ptext <option> [name] [text...]", Core.bundle.get("client.command.ptext.description")) { args, player ->
        when (args[0]) {
            "edit", "e" -> {
                if (args.size <= 1) {
                    player.sendMessage(Core.bundle.format("client.command.ptext.noselected", "edit"))
                    return@register
                }
                if (args.size <= 2) {
                    player.sendMessage(Core.bundle.format("client.command.ptext.edit.clear", args[1]))
                    if (Core.settings.get("ptext-${args[1]}", "").toString().isNotEmpty()) Core.settings.remove("ptext-${args[1]}")
                }
                else {
                    val text = args.drop(2).joinToString(" ")
                    Core.settings.put("ptext-${args[1]}", text)
                    player.sendMessage(Core.bundle.format("client.command.ptext.edit.success", args[1], text))
                }
            }
            "say", "s" -> {
                if (args.size <= 1) {
                    player.sendMessage(Core.bundle.format("client.command.ptext.noselected", "say"))
                    return@register
                }
                val text = Core.settings.get("ptext-${args[1]}", "").toString()
                if (text.isEmpty()) player.sendMessage(Core.bundle.format("client.command.ptext.notext", args[1]))
                else Call.sendChatMessage(text)
            }
            "js", "j" -> {
                if (args.size <= 1) {
                    player.sendMessage(Core.bundle.format("client.command.ptext.noselected", "run"))
                    return@register
                }
                val text = Core.settings.get("ptext-${args[1]}", "").toString()
                if (text.isEmpty()) player.sendMessage(Core.bundle.format("client.command.ptext.notext", args[1]))
                else player.sendMessage("[accent]${mods.scripts.runConsole(text)}")
            }
            "list", "l" -> {
                var exists = false
                val texts = Seq<String>()
                for (setting in Core.settings.keys()) {
                    if (setting.startsWith("ptext-")) {
                        exists = true
                        texts.add(setting)
                    }
                }
                if (!exists) player.sendMessage(Core.bundle.get("client.command.ptext.notexts"))
                else {
                    val sb = StringBuilder(Core.bundle.get("client.command.ptext.list"))
                    texts.forEach { sb.append("\n${it.drop(6)} [gray]-[] ${Core.settings.getString(it)}") }
                    player.sendMessage(sb.toString())
                }
            }
            else -> player.sendMessage(Core.bundle.get("client.command.ptext.invalidargs"))
        }
    }

    register("bannedcontent", Core.bundle.get("client.command.bannedcontent.description")) { _, player ->
        val sb = StringBuilder(Core.bundle.get("client.command.bannedcontent.text")).append(" ")
        state.rules.bannedUnits.forEach { sb.append(it.localizedName).append(" ") }
        state.rules.bannedBlocks.forEach { sb.append(it.localizedName).append(" ") }
        player.sendMessage(sb.toString())
    }

    register("mute <player>", Core.bundle.get("client.command.mute.description")) { args, player ->
        val id = args[0].toIntOrNull()
        val target = if (id != null && Groups.player.getByID(id) != null) Groups.player.getByID(id)
        else Groups.player.minBy { p -> BiasedLevenshtein.biasedLevenshteinInsensitive(p.name, args[0]) }

        player.sendMessage(Core.bundle.format("client.command.mute.success", target.coloredName(), target.id))
        val previous = mutedPlayers.firstOrNull { pair -> pair.first.name == target.name || pair.second == target.id }
        if (previous != null) mutedPlayers.remove(previous)
        mutedPlayers.add(Pair(target, target.id))
    }

    register("unmute <player>", Core.bundle.get("client.command.unmute.description")) { args, player ->
        val id = args[0].toIntOrNull()
        val target = Groups.player.minBy { p -> BiasedLevenshtein.biasedLevenshteinInsensitive(p.name, args[0]) }
        val match = mutedPlayers.firstOrNull { p -> (id != null && p.second == id) || (p.first != null && p.first.name == target.name) }
        if (match != null) {
            if (target != null) player.sendMessage(Core.bundle.format("client.command.unmute.success", target.coloredName(), target.id))
            else player.sendMessage(Core.bundle.format("client.command.unmute.byid", match.second))
            mutedPlayers.remove(match)
        }
        else player.sendMessage(Core.bundle.get("client.command.mute.notmuted"))
    }

    register("clearmutes", Core.bundle.get("client.command.clearmutes.description")) {_, player ->
        player.sendMessage(Core.bundle.get("client.command.clearmutes.success"))
        mutedPlayers.clear()
    }
    
    // Special commands

    register("admin [option]", Core.bundle.get("client.command.admin.description")) { args, player ->
        val arg = if (args.isEmpty()) "" else args[0]
        when (arg.lowercase()) {
            "s", "settings" -> {
                ui.settings.show()
                ui.settings.visible(4)
            }
            "l", "leaves" -> Client.leaves?.leftList() ?: player.sendMessage("[scarlet]Leave logs are disabled")
            else -> player.sendMessage("[scarlet]Invalid option specified, options are:\nSettings, Leaves")
        }
    }
    
    // Symbol replacements

    registerReplace("%", "c", "cursor") {
        "(${control.input.rawTileX()}, ${control.input.rawTileY()})"
    }

    registerReplace("%", "s", "shrug") {
        "¯\\_(ツ)_/¯"
    }

    registerReplace("%", "h", "here") {
        "(${player.tileX()}, ${player.tileY()})"
    }

    //FINISHME: add various % for gamerules

    // Experimentals (and funny commands)
    if (Core.settings.getBool("client-experimentals") || OS.hasProp("policone")) {
        register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
            sendMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
        }

        register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
            sendMessage("Silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance. They are not the same, please get it right!")
        }

        register("hh [h]", "!") { args, _ ->
            if (!net.client()) return@register
            val u = if (args.any()) content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.name) } else Vars.player.unit().type
            val current = ui.join.lastHost ?: return@register
            if (current.group == null) current.group = ui.join.communityHosts.find { it == current } ?.group ?: return@register
            switchTo = ui.join.communityHosts.filterTo(arrayListOf<Any>()) { it.group == current.group && it != current && !it.equals("135.181.14.60:6567") }.apply { add(current); add(u) } // IO attack has severe amounts of skill issue currently hence why its ignored
            val first = switchTo!!.removeFirst() as Host
            NetClient.connect(first.address, first.port)
        }


        register("rollback <time> <buildrange>", """
            Retrieves blocks from tile logs and places them into buildplan.
                [white]time[] How long ago to rollback to, in minutes before now
                [white]buildrange[] Radius within which buildings are rebuilt
        """.trimIndent()) { args, player ->
            val time: Instant
            val range: Float
            try {
                time = Instant.now().minus(args[0].toLong(), ChronoUnit.MINUTES)
                range = args[1].toFloat() * tilesize
            }
            catch (_: Exception) {
                player.sendMessage("[scarlet]Invalid arguments! Please specify 2 numbers (time and range)!")
                return@register
            }

            Tmp.r1.set(player.x - range, player.y - range, range * 2, range * 2)
            val tiles = world.tiles.filter { it.getBounds(Tmp.r2).overlaps(Tmp.r1) }
            clientThread.post {
                val plans: Seq<BuildPlan> = Seq()
                tiles.forEach {
                    if (!it.within(player.x, player.y, range) || it.block() != Blocks.air) return@forEach

                    val record = TileRecords[it] ?: return@forEach

                    // Get the sequence associated with the rollback time
                    val seq: TileLogSequence = record.lastSequence(time) ?: return@forEach
                    var shouldBuild = seq.snapshotIsOrigin
                    val state = seq.snapshot.clone()
                    var prevBlock: Block
                    // Step through logs until time is reached
                    for (diff in seq.iterator()) {
                        if (diff.time > time) break
                        prevBlock = state.block
                        diff.apply(state)
                        // Cursed - we can potentially change the function of isOrigin
                        if(state.block !== prevBlock) shouldBuild = diff.isOrigin // this is only modified when the log caused the construction/destruction of a building
                    }
                    if (!shouldBuild || state.block == Blocks.air) return@forEach // If building does not need to be built, do not build it
                    plans.add(BuildPlan(state.x, state.y, max(0, state.rotation), state.block, state.configuration))
                }

                if (plans.size == 0) return@post
                Core.app.post {
                    control.input.isBuilding = false
                    control.input.flushPlans(plans)
                }
            }
        }

        register("rebuild <start> <end> <buildrange>", """
            Rebuilds the last building for each tile over a time range, by using tile logs and placing them into buildplan.
                [white]start[] Start of time interval to rebuild, in minutes before now
                [white]end[] End of time interval to rebuild, in minutes before now
                [white]buildrange[] Radius within which buildings are rebuilt
        """.trimIndent()) { args, player ->
            val timeStart: Instant
            val timeEnd: Instant
            val range: Float
            try {
                timeEnd = Instant.now().minus(args[0].toLong(), ChronoUnit.MINUTES)
                timeStart = timeEnd.minus(args[1].toLong(), ChronoUnit.MINUTES)
                range = args[2].toFloat() * tilesize
            }
            catch (_: Exception) {
                player.sendMessage("[scarlet]Invalid arguments! Please specify 2 numbers (minutes and range)!")
                return@register
            }

            Tmp.r1.set(player.x - range, player.y - range, range * 2, range * 2)
            val tiles = world.tiles.filter { it.getBounds(Tmp.r2).overlaps(Tmp.r1) }
            clientThread.post {
                val plans: Seq<BuildPlan> = Seq()
                tiles.forEach {
                    if (!it.within(player.x, player.y, range) || it.block() != Blocks.air) return@forEach

                    val record = TileRecords[it] ?: return@forEach
                    if (record.sequences == null) return@forEach
                    var last: TileState? = null

                    seq@ for (seq in record.sequences!!.asReversed()) { // Rebuilds are likely used on recent states, so start from the last logs
                        val state = seq.snapshot.clone()
                        for (diff in seq.iterator()) {
                            diff.apply(state)
                            // Exit if we've gone past the time start
                            // Continue searching if we have not reached time end
                            if (diff.time > timeEnd) break@seq
                            if (diff.time < timeStart || !diff.isOrigin || state.block == Blocks.air) continue

                            last = state.clone()
                        }
                    }
                    if (last == null) return@forEach
                    plans.add(BuildPlan(last.x, last.y, last.rotation, last.block, last.configuration))
                }

                if (plans.size == 0) return@post
                Core.app.post {
                    control.input.flushPlans(plans)
                }
            }
        }

        register("rebuild2 <start> <end> <buildrange>", """
            Rebuilds the last building for each tile over a time range, by using tile logs and placing them into buildplan.
                [white]start[] Start of time interval to rebuild, in minutes before now
                [white]end[] End of time interval to rebuild, in minutes before now
                [white]buildrange[] Radius within which buildings are rebuilt
        """.trimIndent()) { args, player ->
            val timeStart: Instant
            val timeEnd: Instant
            val range: Float
            try {
                timeStart = Instant.now().minus(args[0].toLong(), ChronoUnit.MINUTES)
                timeEnd = Instant.now().minus(args[1].toLong(), ChronoUnit.MINUTES)
                range = args[2].toFloat() * tilesize
            }
            catch (_: Exception) {
                player.sendMessage("[scarlet]Invalid arguments!")
                return@register
            }
            if (timeStart >= timeEnd) { // I hate dealing with people
                player.sendMessage("[scarlet]Invalid time interval! Start must be before end.")
                return@register
            }

            // FINISHME: Add some filter for team. And optionally omit the collision code
            Tmp.r1.set(player.x - range, player.y - range, range * 2, range * 2)
            val tiles = world.tiles.filter { it.getBounds(Tmp.r2).overlaps(Tmp.r1) }
            clientThread.post {
                val states: Seq<TileState> = Seq()
                tiles.forEach {
                    if (!it.within(player.x, player.y, range) || it.block() != Blocks.air) return@forEach

                    val sequences = TileRecords[it]?.sequences ?: return@forEach
                    var last: TileState? = null
                    var prevBlock: Block
                    var shouldBuild = false // Whether the current block (in last) is origin, and should be built
                    var hasBeenOverwritten = false // Whether there is another block that is placed offset some time in the future

                    seq@ for (seq in sequences.asReversed()) { // Rebuilds are likely used on recent states, so start from the last logs
                        if (seq.snapshot.time > timeEnd) continue // Skip to the first sequence that overlaps with time interval
                        val state = seq.snapshot.clone()
                        shouldBuild = seq.snapshotIsOrigin && state.block !== Blocks.air
                        last = if (shouldBuild && seq.snapshot.time > timeStart) state.clone() else null
                        // Step through logs until time is reached
                        for (diff in seq.iterator()) {
                            if (diff.time > timeEnd) break // Abort if we have reached time end
                            prevBlock = state.block
                            diff.apply(state)
                            if (diff.time < timeStart) continue // Skip to time start
                            if (prevBlock !== state.block) {
                                if (state.block !== Blocks.air) { // If new building is built
                                    hasBeenOverwritten = hasBeenOverwritten || !diff.isOrigin // if(!diff.isOrigin)..=true
                                    shouldBuild = diff.isOrigin
                                } // If building is destroyed, it will be implicitly handled - last and shouldBuild will remain as it is
                                // so the state is saved for rebuilds when we exit the loop
                            }
                            if (!shouldBuild || state.block === Blocks.air) continue // Don't save the state if we cannot build it
                            if (last != null) {
                                diff.apply(last!!)
                            } else last = state.clone()
                            last!!.time = diff.time
                        }
                        if (shouldBuild || hasBeenOverwritten) break@seq // Break if we can restore that, or no earlier logs need to be used
                    }
                    if (!shouldBuild) return@forEach
                    states.add(last?: return@forEach)
                }

                if (states.size == 0) {
                    Core.app.post { player.sendMessage("[accent]No blocks found to rebuild.") }
                    return@post
                }
                // The following is so inefficient lol what
                // FINSIHME: Do not plan over EXISTING buildings
                clientThread.sortingInstance.sort(states, Comparator.comparing { it.time }) // Sort by time to deal with tile overlaps
                val minX = max(floor((player.x - range) / tilesize).toInt(), 0)
                val minY = max(floor((player.y - range) / tilesize).toInt(), 0)
                val takenTiles = GridBits(min(ceil((player.x + range) / tilesize).toInt() - minX, world.width()) + 1, min(ceil((player.y + range) / tilesize).toInt() - minY, world.height()) + 1)
                val plans = Seq<BuildPlan>()
                for (i in states.size - 1 downTo 0) { // Reverse this because latest should be rebuilt, not the earliest
                    var taken = false
                    val state = states[i]
                    // state.x, state.y describe the middle/bottom-left of the tile. So we steal code from TileLog.companion.linkedArea
                    val size = state.block.size
                    val offset = -(size - 1) / 2
                    val bounds = IntRectangle(state.x + offset, state.y + offset + size - 1, size, size)
                    bounds.iterator().forEach {
                        taken = taken || takenTiles.get(it.x - minX, it.y - minY) // Maybe use quadtree for large things
                    }
                    if (taken) continue
                    bounds.iterator().forEachRemaining {
                        takenTiles.set(it.x - minX, it.y - minY)
                    }
                    plans.add(BuildPlan(state.x, state.y, state.rotation, state.block, state.configuration))
                }
                states.clear()
                if (plans.size == 0) {
                    Core.app.post { player.sendMessage("[accent]No blocks found to rebuild.") }
                    return@post
                }
                Core.app.post {
                    control.input.flushPlans(plans)
                    player.sendMessage("[accent]Queued ${plans.size} blocks for rebuilding.")
                    plans.clear()
                }
            }
        }

        register("undo <player> [range]", "Undo Configs from a specific player (get rekt griefers)") { args, player ->
            val range: Float
            val id: Int?
            try {
                id = args[0].toInt()
                range = if (args.size >= 2) args[1].toFloat() * tilesize else Float.MAX_VALUE
            }
            catch (_: Exception) {
                player.sendMessage("[scarlet]Invalid args! Please specify a player id number and (optionally) a range number")
                return@register
            }

            Tmp.r1.set(player.x - range, player.y - range, range * 2, range * 2)
            val tiles = world.tiles.filter { it.getBounds(Tmp.r2).overlaps(Tmp.r1) }
            clientThread.post {
                var playerName: String? = null
                val plans: Seq<BuildPlan> = Seq()
                tiles.forEach {
                    if (!it.within(player.x, player.y, range)) return@forEach

                    val sequences = TileRecords[it]?.sequences ?: return@forEach
                    var last: TileState? = null

                    for (seq in sequences.asReversed()) {
                        val state = seq.snapshot.clone()
                        for (diff in seq.iterator()) {
                            diff.apply(state)

                            // Ignore if its the target player
                            if (diff.cause.playerID != id) {
                                last = state.clone()
                            } else if (playerName == null) playerName = diff.cause.shortName
                        }
                    }
                    // Only rebuild if block is different than the current block
                    // Only configure if its different
                    if (last == null) return@forEach

                    val plan = last!!.block != it.block() || last!!.rotation != it.build?.rotation
                    if (plan) {
                        if (last!!.block == Blocks.air || last!!.block is Prop) plans.add(BuildPlan(last!!.x, last!!.y))
                        else plans.add(BuildPlan(last!!.x, last!!.y, last!!.rotation, last!!.block, last!!.configuration))
                    }

                    if (last!!.block == it.block() && last!!.configuration != it.build?.config()) {
                        if (plan) {
                            plans.last().configLocal = true // FINISHME: Setting this on the client thread is a bad idea
                            plans.last().config = last!!.configuration
                        } else {
                            configs.add(ConfigRequest(it.build, last!!.configuration))
                        }
                    }
                }

                Core.app.post {
                    player.sendMessage("[accent] Undoing ${configs.size} configs and ${plans.size} builds made by $playerName")
                    control.input.flushPlans(plans, false, true, false) // Overplace
                }
            }
        }

        register("seer", "Seer related commands") { _, _ -> // FINISHME
            SeerDialog.show()
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
    clientCommandHandler.register(format.substringBefore(' '), args, description, runner)
}

fun replaceMsg(match: String, matchRegex: Boolean, from: String, fromRegex: Boolean, to: String){
    clientThread.post {
        var matchReg = Regex("No. Something went wrong.")
        var fromReg = Regex("No. Something went wrong.")
        if(matchRegex) matchReg = match.toRegex()
        if(fromRegex) fromReg = from.toRegex()
        var num = 0
        val seq = player.team().data().buildings.filterIsInstance<MessageBlock.MessageBuild>()
        seq.forEach {
            val msg = it.message.toString()
            if((!matchRegex && !msg.contains(match)) || (matchRegex && !matchReg.matches(msg))) return@forEach
            val msg2 = if(fromRegex) msg.replace(fromReg, to)
            else msg.replace(from, to)
            configs.add(ConfigRequest(it.tileX(), it.tileY(), msg2))
            num++
        }
        player.sendMessage("[accent]Queued $num messages for editing")
    }
}

fun registerReplace(symbol: String = "%", vararg cmds: String, runner: Prov<String>) {
    cmds.forEach { registerReplace(symbol, it, runner) }
}
fun registerReplace(symbol: String = "%", cmd: String, runner: Prov<String>) {
    if(symbol.length != 1) throw IllegalArgumentException("Bad symbol in replace command")
    val seq = containsCommandHandler.get(symbol) { Seq() }
    seq.add(Pair(cmd, runner))
    seq.sort(Structs.comparingInt{ -it.first.length })
}

private fun connectTls(certname: String, onFinish: (Packets.CommunicationClient, X509Certificate) -> Unit) {
    val cert = Main.keyStorage.aliases().firstOrNull { it.second.equals(certname, true) }
        ?.run { Main.keyStorage.findTrusted(BigInteger(first)) }
        ?: Main.keyStorage.trusted().firstOrNull { it.readableName.equals(certname, true) }

    cert ?: run {
        player.sendMessage(Core.bundle.format("client.tls.foundnocert", certname))
        return
    }

    if (cert == Main.keyStorage.cert()) {
        player.sendMessage(Core.bundle.get("client.tls.connectself"))
        return
    }

    val preexistingConnection = Main.tlsPeers.firstOrNull { it.second.peer.expectedCert.encoded.contentEquals(cert.encoded) }

    if (preexistingConnection != null) {
        if (preexistingConnection.second.peer.handshakeDone) {
            onFinish(preexistingConnection.first, cert)
        } else {
            player.sendMessage(Core.bundle.get("client.tls.incompletehandshake"))
        }
    } else {
        player.sendMessage(Core.bundle.get("client.tls.sendingrequest"))
        Main.connectTls(cert, {
            player.sendMessage(Core.bundle.get("client.tls.connected"))
            // delayed to make sure receiving end is ready
            Timer.schedule({ onFinish(it, cert) }, .1F)
        }, { player.sendMessage(Core.bundle.get("client.tls.setupcommunication")) })
    }
}
