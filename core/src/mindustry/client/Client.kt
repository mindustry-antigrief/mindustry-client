package mindustry.client

import arc.*
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
import mindustry.client.ClientVars.*
import mindustry.client.Spectate.spectate
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.communication.Packets
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.*
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
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.power.*
import mindustry.world.blocks.units.*
import org.bouncycastle.jce.provider.*
import org.bouncycastle.jsse.provider.*
import java.math.*
import java.security.*
import java.security.cert.*
import kotlin.math.*
import kotlin.random.*


object Client {
    var leaves: Moderation? = Moderation()
    val tiles = mutableListOf<Tile>()
    val timer = Interval(2)
    var spawnTime = 60f * Core.settings.getInt("spawntime")
    var travelTime = Core.settings.getInt("traveltime").toFloat()

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
        Navigation.update()
        PowerInfo.update()
        Spectate.update() // FINISHME: Why is spectate its own class? Move it here, no method is needed just add an `if` like below

        if (!configs.isEmpty) {
            try {
                if (configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
                    configs.removeLast().run()
                }
            } catch (e: Exception) {
                Log.err(e)
            }
        }
    }

    fun draw() {
        // Spawn path
        if (spawnTime < 0 && spawner.spawns.size < 50) { // FINISHME: Repetitive code, squash down
            for (i in 0 until spawner.spawns.size) {
                if (i >= tiles.size) tiles.add(spawner.spawns[i])
                Lines.beginLine()
                Draw.color(state.rules.waveTeam.color)
                var target: Tile? = null
                tiles[i] = spawner.spawns[i]
                while (target != tiles[i]) {
                    if (target != null) tiles[i] = target
                    target = pathfinder.getTargetTile(tiles[i], pathfinder.getField(state.rules.waveTeam, Pathfinder.costGround, Pathfinder.fieldCore))
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
        if (showingTurrets) {
            Draw.z(Layer.space)
            val units = Core.settings.getBool("unitranges")
            for (t in obstacles) {
                if (!t.canShoot || !(t.turret || units) || !bounds.overlaps(t.x - t.radius, t.y - t.radius, t.radius * 2, t.radius * 2)) continue
                Drawf.dashCircle(t.x, t.y, t.radius - tilesize, if (t.canHitPlayer) t.team.color else Team.derelict.color)
            }
        }

        // Player controlled turret range
        if ((player.unit() as? BlockUnitUnit)?.tile() is BaseTurret.BaseTurretBuild) {
            Drawf.dashCircle(player.x, player.y, player.unit().range(), player.team().color)
        }
    }

    private fun registerCommands() {
        register("help [page]", Core.bundle.get("client.command.help.description")) { args, player ->
            if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
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
            result.append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", page + 1, pages))
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(clientCommandHandler.commandList.size)) {
                val command = clientCommandHandler.commandList[i]
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText)
                    .append("[lightgray] - ").append(command.description).append("\n")
            }
            player.sendMessage(result.toString())
        }

        register("unit <unit-type>", Core.bundle.get("client.command.unit.description")) { args, _ ->
            ui.unitPicker.findUnit(content.units().min { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) })
        }

        register("count <unit-type>", Core.bundle.get("client.command.count.description")) { args, player ->
            val type = content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) }
            val counts = intArrayOf(0, 0, Units.getCap(player.team()), 0, 0, 0, 0, 0)

            for (unit in player.team().data().units) {
                if (unit.type != type) continue

                when (unit.sense(LAccess.controlled).toInt()) {
                    GlobalConstants.ctrlPlayer -> counts[5]++
                    GlobalConstants.ctrlFormation -> counts[6]++
                    GlobalConstants.ctrlProcessor -> counts[7]++
                    else -> counts[1]++
                }
                counts[0]++
                if (unit.flag != 0.0) counts[3]++
                else counts[4]++
            }

            player.sendMessage("""
                [accent]${type.localizedName}:
                Total: ${counts[0]}
                Free: ${counts[1]}
                Cap: ${counts[2]}
                Flagged(Unflagged): ${counts[3]}(${counts[4]})
                Players(Formation): ${counts[5]}(${counts[6]})
                Logic Controlled: ${counts[7]}""".trimIndent())
        }

        // FINISHME: Add spawn command

        register("go [x] [y]", Core.bundle.get("client.command.go.description")) { args, player ->
            try {
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
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
            Call.sendChatMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", player.tileX(), player.tileY()))
        }

        register("cursor [message...]", Core.bundle.get("client.command.cursor.description")) { args, _ ->
            Call.sendChatMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", control.input.rawTileX(), control.input.rawTileY()))
        }

        register("builder [options...]", Core.bundle.get("client.command.builder.description")) { args, _: Player ->
            follow(BuildPath(if (args.isEmpty()) "" else args[0]))
        } // FINISHME: This is so scuffed lol

        register("miner [options...]", Core.bundle.get("client.command.miner.description")) { args, _: Player ->
            follow(MinePath(if (args.isEmpty()) "" else args[0]))
        } // FINISHME: This is so scuffed lol

        register(" [message...]", Core.bundle.get("client.command.!.description")) { args, _ ->
            Call.sendChatMessage("!" + if (args.size == 1) args[0] else "")
        }

        register("shrug [message...]", Core.bundle.get("client.command.shrug.description")) { args, _ ->
            Call.sendChatMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "")
        }

        register("login [name] [pw]", Core.bundle.get("client.command.login.description")) { args, _ ->
            if (args.size == 2) Core.settings.put("cnpw", args[0] + " " + args[1])
            else Call.sendChatMessage("/login " + Core.settings.getString("cnpw", ""))
        }

        register("marker <name> [x] [y]", Core.bundle.get("client.command.marker.description")) { args, player ->
            val x = if (args.size == 3) args[1].toIntOrNull() ?: player.tileX() else player.tileX()
            val y = if (args.size == 3) args[2].toIntOrNull() ?: player.tileY() else player.tileY()
            val color = Color.HSVtoRGB(Random.nextFloat() * 360, 75f, 75f)
            Markers.add(Markers.Marker(x, y, args[0], color))
            player.sendMessage(Core.bundle.format("client.command.marker.added", x, y))
        }

        register("js <code...>", Core.bundle.get("client.command.js.description")) { args, player: Player ->
            player.sendMessage("[accent]" + mods.scripts.runConsole(args[0]))
        }

        register("/js <code...>", Core.bundle.get("client.command.serverjs.description")) { args, player ->
            player.sendMessage("[accent]" + mods.scripts.runConsole(args[0]))
            Call.sendChatMessage("/js " + args[0])
        }

        register("cc [setting]", Core.bundle.get("client.command.cc.description")) { args, player ->
            if (args.size != 1 || !args[0].matches("(?i)^[ari].*".toRegex())) {
                player.sendMessage(Core.bundle.format("client.command.cc.invalid", player.team().data().command.localized()))
                return@register
            }

            val cc = Units.findAllyTile(player.team(), player.x, player.y, Float.MAX_VALUE / 2) { t -> t is CommandCenter.CommandBuild }
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
            val build = MessageBlockCommunicationSystem.findProcessor() ?: MessageBlockCommunicationSystem.findMessage()
            if (build == null) player.sendMessage("[scarlet]No valid processor or message block found; communication system inactive.")
            else player.sendMessage("[accent]${build.block.localizedName} at (${build.tileX()}, ${build.tileY()}) in use for communication.")
        }

        register("fixpower [c]", Core.bundle.get("client.command.fixpower.description")) { args, player ->
            clientThread.taskQueue.post {
                val confirmed = args.any() && args[0] == "c" // Don't configure by default
                val inProgress = !configs.isEmpty
                var n = 0
                val grids = mutableMapOf<Int, MutableSet<Int>>()
                for (grid in PowerGraph.activeGraphs.filter { g -> g.team == player.team() }) {
                    for (nodeBuild in grid.all) {
                        val nodeBlock = nodeBuild.block as? PowerNode ?: continue
                        var links = nodeBuild.power.links.size
                        nodeBlock.getPotentialLinks(nodeBuild.tile, player.team()) { link ->
                            if (PowerDiode.connected.any { it.first == min(grid.id, link.power.graph.id) && it.second == max(grid.id, link.power.graph.id) }) return@getPotentialLinks // Don't connect across diodes
                            if (++links > nodeBlock.maxNodes) return@getPotentialLinks // Respect max links
                            val t = grids.getOrPut(grid.id) { mutableSetOf(grid.id) }
                            val l = grids.getOrDefault(link.power.graph.id, mutableSetOf())
                            if (l.add(grid.id) && t.add(link.power.graph.id)) {
                                l.addAll(t)
                                grids[link.power.graph.id] = l
                                if (confirmed && !inProgress) configs.add(ConfigRequest(nodeBuild.tileX(), nodeBuild.tileY(), link.pos()))
                                n++
                            }
                        }
                    }
                }
                if (confirmed) {
                    if (inProgress) player.sendMessage("The config queue isn't empty, there are ${configs.size} configs queued, there are $n nodes to connect.") // FINISHME: Bundle
                    else player.sendMessage(Core.bundle.format("client.command.fixpower.success", n))
                } else {
                    player.sendMessage(Core.bundle.format("client.command.fixpower.confirm", n, PowerGraph.activeGraphs.size))
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
                "l", "leaves" -> if (leaves != null) leaves!!.leftList() else player.sendMessage("[scarlet]Leave logs are disabled")
                else -> player.sendMessage("[scarlet]Invalid option specified, options are:\nSettings, Leaves")
            }
        }

        register("clearghosts [c]", "Removes the ghosts of blocks which are in range of enemy turrets, useful to stop polys from building forever") { args, player -> // FINISHME: Bundle
            clientThread.taskQueue.post {
                val confirmed = args.any() && args[0].startsWith("c") // Don't clear by default
                val all = confirmed && Main.keyStorage.builtInCerts.contains(Main.keyStorage.cert()) && args[0] == "clear"
                val blocked = GridBits(world.width(), world.height())

                synchronized(obstacles) {
                    for (turret in obstacles) {
                        if (!turret.turret) continue
                        val lowerXBound = ((turret.x - turret.radius) / tilesize).toInt()
                        val upperXBound = ((turret.x + turret.radius) / tilesize).toInt()
                        val lowerYBound = ((turret.y - turret.radius) / tilesize).toInt()
                        val upperYBound = ((turret.y + turret.radius) / tilesize).toInt()
                        for (x in lowerXBound..upperXBound) {
                            for (y in lowerYBound..upperYBound) {
                                if (Structs.inBounds(x, y, world.width(), world.height()) && turret.contains(x * tilesize.toFloat(), y * tilesize.toFloat())) {
                                    blocked.set(x, y)
                                }
                            }
                        }
                    }
                }
                var n = 0
                do {
                    val plans = IntSeq()
                    var i = 0
                    for (plan in Vars.player.team().data().blocks) {
                        var isBlocked = false
                        world.tile(plan.x.toInt(), plan.y.toInt()).getLinkedTilesAs(content.block(plan.block.toInt())) { t ->
                            if (blocked.get(t.x.toInt(), t.y.toInt())) isBlocked = true
                        }
                        if (!isBlocked && !all) continue
                        if (++i > 100) break

                        plans.add(Point2.pack(plan.x.toInt(), plan.y.toInt()))
                    }
                    n += plans.size
                    if (confirmed) Call.deletePlans(player, plans.toArray())
                } while (i > 100)

                if (confirmed) player.sendMessage("[accent]Removed $n plans, ${Vars.player.team().data().blocks.size} remain")
                else player.sendMessage("[accent]Found $n (out of ${Vars.player.team().data().blocks.size}) block ghosts within turret range, run [coral]!clearghosts c[] to remove them")
            }
        }

        register("removelast [count]", "Horrible and inefficient command to remove the x oldest tile logs") { args, _ -> // FINISHME: Bundle
            clientThread.taskQueue.post {
                val count = if (args.isEmpty()) 1 else args[0].toInt()
                lateinit var record: TileRecord
                lateinit var sequence: TileLogSequence
                lateinit var log: TileLog
                for (i in 1..count) {
                    val logs = mutableMapOf<TileLogSequence, Long>()
                    world.tiles.eachTile { t ->
                        record = TileRecords[t] ?: return@eachTile
                        sequence = record.oldestSequence() ?: return@eachTile
                        log = record.oldestLog(sequence) ?: return@eachTile

                        logs[sequence] = log.id
                    }
                    logs.minByOrNull { it.value }?.key?.logs?.removeAt(0) ?: return@post
                }
                player.sendMessage("done")
            }
        }

        register("e <certname> <message...>", "Sends an encrypted message over TLS.") { args, _ -> // FINISHME: Bundle
            val certname = args[0]
            val msg = args[1]

            connectTls(certname) { comms, cert ->
                comms.send(MessageTransmission(msg))
                ui.chatfrag.addMessage(msg, "[coral]" + (Main.keyStorage.cert()?.readableName ?: "you") + "[white] -> [white]" + Main.keyStorage.aliasOrName(cert), encrypted)
                lastCertName = cert.readableName
            }
        }

        register("togglesign", Core.bundle.get("client.command.togglesign.description")) { _, player ->
            val previous = Core.settings.getBool("signmessages")
            Core.settings.put("signmessages", !previous)
            player.sendMessage(Core.bundle.format("client.command.togglesign.success", Core.bundle.get(if (previous) "off" else "on").lowercase()))
        }

        register("stoppathing <name>", "Stop someone from pathfinding.") { args, _ -> // FINISHME: Bundle
            val certname = args[0]

            connectTls(certname) { comms, _ ->
                comms.send(CommandTransmission(CommandTransmission.Commands.STOP_PATH))
            }
        }
    }

    fun connectTls(certname: String, onFinish: (Packets.CommunicationClient, X509Certificate) -> Unit) { // FINISHME: Bundle
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
}
