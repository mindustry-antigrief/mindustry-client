package mindustry.client

import arc.*
import arc.func.Prov
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.*
import mindustry.Vars.*
import mindustry.Vars.state
import mindustry.ai.*
import mindustry.ai.types.*
import mindustry.client.ClientVars.*
import mindustry.client.Spectate.spectate
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.communication.Packets
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.follow
import mindustry.client.navigation.Navigation.getAllyTree
import mindustry.client.navigation.Navigation.getTree
import mindustry.client.navigation.Navigation.navigateTo
import mindustry.client.navigation.Navigation.navigator
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.core.*
import mindustry.entities.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.input.*
import mindustry.logic.*
import mindustry.net.*
import mindustry.ui.Fonts
import mindustry.world.*
import mindustry.world.blocks.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.defense.turrets.BaseTurret.*
import mindustry.world.blocks.distribution.ItemBridge
import mindustry.world.blocks.logic.*
import mindustry.world.blocks.logic.LogicBlock.*
import mindustry.world.blocks.power.*
import mindustry.world.blocks.sandbox.PowerVoid
import mindustry.world.blocks.units.*
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import java.io.*
import java.math.*
import java.security.*
import java.security.cert.*
import kotlin.math.*
import kotlin.random.*

object Client {
    var leaves: Moderation? = Moderation()
    val tiles = mutableListOf<Tile>()
    val timer = Interval(4)
//    val kts by lazy { ScriptEngineManager().getEngineByExtension("kts") }
    val autoTransfer by lazy { AutoTransfer() } // FINISHME: Awful

    fun initialize() {
        registerCommands()
        ClientLogic()

        val bc = BouncyCastleProvider()
        // append bouncycastle to the list
        val n = Security.getProviders().contentToString().length
        Security.insertProviderAt(bc, n)
        Security.insertProviderAt(BouncyCastleJsseProvider(bc), n + 1)
        provider.setProvider(bc)
        // FINISHME is this secure?  what exactly does this mean?  test without this every so often with new bouncycastle versions
        System.setProperty("jdk.tls.namedGroups", "secp256r1")
    }

    fun update() {
        autoTransfer.update()
        Navigation.update()
        PowerInfo.update()
        Spectate.update() // FINISHME: Why is spectate its own class? Move it here, no method is needed just add an `if` like below
        Core.camera.bounds(cameraBounds) // do we do this here or on draw? can Core.camera be null?
        cameraBounds.grow(2 * tilesizeF)

        if (ratelimitRemaining != Administration.Config.interactRateLimit.num() - 1 && timer.get(3, (Administration.Config.interactRateWindow.num() + 1) * 60F)) { // Reset ratelimit, extra second to account for server lag
            ratelimitRemaining = Administration.Config.interactRateLimit.num() - 1
        }

        if (!configs.isEmpty()) {
            try {
                if (ratelimitRemaining > 0 || !net.client()) { // Run the config NOTE: Counter decremented in InputHandler and not here so that manual configs don't cause issues
                    configs.poll().run()
                }
            } catch (e: Exception) {
                Log.err(e)
            }
        }

        if (state?.rules?.editor == true) ui.editor.autoSave()
    }

