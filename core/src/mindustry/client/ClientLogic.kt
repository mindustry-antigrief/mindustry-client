package mindustry.client

import arc.*
import arc.Core.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.navigation.Navigation.stopFollowing
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.core.*
import mindustry.game.EventType.*
import mindustry.gen.*
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
                    if (arg != null) {
                        if (arg is Host) {
                            NetClient.connect(arg.address, arg.port)
                        } else {
                            if (arg is UnitType) ui.unitPicker.pickUnit(arg)
                            switchTo = null
                        }
                        // Game join text after hh
                        if (settings.getString("gamejointext")?.isNotBlank() == true) {
                            Call.sendChatMessage(settings.getString("gamejointext"))
                        }

                        when (val vote = settings.getInt("automapvote")) {
                            1, 2, 3 -> Server.current.mapVote(vote - 1)
                            4 -> Server.current.mapVote(Random.nextInt(0..2))
                        }
                    }
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
            if (!syncing) {
                AutoTransfer.enabled = settings.getBool("autotransfer") && !(state.rules.pvp && Server.io())
                Player.persistPlans.clear()
                frozenPlans.clear()
            }
            lastJoinTime = Time.millis()
            configs.clear()
            control.input.lastVirusWarning = null
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            hidingAirUnits = false
            showingTurrets = false
            showingAllyTurrets = false
            showingInvTurrets = false
            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
            overdrives.clear()
            massDrivers.clear()
            Client.tiles.clear()
        }

        Events.on(MenuReturnEvent::class.java) { // Run when returning to the title screen
            stopFollowing()
            syncing = false // Never syncing when not connected
            ui.join.lastHost = null // Not needed unless connected
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading
            app.post { // Run next frame as Vars.clientLoaded is true then and the load methods depend on it
                Musics.load() // Loading music isn't very important
                Sounds.load() // Same applies to sounds
            }

            val changeHash = files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            settings.put("changeHash", changeHash)

            if (settings.getBool("discordrpc")) platform.startDiscord()
            if (settings.getBool("mobileui")) mobile = !mobile
            if (settings.getBool("viruswarnings")) LExecutor.virusWarnings = true

            Autocomplete.autocompleters.add(BlockEmotes(), PlayerCompletion(), CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()

            // Hitbox setting was changed, this updates it. FINISHME: Remove a while after v7 release.
            if (settings.getBool("drawhitboxes") && settings.getInt("hitboxopacity") == 0) { // Old setting was enabled and new opacity hasn't been set yet
                settings.put("hitboxopacity", 30)
                UnitType.hitboxAlpha = settings.getInt("hitboxopacity") / 100f
            }

            // FINISHME: Remove these at some point
            settings.remove("drawhitboxes") // Don't need this old setting anymore
            settings.remove("signmessages") // same as above FINISHME: Remove this at some point
            settings.remove("firescl") // firescl, effectscl and cmdwarn were added in sept 2022, remove them in mid 2023 or something
            settings.remove("effectscl")
            settings.remove("commandwarnings")
	        settings.remove("nodeconfigs")
            settings.remove("attemwarfarewhisper")
            if (settings.has("gameovertext")) {
                if (settings.getString("gameovertext").isNotBlank()) settings.put("gamewintext", settings.getString("gameovertext"))
                settings.remove("gameovertext")
            }

            if (isDeveloper()) {
                register("update <name/id...>") { args, _ ->
                    val name = args.joinToString(" ")
                    val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { BiasedLevenshtein.biasedLevenshteinInsensitive(Strings.stripColors(it.name), name) }!!
                    Main.send(CommandTransmission(CommandTransmission.Commands.UPDATE, Main.keyStorage.cert() ?: return@register, player))
                }
            }
        }

        Events.on(PlayerJoin::class.java) { e -> // Run when a player joins the server
            if (e.player == null) return@on

            if (settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !Strings.stripColors(ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has connected.")) && Time.timeSinceMillis(lastJoinTime) > 10000)
                player.sendMessage(bundle.format("client.connected", e.player.name))
        }

        Events.on(PlayerLeave::class.java) { e -> // Run when a player leaves the server
            if (e.player == null) return@on

            if (settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !Strings.stripColors(ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has disconnected.")))
                player.sendMessage(bundle.format("client.disconnected", e.player.name))
        }

        Events.on(GameOverEventClient::class.java) {
            if (net.client()) {
                // Afk players will start mining at the end of a game (kind of annoying but worth it)
                if (!Navigation.isFollowing || (Navigation.currentlyFollowing as? BuildPath)?.mineItems != null) Navigation.follow(MinePath(UnitTypes.gamma.mineItems, newGame = true))

                // Save maps on game over if the setting is enabled
                if (settings.getBool("savemaponend")) control.saves.addSave(state.map.name())
            }

            // TODO: Make this work in singleplayer
            if (it.winner == player.team()) {
                if (settings.getString("gamewintext")?.isNotBlank() == true) Call.sendChatMessage(settings.getString("gamewintext"))
            } else {
                if (settings.getString("gamelosetext")?.isNotBlank() == true) Call.sendChatMessage(settings.getString("gamelosetext"))
            }
        }

        Events.on(BlockDestroyEvent::class.java) {
            if (it.tile.block() is PowerVoid) {
                val message = bundle.format("client.voidwarn", it.tile.x.toString(), it.tile.y.toString())
                NetClient.findCoords(ui.chatfrag.addMessage(message, null, null, "", message)) // FINISHME: Awful way to circumvent arc formatting numerics with commas at thousandth places
            }
        }

        // Warn about turrets that are built with an enemy void in range
        Events.on(BlockBuildBeginEventBefore::class.java) { event ->
            val block = event.newBlock
            if (block !is Turret) return@on
            if (event.unit?.player == null) return@on

            clientThread.post { // Scanning through tiles can be exhaustive. Delegate it to the client thread.
                val voids = Seq<Building>()
                for (tile in world.tiles) if (tile.block() is PowerVoid) voids.add(tile.build)

                val void = voids.find { it.within(event.tile, block.range) }
                if (void != null) { // Code taken from LogicBlock.LogicBuild.configure
                    app.post {
                        if (event.unit.player != turretVoidWarnPlayer || Time.timeSinceMillis(lastTurretVoidWarn) > 5e3) {
                            turretVoidWarnPlayer = event.unit.player
                            turretVoidWarnCount = 1
                            val message = bundle.format("client.turretvoidwarn", getName(event.unit),
                                event.tile.x.toString(), event.tile.y.toString(), void.tileX().toString(), void.tileY().toString() // FINISHME: Awful way to circumvent arc formatting numerics with commas at thousandth places
                            )
                            turretVoidWarnMsg = ui.chatfrag.addMessage(message , null, null, "", message)
                            NetClient.findCoords(turretVoidWarnMsg)
                        } else {
                            ui.chatfrag.messages.remove(turretVoidWarnMsg)
                            ui.chatfrag.messages.insert(0, turretVoidWarnMsg)
                            ui.chatfrag.doFade(6f); // Reset fading
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
                lastCorePos.set(event.tile.tileX().toFloat(), event.tile.tileY().toFloat())
                if (event.tile.message == null || ui.chatfrag.messages.indexOf(event.tile.message) > 8) {
                    event.tile.disconnections = count
                    event.tile.message = ui.chatfrag.addMessage(message, null, null, "", message)
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
    }
}
