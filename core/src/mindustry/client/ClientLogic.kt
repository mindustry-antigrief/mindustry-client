package mindustry.client

import arc.*
import arc.Core.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.stopFollowing
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.core.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.input.*
import mindustry.logic.*
import mindustry.net.*
import mindustry.type.*
import mindustry.ui.fragments.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.power.*
import mindustry.world.blocks.sandbox.*
import kotlin.random.*

/** WIP client logic class, similar to [Logic] but for the client.
 * Handles various events and such.
 * FINISHME: Move the 9000 different bits of code throughout the client to here. Update: this was an awful idea lmao */
class ClientLogic {
    private var turretVoidWarnMsg: ChatFragment.ChatMessage? = null
    private var turretVoidWarnCount = 0
    private var turretVoidWarnPlayer: Player? = null
    private var lastTurretVoidWarn = 0L

    /** Create event listeners */
    init {
        Events.on(ServerJoinEvent::class.java) { // Run just after the player joins a server
            Spectate.pos = null

            Timer.schedule({
                app.post {
                    val arg = switchTo?.removeFirstOrNull()
                    if (arg != null && arg is Host) {
                        when (val vote = settings.getInt("automapvote")) {
                            1, 2, 3 -> Server.current.mapVote(vote - 1)
                            4 -> Server.current.mapVote(Random.nextInt(0..2))
                        }
                        NetClient.connect(arg.address, arg.port)
                    } else if (arg is UnitType) {
                        ui.unitPicker.pickUnit(arg)
                        switchTo = null
                    }
                    executeOrSendText(settings.getString("gamejointext"))
                }
            }, .1F)

            if (settings.getBool("onjoinfixcode")) { // FINISHME: Make this also work for singleplayer worlds
                ProcessorPatcher.fixCode(ProcessorPatcher.FixCodeMode.Fix)
            }

            Seer.players.clear()
            Groups.player.each(Seer::registerPlayer)
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            lastJoinTime = Time.millis()
            if (!syncing) {
                AutoTransfer.enabled = settings.getBool("autotransfer") && !(state.rules.pvp && Server.io())
                frozenPlans.clear()
                app.post {
                when (val vote = settings.getInt("automapvote")) {
                    1, 2, 3 -> Server.current.mapVote(vote - 1)
                    4 -> Server.current.mapVote(Random.nextInt(0..2))
                    }
                }
                hidingAirUnits = false
                showingTurrets = false
                showingAllyTurrets = false
                showingInvTurrets = false
            }
            configs.clear()
            control.input.lastVirusWarning = null
            control.input.followGameEndPan = true
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            if (state.rules.pvp && !isDeveloper()) ui.announce("@client.nopvp", 5f)
            if (!state.rules.schematicsAllowed && !syncing) ui.announce("@client.schematicsdisabled", 5f)
            overdrives.clear()
            massDrivers.clear()
            payloadMassDrivers.clear()
            Client.tiles.clear()
            Client.tilesNaval.clear()
            Client.tilesFlying.clear()
        }

        Events.on(MenuReturnEvent::class.java) { // Run when returning to the title screen
            stopFollowing()
            syncing = false // Never syncing when not connected
            ui.join.lastHost = null // Not needed unless connected
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading FINISHME: Look into optimizing this, it takes half of the entire ClientLoadEvent, ~250ms on my machine
            handleMenuTasksAsync()
            clientThread.post {
                val changeHash = files.internal("changelog").readString().replace("\r\n", "\n").hashCode() // Display changelog if the file contents have changed as well as on first run
                Core.app.post {
                    if (settings.getInt("changeHash") != changeHash) {
                        ChangelogDialog.show()
                        settings.put("changeHash", changeHash)
                    }
                }
            }

            if (settings.getBool("discordrpc")) platform.startDiscord()
            if (settings.getBool("mobileui") && !OS.hasProp("nomobileui")) mobile = !mobile
            if (settings.getBool("viruswarnings")) LExecutor.virusWarnings = true
            UnitType.drawAllItems = settings.getBool("drawallitems")
            UnitType.formationAlpha = settings.getInt("formationopacity") / 100f
            UnitType.hitboxAlpha = settings.getInt("hitboxopacity") / 100f

            Autocomplete.autocompleters.add(BlockEmotes(), PlayerCompletion(), CommandCompletion())
            Autocomplete.initialize()

            Navigation.navigator.init()

            Migrations().runMigrations()
        }

        Events.on(PlayerJoin::class.java) { e -> // Run when a player joins the server
            if (e.player == null) return@on

            if (settings.getBool("clientjoinleave")) {
                if (!Server.io()) {
                    val target = "${Strings.stripColors(e.player.name)} has connected."
                    val searchTime = Time.millis() - 10000;
                    val msgs = ui.chatfrag.messages
                    var found: ChatFragment.ChatMessage? = null
                    for (i in 0..<msgs.size) {
                        if (Strings.stripColors(msgs[i].message) == target) {
                            found = msgs[i]
                            break
                        }
                        if (msgs[i].receivedAt < searchTime) break
                    }
                    if (found == null) {
                        found = ui.chatfrag.addMsg(bundle.format("client.connected", e.player.name))
                    }
                    if (found.buttons != null && found.buttons.isEmpty) {
                        found.addButton(e.player.name) { Spectate.spectate(e.player) }
                    }
                } else {
                    // io has custom player join format
                    val target = e.player.coloredName()
                    val searchTime = Time.millis() - 10000;
                    val msgs = ui.chatfrag.messages
                    for (i in 0..<msgs.size) {
                        val msg = msgs[i].message
                        if (
                            msg.startsWith(target) && // Start with player's colored name
                            Strings.stripColors(msg.substring(target.length)).let { // after that, should follow a template (minus color tags)
                            it.length - 6 == " has connected - ".length && it.startsWith(" has connected - ") // and then a 6-letter id
                        }) {
                            if (msgs[i].buttons != null) {
                                msgs[i].addButton(target) { Spectate.spectate(e.player) }
                            }
                            break
                        }
                        if (msgs[i].receivedAt < searchTime) break
                    }
                }
            }
        }

        Events.on(PlayerLeave::class.java) { e -> // Run when a player leaves the server
            if (e.player == null) return@on

            val id = e.player.id.toString()

            if (settings.getBool("clientjoinleave") && !Server.io() && (ui.chatfrag.messages.isEmpty || !Strings.stripColors(ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has disconnected.")))
                ui.chatfrag.addMsg(bundle.format("client.disconnected", e.player.name)).addButton(e.player.name) { app.clipboardText = id } // FINISHME: Also apply to io leaves!

            if (settings.getBool("showidinjoinleave", false))
                ui.chatfrag.addMsg(bundle.format("client.disconnected.withid", id))
                    .addButton(id) { app.clipboardText = "!undo $id" }
        }

        Events.on(GameOverEventClient::class.java) {
            if (net.client()) {
                // Afk players will start mining at the end of a game (kind of annoying but worth it)
                if ((Navigation.currentlyFollowing as? BuildPath)?.mineItems == null && !CustomMode.defense()) {
                    (control.input as? DesktopInput)?.moved = false
                    Navigation.follow(MinePath(args = "copper lead beryllium graphite", newGame = true))
                }

                // Save maps on game over if the setting is enabled
                if (settings.getBool("savemaponend")) control.saves.addSave(state.map.name())
            }

            // FINISHME: Make this work in singleplayer
            if (it.winner == player.team()) {
                executeOrSendText(settings.getString("gamewintext"))
            } else {
                executeOrSendText(settings.getString("gamelosetext"))
            }
        }

        Events.on(BlockDestroyEvent::class.java) {
            if (it.tile.block() is PowerVoid) {
                NetClient.findCoords(ui.chatfrag.addMsg(bundle.format("client.voidwarn", it.tile.x.toString(), it.tile.y.toString()))) // FINISHME: Awful way to circumvent arc formatting numerics with commas at thousandth places
            }
        }

        // Warn about turrets that are built with an enemy void in range
        Events.on(BlockBuildBeginEventBefore::class.java) { event ->
            val block = event.newBlock
            if (!((block as? Turret)?.targetGround ?: false)) return@on
            if (event.unit?.player == null) return@on
            if (state.rules.infiniteResources) return@on

            clientThread.post { // Scanning through tiles can be exhaustive. Delegate it to the client thread.
                val voids = Seq<Building>()
                for (tile in world.tiles) if (tile.block() is PowerVoid) voids.add(tile.build)

                val void = voids.find { it.within(event.tile, block.range) && it.team != event.unit.team }
                if (void != null) { // Code taken from LogicBlock.LogicBuild.configure
                    app.post {
                        if (event.unit.player != turretVoidWarnPlayer || Time.timeSinceMillis(lastTurretVoidWarn) > 5e3) {
                            turretVoidWarnPlayer = event.unit.player
                            turretVoidWarnCount = 1
                            val message = bundle.format("client.turretvoidwarn", getName(event.unit), event.tile.x.toString(), event.tile.y.toString(), void.tileX().toString(), void.tileY().toString()) // FINISHME: Awful way to circumvent arc formatting numerics with commas at thousandth places
                            turretVoidWarnMsg = ui.chatfrag.addMsg(message)
                            NetClient.findCoords(turretVoidWarnMsg)
                        } else {
                            ui.chatfrag.messages.remove(turretVoidWarnMsg)
                            ui.chatfrag.messages.insert(0, turretVoidWarnMsg)
                            ui.chatfrag.doFade(6f) // Reset fading
                            turretVoidWarnMsg!!.prefix = "[scarlet](x${++turretVoidWarnCount}) "
                            turretVoidWarnMsg!!.format()
                        }
                        lastTurretVoidWarn = Time.millis()
                    }
                }
            }
        }

        Events.on(ConfigEvent::class.java) { event ->
            @Suppress("unchecked_cast")
            if (event.player != null && event.player != player && settings.getBool("powersplitwarnings") && event.tile is PowerNode.PowerNodeBuild) {
                val prev = Seq(event.previous as Array<Point2>)
                val count = if (event.value is Int) { // FINISHME: Awful
                    if (prev.contains(Point2.unpack(event.value).sub(event.tile.tileX(), event.tile.tileY()))) 1 else 0
                } else {
                    prev.count { !((event.value as? Array<Point2>)?.contains(it)?: true) }
                }
                if (count == 0) return@on // No need to warn
                event.tile.disconnections += count

                val message: String = bundle.format("client.powerwarn", Strings.stripColors(event.player.name), event.tile.disconnections, event.tile.tileX().toString(), event.tile.tileY().toString()) // FINISHME: Awful way to circumvent arc formatting numerics with commas at thousandth places
                lastWarnPos.set(event.tile.tileX().toFloat(), event.tile.tileY().toFloat())
                if (event.tile.message == null || ui.chatfrag.messages.indexOf(event.tile.message) > 8) {
                    event.tile.disconnections = count
                    event.tile.message = ui.chatfrag.addMsg(message)
                    NetClient.findCoords(event.tile.message)
                } else {
                    ui.chatfrag.doFade(2f)
                    event.tile.message!!.message = message
                    event.tile.message!!.format()
                }
            }
        }

        Events.run(Trigger.draw) {
            camera.bounds(cameraBounds)
            cameraBounds.grow(2 * tilesizeF)
        }

//        Events.on(ResetEvent::class.java) { FINISHME v8: Get this working again, it saves a tiny amount of ram after a large map but it may as well get fixed since its so trivial
//            (batch as? SortedSpriteBatch)?.reset()
//        }
    }

    private fun executeOrSendText(text: String?){
        if(text?.isBlank() != false) return
        val input = text.split("(?<!\\\\);".toRegex())
        val out = StringBuilder()
        Log.debug("The split input: $input")
        input.forEach { // Try running each bit as a command, this code is terribly inefficient but should get the job done so I don't care
            val clean = (if (!it.endsWith("\\\\;")) it.removeSuffix(";") else it) // Remove the ending semicolon on each command
                    .replace("\\\\;", ";") // Replace \\; with ;
            Log.debug("Input part: '$it' ('$clean')")
            val res = ChatFragment.handleClientCommand(clean, false)
            if (res.type == CommandHandler.ResponseType.noCommand) out.append(clean) // If the command doesn't exist we pass the text through to the output, otherwise we don't pass it to the output. this is kind of hacky but i don't care.
        }
        Log.debug("Sent message: $out")
        if (out.isNotBlank()) Call.sendChatMessage(out.toString())
    }
}