    fun draw() {
        Spectate.draw()
        autoTransfer.draw()

        // Spawn path
        if (spawnTime < 0 && spawner.spawns.size < 50) { // FINISHME: Repetitive code, squash down
            Draw.color(state.rules.waveTeam.color)
            for (i in 0 until spawner.spawns.size) {
                var target: Tile? = spawner.spawns[i]
                Lines.beginLine()
                while(target != pathfinder.getTargetTile(target, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore)).also { target = it }) {
                    Lines.linePoint(target)
                }
                Lines.endLine()
            }
        } else if (spawnTime != 0f && travelTime != 0f && spawner.spawns.size < 50 && timer.get(0, travelTime)) {
            if (timer.get(1, spawnTime)) tiles.addAll(spawner.spawns)
            for (i in 0 until tiles.size) {
                val t = tiles.removeFirst()
                val target = pathfinder.getTargetTile(t, pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore))
                if (target != t) tiles.add(target)
                Fx.healBlock.at(t.worldx(), t.worldy(), 1f, state.rules.waveTeam.color)
            }
        }

        // Turret range
        val bounds = Core.camera.bounds(Tmp.r3).grow(tilesize.toFloat())
        if (showingTurrets || showingInvTurrets || showingAllyTurrets) {
            val units = Core.settings.getBool("unitranges")
            if (showingTurrets || showingInvTurrets) {
                val flying = player.unit().isFlying
                getTree().intersect(bounds) {
                    if ((units || it.turret) && it.canShoot() && (it.targetAir || it.targetGround)) {//circles.add(it to if (it.canHitPlayer()) it.entity.team().color else Team.derelict.color)
                        val valid = (flying && it.targetAir) || (!flying && it.targetGround)
                        val validInv = (!flying && it.targetAir) || (flying && it.targetGround)
                        Drawf.dashCircle(
                            it.entity.x, it.entity.y, it.range - tilesize,
                            if ((valid && showingTurrets) || (validInv && showingInvTurrets)) it.entity.team().color else Team.derelict.color)
                    }
                }
            }
            if (showingAllyTurrets) {
                getAllyTree().intersect(bounds) {
                    if ((units || it.turret) && it.canShoot() && (it.targetAir || it.targetGround)) {
                        Drawf.dashCircle(it.entity.x, it.entity.y, it.range - tilesize, it.entity.team().color)
                    }
                }
            }
        }

        // Player controlled turret range
        if ((player.unit() as? BlockUnitUnit)?.tile() is BaseTurret.BaseTurretBuild) {
            Drawf.dashCircle(player.x, player.y, player.unit().range(), player.team().color)
        }

        // Overdrive range
        if (showingOverdrives) {
            overdrives.forEach { b ->
                val range = b.realRange()
                if (b.team == player.team() && bounds.overlaps(b.x - range, b.y - range, range * 2, range * 2)) b.drawSelect()
            }
        }
    }

    private fun registerCommands() {
        register("help [page/command]", Core.bundle.get("client.command.help.description")) { args, player ->
            if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
                val command = clientCommandHandler.commandList.find { it.text == args[0] }
                if (command != null) {
                    player.sendMessage(Strings.format("[orange] !@[white] @[lightgray] - @",
                        command.text, command.paramText, command.description))
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
            ui.unitPicker.pickUnit(content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) })
        }

        register("unit <unit-type>", "Picks a unit nearest to cursor. Use null or no args to unqueue switch.") { args, _ ->
            if (args[0] == "null" || args[0] == "") ui.unitPicker.unpickUnit()
            else ui.unitPicker.pickUnit(
                content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) },
                Core.input.mouseWorldX(), Core.input.mouseWorldY(), false
            )
        }

        register("count <unit-type>", Core.bundle.get("client.command.count.description")) { args, player ->
            if (args[0] == "all") {
                val cap = Units.getStringCap(player.team())
                val sb = StringBuilder("[accent]Count all units (Cap: $cap)")
                for (type in content.units()) {
                    var total = 0
                    (player.team().data().unitCache(type) ?: Seq.with()).withEach { total++ } // If possible please rewrite this single line. It looks scuffed.
                    if (total == 0) continue
                    sb.append("\n[white]${Fonts.stringIcons.get(type.name)}[] ${type.localizedName}: $total")
                }
                player.sendMessage(sb.toString())
            }
            else {
                val type = content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) }
                val cap = Units.getStringCap(player.team()); var total = 0; var free = 0; var flagged = 0; var unflagged = 0; var players = 0; var formation = 0; var logic = 0; var freeFlagged = 0; var logicFlagged = 0

                (player.team().data().unitCache(type) ?: Seq.with()).withEach {
                    total++
                    val ctrl = sense(LAccess.controlled).toInt()
                    if (flag == 0.0) unflagged++
                    else {
                        flagged++
                        if (ctrl == 0) freeFlagged++
                    }
                    when (ctrl) {
                        GlobalConstants.ctrlPlayer -> players++
                        GlobalConstants.ctrlFormation -> formation++
                        GlobalConstants.ctrlProcessor -> {
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
                Players(Formation): $players($formation)
                Logic(Logic Flagged): $logic($logicFlagged)
                """.trimIndent())
            }
        }

        register("spawn [x] [y]", Core.bundle.get("client.command.spawn.description")) {args, player ->
            try {
                if (args.size == 2) state.teams.closestCore(args[0].toFloat(), args[1].toFloat(), player.team())?.requestSpawn(player)
                else state.teams.closestCore(Core.input.mouseWorldX(), Core.input.mouseWorldY(), player.team())?.requestSpawn(player)
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "go"))
            }
        }

        register("go [x] [y]", Core.bundle.get("client.command.go.description")) { args, player ->
            try {
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                else throw IOException()
                navigateTo(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "go"))
            }
        }

        register("lookat [x] [y]", Core.bundle.get("client.command.lookat.description")) { args, player ->
            try {
                (control.input as? DesktopInput)?.panning = true
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                spectate(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "lookat"))
            }
        }

        register("tp [x] [y]", Core.bundle.get("client.command.tp.description")) { args, player ->
            try {
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                NetClient.setPosition(lastSentPos.cpy().scl(tilesize.toFloat()).x, lastSentPos.cpy().scl(tilesize.toFloat()).y)
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "tp"))
            }
        }

        register("here [message...]", Core.bundle.get("client.command.here.description")) { args, player ->
            sendMessage(Strings.format("@[#3141FF](@, @)", if (args.isEmpty()) "" else args[0] + " ", player.tileX(), player.tileY()))
        }

        register("cursor [message...]", Core.bundle.get("client.command.cursor.description")) { args, _ ->
            sendMessage(Strings.format("@[#3141FF](@, @)", if (args.isEmpty()) "" else args[0] + " ", control.input.rawTileX(), control.input.rawTileY()))
        }

        register("builder [options...]", Core.bundle.get("client.command.builder.description")) { args, _: Player ->
            follow(BuildPath(if (args.isEmpty()) "" else args[0]))
        } // FINISHME: This is so scuffed lol

        register("miner [options...]", Core.bundle.get("client.command.miner.description")) { args, _: Player ->
            if (args.isEmpty()) follow(MinePath()) else follow(MinePath(args = args[0]))
        } // FINISHME: This is so scuffed lol

        register("buildmine", "Buildpath (self) + mine (all)") {_, _: Player ->
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

        register("blank", "Sends nothing.") { _, _ ->
            sendMessage("\u200B")
        }
//        register("kts <code...>", Core.bundle.get("client.command.kts.description")) { args, player: Player -> // FINISHME: Bundle
//            player.sendMessage("[accent]${try{ kts.eval(args[0]) }catch(e: Throwable){ e }}")
//        }

        register("/js <code...>", Core.bundle.get("client.command.serverjs.description")) { args, player ->
            player.sendMessage("[accent]${mods.scripts.runConsole(args[0])}")
            sendMessage("/js ${args[0]}")
        }

        register("cc [setting]", Core.bundle.get("client.command.cc.description")) { args, player ->
            if (args.size != 1 || !args[0].matches("(?i)^[ari].*".toRegex())) {
                player.sendMessage(Core.bundle.format("client.command.cc.invalid", player.team().data().command.localized()))
                return@register
            }

            val cc = Units.findAllyTile(player.team(), player.x, player.y, Float.MAX_VALUE / 2) { it is CommandCenter.CommandBuild }
            if (cc != null) {
                Call.tileConfig(player, cc, when (args[0].lowercase()[0]) {
                    'a' -> UnitCommand.attack
                    'r' -> UnitCommand.rally
                    else -> UnitCommand.idle
                })
                player.sendMessage(Core.bundle.format("client.command.cc.success", args[0]))
            } else player.sendMessage(Core.bundle.get("client.command.cc.notfound"))
        }

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
            val grids = PowerGraph.activeGraphs.select { it.team == player.team() }.associate { it.id to it.all.copy() }
            val confirmed = args.any() && args[0] == "c" // Don't configure by default
            val inProgress = !configs.isEmpty()
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
            configs.addAll(tmp)
            @Suppress("CAST_NEVER_SUCCEEDS") val msg = ui.chatfrag.addMessage("", null as? Color)
            msg.message = when {
                confirmed && inProgress -> Core.bundle.format("client.command.fixpower.inprogress", configs.size, n)
                confirmed -> { // Actually fix the connections
                    configs.add { // This runs after the connections are made
                        msg.message = Core.bundle.format("client.command.fixpower.success", n, PowerGraph.activeGraphs.select { it.team == player.team() }.size)
                        msg.format()
                    }
                    Core.bundle.format("client.command.fixpower.confirmed", n)
                }
                else -> Core.bundle.format("client.command.fixpower.confirm", n, grids.size)
            }
            msg.format()
        }

        @Suppress("unchecked_cast")
        register("fixcode [options...]", "Disables problematic \"attem >= 83\" flagging logic") { args, player -> // FINISHME: Bundle
            val builds = Seq<LogicBuild>()
            Vars.player.team().data().buildings.getObjects(builds as Seq<Building>) // Must be done on the main thread
            clientThread.post {
                builds.removeAll { it !is LogicBlock.LogicBuild }
                val confirmed = args.any() && (args[0] == "c" || args[0] == "r") // Don't configure by default
                val inProgress = !configs.isEmpty()
                var n = 0

                if (confirmed && !inProgress) {
                    Log.debug("Patching!")
                    builds.forEach {
                        val patched = ProcessorPatcher.patch(it.code, args[0])
                        if (patched != it.code) {
                            Log.debug("${it.tileX()} ${it.tileY()}")
                            configs.add(ConfigRequest(it.tileX(), it.tileY(), compress(patched, it.relativeConnections())))
                            n++
                        }
                    }
                }
                Core.app.post {
                    if (confirmed) {
                        if (inProgress) player.sendMessage("[scarlet]The config queue isn't empty, there are ${configs.size} configs queued, there are ${ProcessorPatcher.countProcessors(builds)} processors to reconfigure.") // FINISHME: Bundle
                        else player.sendMessage("[accent]Successfully reconfigured $n/${builds.size} processors")
                    } else {
                        player.sendMessage("[accent]Run [coral]!fixcode [c | r][] to reconfigure ${ProcessorPatcher.countProcessors(builds)}/${builds.size} processors")
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
                    ui.settings.show()
                    ui.settings.visible(4)
                }
                "l", "leaves" -> leaves?.leftList() ?: player.sendMessage("[scarlet]Leave logs are disabled")
                else -> player.sendMessage("[scarlet]Invalid option specified, options are:\nSettings, Leaves")
            }
        }

        register("clearghosts [c]", "Removes the ghosts of blocks which are in range of enemy turrets, useful to stop polys from building forever") { args, player -> // FINISHME: Bundle
            val confirmed = args.any() && args[0].startsWith("c") // Don't clear by default
            val all = confirmed && Main.keyStorage.builtInCerts.contains(Main.keyStorage.cert()) && args[0] == "clear"
            val plans = mutableListOf<Int>()

            for (plan in Vars.player.team().data().blocks) {
                val block = content.block(plan.block.toInt())
                if (!(all || getTree().any(Tmp.r1.setCentered(plan.x * tilesize + block.offset, plan.y * tilesize + block.offset, block.size * tilesizeF)))) continue

                plans.add(Point2.pack(plan.x.toInt(), plan.y.toInt()))
            }

            Log.info("Took @ | new = @", Time.elapsed(), useNew)

            if (confirmed) {
                plans.chunked(100) { Call.deletePlans(player, it.toIntArray()) }
                player.sendMessage("[accent]Removed ${plans.size} plans, ${Vars.player.team().data().blocks.size} remain")
            } else player.sendMessage("[accent]Found ${plans.size} (out of ${Vars.player.team().data().blocks.size}) block ghosts within turret range, run [coral]!clearghosts c[] to remove them")
        }

        register("e <certname> <message...>", "Sends an encrypted message over TLS.") { args, _ -> // FINISHME: Bundle
            val certname = args[0]
            val msg = args[1]

            connectTls(certname) { comms, cert ->
                comms.send(MessageTransmission(msg))
                ui.chatfrag.addMessage(msg, "[coral]${Main.keyStorage.cert()?.readableName ?: "you"} [white]-> ${Main.keyStorage.aliasOrName(cert)}", encrypted)
                lastCertName = cert.readableName
            }
        }

        register("stoppathing <name/id...>", "Stop someone from pathfinding.") { args, _ -> // FINISHME: Bundle
            val name = args.joinToString(" ")
            val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { Strings.levenshtein(Strings.stripColors(it.name), name) }!!
            Main.send(CommandTransmission(CommandTransmission.Commands.STOP_PATH, Main.keyStorage.cert() ?: return@register, player))
            // FINISHME: success message
        }

        register("replacemessage <from> <to> [useRegex=t]", "Replaces corresponding text in messages.") { args, player ->
            if (args[0].length < 3) {
                player.sendMessage("[scarlet]That might not be a good idea...")
                return@register
            }
            val useRegex = args.size > 2 && args[2] == "t"
            replaceMsg(args[0], useRegex, args[0], useRegex, args[1])
        }

        register(
            "replacemsgif <matches> <from> <to> [useMatchRegex=t] [useFromRegex=t]",
            "Replaces corresponding text in messages, only if they match the text."
        ) { args, player ->
            if (args[0].length < 3) {
                player.sendMessage("[scarlet]That might not be a good idea...")
                return@register
            }
            replaceMsg(args[0], args.size > 3 && args[3] == "t", args[1], args.size > 4 && args[4] == "t", args[2])
        }

        register("c <message...>", "Send a message to other client users.") { args, _ ->  // FINISHME: Bundle
            Main.send(ClientMessageTransmission(args[0]).apply { addToChatfrag() })
        }

        register("mapinfo", "Lists various useful map info.") { _, player -> // FINISHME: Bundle
            player.sendMessage(with(state) {
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
            val type = content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) }

            player.team().data().unitCache(type)
                ?.filter { it.controller() is LogicAI }
                ?.groupBy { (it.controller() as LogicAI).controller }
                ?.forEach { (build, units) ->
                    player.sendMessage("x${units.size} [accent](${build.tileX()}, ${build.tileY()})")
                }
        }
        
        register("phasei <interval>", "Changes interval for end bridge when shift+dragging phase conveyors.") { args, player ->
            try{
                val interval = Integer.parseInt(args[0])
                val maxInterval = (Blocks.phaseConveyor as ItemBridge).range
                if(interval < 1 || interval > maxInterval){
                    player.sendMessage("[scarlet]Interval must be within 1 and $maxInterval!")
                    return@register
                }
                ItemBridge.phaseWeaveInterval = interval
                Core.settings.put("weaveEndInterval", interval)
                player.sendMessage("[accent]Successfully set interval to $interval.")
            } catch (e : Exception){
                player.sendMessage("[scarlet]Failed to parse integer!")
            }
        }
        register("hfixnode [pls]", "Displays info for !fixnode") { args, player -> //TODO: Make param info some tooltip/popup thing
            if (args.isNotEmpty() && args[0] == "pls") player.sendMessage("[lightgray]Thanks for saying please!") // i am going insane
            player.sendMessage(
                "Set enforcement of node configs, use either [scarlet]0/false []or [green]1/true[]\n" +
                "   [lightgray]Enter your command in the form [orange]!fixnode[] [white][[[yellow][t][]emptoggle]/[yellow][d][]isable[yellow](-1)[]][]\n" +
                "   OR [orange]!fixnode[] [white][normal] [source][]\n" +
                "   [white]normal[] - Whether to enforce on regular copy and paste (not just from saved schematics). Set to -1 to completely disable fixing.\n" +
                "   [white]source[] - Whether to enforce on item sources\n" +
                "[orange]-- End --"
            )
        }
        register("fixnode [do-!hfixnode] [for-more-info]","Do !hfixnode for more info") { args, player -> //TODO: Bundle (how about no)
            val setting = PowerNode.PowerNodeFixSettings.get(Core.settings.getInt("nodeconf", 0))
            val curr = PowerNode.PowerNodeFixSettings.get(PowerNode.PowerNodeBuild.fixNode)
            if (args.isEmpty()) {
                player.sendMessage("[accent]Node fixing is currently [white]$curr[].")
                return@register
            }
            val set = { new: Int ->
                PowerNode.PowerNodeBuild.fixNode = new
                Core.settings.put("nodeconf", new)
            }
            val print = { changed: Boolean, now: PowerNode.PowerNodeFixSettings ->
                player.sendMessage("[accent]Automatic node fixing [white]$now[]${if (changed) "" else " [lightgray](no change)[]"}.") }
            if ((args[0] == "-1" && args.size < 2) || args[0] == "d" || args[0] == "disable") {
                set(PowerNode.PowerNodeFixSettings.disabled.ordinal)
                player.sendMessage("[accent]Automatic node fixing disabled")
                return@register
            }
            if (args[0] == "t" || args[0] == "temp" || args[0] == "temptoggle") {
                if (setting == curr) {
                    player.sendMessage("[accent]No changes made (currently [white]$curr[]).")
                    return@register
                }
                if (curr == PowerNode.PowerNodeFixSettings.disabled) { // enable back
                    PowerNode.PowerNodeBuild.fixNode = setting.ordinal
                    player.sendMessage("[accent]Automatic node fixing re-enabled (now [white]$setting[]).")
                    return@register
                } else { // disable
                    PowerNode.PowerNodeBuild.fixNode = PowerNode.PowerNodeFixSettings.disabled.ordinal
                    player.sendMessage("[accent]Automatic node fixing temporarily disabled.")
                    return@register
                }
            }
            val normal = try { when(Integer.parseInt(args[0])){ 0 -> false; 1 -> true; else -> throw Exception() } }
            catch (e: Exception) { // cursed
                player.sendMessage("[scarlet]Invalid input for first argument!")
                return@register
            }
            if (args.size <= 1) {
                val new = PowerNode.PowerNodeFixSettings.get(normal, setting.source)
                val changed = new != curr || new != setting
                if (changed) set(new.ordinal)
                print(changed, new)
                return@register
            }
            val source = try { when(Integer.parseInt(args[1])){ 0 -> false; 1 -> true; else -> throw Exception() } }
            catch (e: Exception) { // cursed2
                player.sendMessage("[scarlet]Invalid input for second argument!")
                return@register
            }
            val new = PowerNode.PowerNodeFixSettings.get(normal, source)
            val changed = new != curr || new != setting
            if (changed) set(new.ordinal)
            print(changed, new)
        }

        register("pathing", "Change the pathfinding algorithm") { _, player ->
            if (navigator is AStarNavigator) {
                navigator = AStarNavigatorOptimised
                player.sendMessage("[accent]Using [green]improved[] algorithm")
            } else if (navigator is AStarNavigatorOptimised) {
                navigator = AStarNavigator
                player.sendMessage("[accent]Using [gray]classic[] algorithm")
            }
        }

        register("pic [quality]", "Sets the image quality for sending via chat (0 -> png)") { args, player ->
            if (args.isEmpty()) {
                player.sendMessage("[accent]Enter a value between 0.0 and 1.0 for quality (0.0 -> png)\n" +
                        "Currently set to [white]${jpegQuality}${if(jpegQuality == 0f)" (png)" else ""}[].")
                return@register
            }
            try {
                val quality = args[0].toFloat()
                if (quality !in 0f .. 1f) {
                    player.sendMessage("[scarlet]Please enter a number between 0.0 and 1.0 (please)")
                    return@register
                }
                jpegQuality = quality
                Core.settings.put("commpicquality", quality)
                player.sendMessage("[accent]Set quality to [white]${quality}${if(quality == 0f)" (png)" else ""}[].")
            } catch (e: Exception) {
                Log.err(e)
                if (e is NumberFormatException) player.sendMessage("[scarlet]Please enter a valid number (please)")
                else player.sendMessage("[scarlet]Something went wrong.")
            }
        }

        register("procfind [options...]", "Highlights processors based on search query") { args, player ->
            val newArgs = args.joinToString(" ").split(" ").toTypedArray() // TODO: fix the command arguments. this is beyond cursed

            when (newArgs[0]) {
                "query" -> {
                    if (newArgs.size < 2) {
                        player.sendMessage("[accent]Use [coral]!procfind query ...[] to enter a search query.")
                        return@register
                    }
                    val queryRegex = newArgs.drop(1).joinToString(" ").toRegex()
                    ProcessorFinder.queries.add(queryRegex)
                    ProcessorFinder.search()
                }
                "queries" -> {
                    val sb = StringBuilder().append("[accent]ProcFind queries:\n")
                    ProcessorFinder.queries.forEach { r -> sb.append("\n").append(r.toPattern().pattern()) }
                    player.sendMessage(sb.toString())
                }
                "search" -> ProcessorFinder.search()
                "searchall" -> ProcessorFinder.searchAll()
                "clear" -> {
                    player.sendMessage("[accent]Cleared ${ProcessorFinder.getCount()} highlighters.")
                    ProcessorFinder.clear()
                }
                "list" -> ProcessorFinder.list()
                else -> player.sendMessage("""
                    [accent]Use [coral]!procfind query ...[] to add a search query (automatically continues with !procfind search).
                    Use [coral]!procfind queries[] to list all regex queries. 
                    Use [coral]!procfind search[] to scan your team for processors matching queries
                    Use [coral]!procfind searchall[] to scan every team for processors matching queries
                    Use [coral]!procfind list[] to log clusters of processors.
                    Use [coral]!procfind clear[] to clear queries.
                """.trimIndent())
            }
        }

        register("voids [count]", "Lists power void locations. Use count 0 (or less) to list all") { args, player ->
            var count = 1
            if (args.isNotEmpty()) {
                try {
                    count = args[0].toInt()
                }
                catch (e: Exception) {
                    player.sendMessage("Invalid parameter! Input a number.")
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
                    if (count > 0) sb.append("[accent]Found ${voids.size} voids. Listing only ${count}:")
                    else sb.append("[accent]Found ${voids.size} voids:")
                    for (void in voids) {
                        if (count == 0) break
                        sb.append(String.format("(%d,%d) ", void.tileX(), void.tileY()))
                        if (count > 0) count--
                    }
                    Core.app.post { player.sendMessage(sb.toString()) }
                }
                else Core.app.post { player.sendMessage("[accent]No voids found") }
            }
        }

        register("gamejointext [text...]", "Sets the text you automatically send upon joining.") { args, player ->
            if (args.isEmpty() || args[0] == "") player.sendMessage("[accent]Cleared gamejointext because no text was provided.")
            else {
                Core.settings.put("gamejointext", args[0])
                player.sendMessage("[accent]gamejointext text set to \"${args[0]}\"")
            }
        }


        register("gamewintext [text...]", "Sets the text you automatically send when you win. (eg 'gg bois!')") {args, player ->
            if (args.isEmpty() || args[0] == "") player.sendMessage("[accent]Cleared gamewintext because no text was provided.")
            else {
                Core.settings.put("gamewintext", args[0])
                player.sendMessage("[accent]gamewintext text set to \"${args[0]}\"")
            }
        }

        register("gamelosetext [text...]", "Sets the text you automatically send when you lose.") {args, player ->
            if (args.isEmpty() || args[0] == "") player.sendMessage("[accent]Cleared gamelosetext because no text was provided.")
            else {
                Core.settings.put("gamelosetext", args[0])
                player.sendMessage("[accent]gamelosetext text set to \"${args[0]}\"")
            }
        }

        register("ptext <option> [name] [text...]",
            """Sets custom personal text. 
                |Use [accent]!ptext edit/e <name> <text...>[] to create/edit a text. Input no text to clear the ptext.
                |Use [accent]!ptext say/s <name>[] to say the registered text in chat.
                |Use [accent]!ptext list/l[] to list out registered texts
                |Use [accent]!ptext js/j <name>[] to run a ptext as a js command""".trimMargin()
        ) { args, player ->
            when (args[0]) {
                "edit", "e" -> {
                    if (args.size <= 1) {
                        player.sendMessage("[scarlet]No text name selected to edit.")
                        return@register
                    }
                    if (args.size <= 2) {
                        player.sendMessage("[accent]No text inputted. Clearing registered text \"${args[1]}\"")
                        if (Core.settings.get("ptext-${args[1]}", "").toString().isNotEmpty()) Core.settings.remove("ptext-${args[1]}")
                    }
                    else {
                        val text = args.drop(2).joinToString(" ")
                        Core.settings.put("ptext-${args[1]}", text)
                        player.sendMessage("[accent]Custom text \"${args[1]}\" set to \"$text\"")
                    }
                }
                "say", "s" -> {
                    if (args.size <= 1) {
                        player.sendMessage("[scarlet]No text name selected to say.")
                        return@register
                    }
                    val text = Core.settings.get("ptext-${args[1]}", "").toString()
                    if (text.isEmpty()) player.sendMessage("[accent]No existing text is set for \"${args[1]}\"")
                    else Call.sendChatMessage(text)
                }
                "js", "j" -> {
                    if (args.size <= 1) {
                        player.sendMessage("[scarlet]No text name selected to say.")
                        return@register
                    }
                    val text = Core.settings.get("ptext-${args[1]}", "").toString()
                    if (text.isEmpty()) player.sendMessage("[accent]No existing text is set for \"${args[1]}\"")
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
                    if (!exists) player.sendMessage("[accent]You currently have no ptext set")
                    else {
                        val sb = StringBuilder("[accent]Your ptext(s):")
                        texts.forEach { sb.append("\n${it.drop(6)} [gray]-[] ${Core.settings.getString(it)}") }
                        player.sendMessage(sb.toString())
                    }
                }
                else -> player.sendMessage("[scarlet]Invalid option! [accent]Valid options are edit, say, list")
            }
        }

        register("bannedcontent", "Lists banned units and blocks on the map") { args, player ->
            val sb = StringBuilder("[accent]Banned content on this map: ")
            state.rules.bannedUnits.forEach {sb.append(it.localizedName).append(" ") }
            state.rules.bannedBlocks.forEach { sb.append(it.localizedName).append(" ") }
            player.sendMessage(sb.toString())
        }

        registerReplace("%", "c", "cursor") {
            Strings.format("(@, @)", control.input.rawTileX(), control.input.rawTileY())
        }

        registerReplace("%", "s", "shrug") {
            "¯\\_(ツ)_/¯"
        }

        registerReplace("%", "h", "here") {
            Strings.format("(@, @)", player.tileX(), player.tileY())
        }

        //TOOD: add various % for gamerules
    }

    fun replaceMsg(match: String, matchRegex: Boolean, from: String, fromRegex: Boolean, to: String){
        clientThread.post {
            var matchReg = Regex("No. Something went wrong.")
            var fromReg = Regex("No. Something went wrong.")
            if(matchRegex) matchReg = match.toRegex()
            if(fromRegex) fromReg = from.toRegex()
            var num = 0
            val seq = Seq<Building>()
            player.team().data().buildings.getObjects(seq)
            seq.each<MessageBlock.MessageBuild>({ it.team() == player.team() && it is MessageBlock.MessageBuild}, {
                val msg = it.message.toString()
                if((!matchRegex && !msg.contains(match)) || (matchRegex && !matchReg.matches(msg))) return@each
                val msg2 = if(fromRegex) msg.replace(fromReg, to)
                else msg.replace(from, to)
                configs.add(ConfigRequest(it.tileX(), it.tileY(), msg2))
                num++
            })
            player.sendMessage("[accent]Queued $num messages for editing");
        }
    }

    private fun connectTls(certname: String, onFinish: (Packets.CommunicationClient, X509Certificate) -> Unit) { // FINISHME: Bundle
        val cert = Main.keyStorage.aliases().singleOrNull { it.second.equals(certname, true) }?.run { Main.keyStorage.findTrusted(BigInteger(first)) } ?: Main.keyStorage.trusted().singleOrNull { it.readableName.equals(certname, true) }

        cert ?: run {
            player.sendMessage("[scarlet]Couldn't find a certificate called or aliased to '$certname'")
            return
        }

        if (cert == Main.keyStorage.cert()) {
            player.sendMessage("[scarlet]Can't establish a connection to yourself")
            return
        }

        val preexistingConnection = Main.tlsPeers.singleOrNull { it.second.peer.expectedCert.encoded.contentEquals(cert.encoded) }

        if (preexistingConnection != null) {
            if (preexistingConnection.second.peer.handshakeDone) {
                onFinish(preexistingConnection.first, cert)
            } else {
                player.sendMessage("[scarlet]Handshake is not completed!")
            }
        } else {
            player.sendMessage("[accent]Sending TLS request...")
            Main.connectTls(cert, {
                player.sendMessage("[accent]Connected!")
                // delayed to make sure receiving end is ready
                Timer.schedule({ onFinish(it, cert) }, .1F)
            }, { player.sendMessage("[scarlet]Make sure a processor/message block is set up for communication!") })
        }
    }

    /** Registers a command.
     *
     * @param format The format of the command, basically name and parameters together. Example:
     *      "help [page]"
     * @param description The description of the command.
     * @param runner A lambda to run when the command is invoked.
     */
    fun register(format: String, description: String = "", runner: (args: Array<String>, player: Player) -> Unit) {
        val args = if (format.contains(' ')) format.substringAfter(' ') else ""
        clientCommandHandler.register(format.substringBefore(' '), args, description, runner)
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

    var target: Teamc? = null
    var hadTarget = false
    fun autoShoot() {
        if (!Core.settings.getBool("autotarget") || state.isMenu || state.isEditor) return
        val unit = player.unit()
        if (((unit as? BlockUnitUnit)?.tile() as? ControlBlock)?.shouldAutoTarget() == false) return
        if (unit.activelyBuilding()) return
        val type = unit.type ?: return
        val targetBuild = target as? Building
        val validHealTarget = player.unit().type.canHeal && targetBuild?.isValid == true && target?.team() == unit.team && targetBuild.damaged() && target?.within(unit, unit.range()) == true

        if ((hadTarget && target == null || target != null && Units.invalidateTarget(target, unit, unit.range())) && !validHealTarget) { // Invalidate target
            val desktopInput = control.input as? DesktopInput
            player.shooting = Core.input.keyDown(Binding.select) && !Core.scene.hasMouse() && (desktopInput == null || desktopInput.shouldShoot)
            target = null
            hadTarget = false
        }

        if (target == null || timer.get(2, 6f)) { // Acquire target
            target = Units.closestEnemy(unit.team, unit.x, unit.y, unit.range()) { u -> u.checkTarget(unit.type.targetAir, unit.type.targetGround) }
            if (unit.type.canHeal && target == null) {
                target = Units.findDamagedTile(player.team(), player.x, player.y)
                if (target != null && !unit.within(target, if (type.hasWeapons()) unit.range() else 0f)) target = null
            }
            if (target == null && flood()) { // Shoot buildings in flood because why not.
                target = Units.findEnemyTile(player.team(), player.x, player.y, unit.range()) { type.targetGround }
            }
            if (!flood() && (unit as? BlockUnitc)?.tile()?.block == Blocks.foreshadow) {
                val amount = unit.range() * 2 + 1
                var closestScore = Float.POSITIVE_INFINITY

                circle(player.tileX(), player.tileY(), unit.range()) { tile ->
                    tile ?: return@circle
                    if (!tile.team().isEnemy(player.team())) return@circle
                    val block = tile.block()
                    val scoreMul = when {
                        // do NOT shoot power voided networks
                        (tile.build?.power?.graph?.getPowerBalance() ?: 0f) <= -1e12f -> Float.POSITIVE_INFINITY
                        // otherwise nodes are good to shoot
                        block == Blocks.powerSource -> 0f
                        block is PowerNode -> if (tile.build.power.status < .9) 2f else 1f

                        block == Blocks.itemSource -> 2f
                        block == Blocks.liquidSource -> 3f  // lower priority because things generally don't need liquid to run

                        // likely to be touching a turret or something
                        block == Blocks.unloader -> 4f
                        block == Blocks.router -> 4f
                        block == Blocks.overflowGate -> 4f
                        block == Blocks.underflowGate -> 4f
                        block == Blocks.sorter -> 4f
                        block == Blocks.invertedSorter -> 4f

                        block == Blocks.liquidRouter -> 5f

                        block == Blocks.mendProjector -> 6f
                        block == Blocks.forceProjector -> 6f
                        block is PointDefenseTurret -> 6f

                        block is BaseTurret -> 7f

                        block != Blocks.air -> 9f
                        else -> 10f
                    }

                    var score = Astar.manhattan.cost(tile.x.toInt(), tile.y.toInt(), player.tileX(), player.tileY())
                    score += scoreMul * amount * if (tile.build?.proximity?.contains { it is BaseTurretBuild } == true) 1F else 1.1F

                    if (score < closestScore) {
                        target = tile.build
                        closestScore = score
                    }
                }
            }
        }

        if (target != null) { // Shoot at target
            val intercept = Predict.intercept(unit, target, if (type.hasWeapons()) type.weapons.first().bullet.speed else 0f)
            val boosting = unit is Mechc && unit.isFlying()

            player.mouseX = intercept.x
            player.mouseY = intercept.y
            player.shooting = !boosting

            if (type.omniMovement && player.shooting && type.hasWeapons() && type.faceTarget && !boosting && type.rotateShooting) { // Rotate towards enemy
                unit.lookAt(unit.angleTo(player.mouseX, player.mouseY))
            }

            unit.aim(player.mouseX, player.mouseY)
            hadTarget = true
        }
    }
}
