package mindustry.client

import arc.Core
import arc.Events
import arc.struct.Seq
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import arc.util.Timer
import mindustry.Vars
import mindustry.Vars.ui
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.ConfigRequest
import mindustry.client.antigrief.Seer
import mindustry.client.communication.CommandTransmission
import mindustry.client.navigation.*
import mindustry.client.ui.ChangelogDialog
import mindustry.client.utils.*
import mindustry.content.UnitTypes
import mindustry.game.EventType.*
import mindustry.gen.Building
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.logic.LExecutor
import mindustry.type.UnitType
import mindustry.ui.fragments.ChatFragment
import mindustry.ui.fragments.ChatFragment.ChatMessage
import mindustry.world.blocks.defense.turrets.Turret
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.sandbox.PowerVoid
import mindustry.world.modules.ItemModule

/** WIP client logic class, similar to [mindustry.core.Logic] but for the client.
 * Handles various events and such.
 * FINISHME: Move the 9000 different bits of code throughout the client to here. Update: this was an awful idea lmao */
class ClientLogic {
    private var switchTo: MutableList<Any>? = null

    private var turretVoidWarnMsg: ChatMessage? = null
    private var turretVoidWarnCount = 0
    private var turretVoidWarnPlayer: Player? = null
    private var lastTurretVoidWarn = 0L

    /** Create event listeners */
    init {
        Events.on(ServerJoinEvent::class.java) { // Run just after the player joins a server
            if (Navigation.currentlyFollowing is AssistPath ||
                Navigation.currentlyFollowing is UnAssistPath ||
                Navigation.currentlyFollowing is WaypointPath<*>
            ) Navigation.stopFollowing()
            Spectate.pos = null

            Timer.schedule({
                Core.app.post {
                    val switchTo = switchTo
                    if (switchTo != null) {
                        Call.sendChatMessage("/novote") // Having it not be a random vote is better
                        if (switchTo.firstOrNull() is Char) Call.sendChatMessage("/switch ${switchTo.removeFirst()}")
                        else {
                            if (switchTo.firstOrNull() is UnitType) Vars.ui.unitPicker.pickUnit(switchTo.first() as UnitType)
                            this.switchTo = null
                        }
                    }

                    // If no hh then send gamejointext
                    else {
                        if (Core.settings.getString("gamejointext")?.isNotEmpty() == true) {
                            Call.sendChatMessage(Core.settings.getString("gamejointext"))
                        }

                        when (Core.settings.getInt("automapvote")) {
                            0 -> {}
                            1 -> Call.sendChatMessage("/downvote")
                            2 -> Call.sendChatMessage("/novote")
                            3 -> Call.sendChatMessage("/upvote")
                            4 -> Call.sendChatMessage(("/${arrayOf("no", "up", "down").random()}"))
                            else -> {}
                        }
                    }
                }
            }, .1F)

            if (Core.settings.getBool("onjoinfixcode")) { // TODO: Make this also work for singleplayer worlds
                Core.app.post {
                    val builds = Vars.player.team().data().buildings.filterIsInstance<LogicBlock.LogicBuild>() // Must be done on the main thread
                    clientThread.post {
                        val inProgress = !configs.isEmpty()
                        var n = 0
                        if (Core.settings.getBool("attemwarfare") && !inProgress) {
                            Log.debug("Patching!")
                            builds.forEach {
                                val patched = ProcessorPatcher.patch(it.code, if(Core.settings.getBool("removeatteminsteadoffixing")) "r" else "c")
                                if (patched != it.code) {
                                    Log.debug("${it.tileX()} ${it.tileY()}")
                                    configs.add(ConfigRequest(it.tileX(), it.tileY(),
                                        LogicBlock.compress(patched, it.relativeConnections())
                                    ))
                                    n++
                                }
                            }
                        }

                        Core.app.post {
                            if (Core.settings.getBool("attemwarfare")) {
                                if (inProgress) Vars.player.sendMessage("[scarlet]The config queue isn't empty, there are ${configs.size} configs queued, there are ${ProcessorPatcher.countProcessors(builds)} processors to reconfigure.") // FINISHME: Bundle
                                else Vars.player.sendMessage("[accent]Successfully reconfigured $n/${builds.size} processors")
                            } else {
                                Vars.player.sendMessage("[accent]Run [coral]!fixcode [c | r][] to reconfigure ${ProcessorPatcher.countProcessors(builds)}/${builds.size} processors")
                            }
                        }
                    }
                }
            }

            Seer.players.clear()
            Groups.player.each { Seer.registerPlayer(it) }
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            Core.app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            if (!syncing){
                AutoTransfer.enabled = Core.settings.getBool("autotransfer") && !(Vars.state.rules.pvp && io())
                Player.persistPlans.clear()
                Vars.frozenPlans.clear()
            }
            lastJoinTime = Time.millis()
            configs.clear()
            Vars.control.input.lastVirusWarning = null
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            hidingAirUnits = false
            showingTurrets = false
            showingAllyTurrets = false
            showingInvTurrets = false
//            if (Vars.state.rules.pvp) Vars.ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
            overdrives.clear()
            Client.tiles.clear()

            UnitTypes.horizon.itemCapacity = if (flood()) 20 else 0 // Horizons can pick up items in flood, this just allows the items to draw correctly
            UnitTypes.crawler.health = if (flood()) 100f else 200f // Crawler health is halved in flood

            if(coreItems == null) coreItems = ItemModule(true)
            else {
                coreItems.update(false)
                coreItems.clear()
            }
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading
            Core.app.post { // Run next frame as Vars.clientLoaded is true then and the load methods depend on it
                Musics.load() // Loading music isn't very important
                Sounds.load() // Same applies to sounds
            }

            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("discordrpc")) Vars.platform.startDiscord()
            if (Core.settings.getBool("mobileui")) Vars.mobile = !Vars.mobile
            if (Core.settings.getBool("viruswarnings")) LExecutor.virusWarnings = true

            Autocomplete.autocompleters.add(BlockEmotes(), PlayerCompletion(), CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()

            // Hitbox setting was changed, this updates it. FINISHME: Remove a while after v7 release.
            if (Core.settings.getBool("drawhitboxes") && Core.settings.getInt("hitboxopacity") == 0) { // Old setting was enabled and new opacity hasn't been set yet
                Core.settings.put("hitboxopacity", 30)
                UnitType.hitboxAlpha = Core.settings.getInt("hitboxopacity") / 100f
            }

            // FINISHME: Remove these at some point
            Core.settings.remove("drawhitboxes") // Don't need this old setting anymore
            Core.settings.remove("signmessages") // same as above FINISHME: Remove this at some point
            Core.settings.remove("firescl") // firescl, effectscl and cmdwarn were added in sept 2022, remove them in mid 2023 or something
            Core.settings.remove("effectscl")
            Core.settings.remove("commandwarnings")
	    Core.settings.remove("nodeconfigs")
            if (Core.settings.getString("gameovertext")?.isNotEmpty() == true) {
                Core.settings.put("gamewintext", Core.settings.getString("gameovertext"))
                Core.settings.remove("gameovertext")
            }

            // How about I enable it anyways :)
            // if (OS.hasProp("policone")) { // People spam these and its annoying. add some argument to make these harder to find
            register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
                sendMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
            }

            register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
                sendMessage("Silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance. They are not the same, please get it right!")
            }

            register("hh [h]", "!") { args, _ ->
                if (!Vars.net.client()) return@register
                val u = if (args.any()) Vars.content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.name) } else Vars.player.unit().type
                val current = (Vars.ui.join.lastHost?.modeName?.first() ?: Vars.ui.join.lastHost?.mode?.name?.get(0) ?: 'f').lowercaseChar()
                switchTo = mutableListOf<Any>('a', 'p', 's', 'f', 't').apply { remove(current); add(current); add(u) }
                Call.sendChatMessage("/switch ${switchTo!!.removeFirst()}")
            }
            //}

            val encoded = Main.keyStorage.cert()?.encoded
            if (encoded != null && Main.keyStorage.builtInCerts.any { it.encoded.contentEquals(encoded) }) {
                register("update <name/id...>") { args, _ ->
                    val name = args.joinToString(" ")
                    val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { BiasedLevenshtein.biasedLevenshteinInsensitive(Strings.stripColors(it.name), name) }!!
                    Main.send(CommandTransmission(CommandTransmission.Commands.UPDATE, Main.keyStorage.cert() ?: return@register, player))
                }
            }
        }

        Events.on(PlayerJoin::class.java) { e -> // Run when a player joins the server
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (Vars.ui.chatfrag.messages.isEmpty || !Strings.stripColors(Vars.ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has connected.")) && Time.timeSinceMillis(lastJoinTime) > 10000)
                Vars.player.sendMessage(Core.bundle.format("client.connected", e.player.name))
        }

        Events.on(PlayerLeave::class.java) { e -> // Run when a player leaves the server
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (Vars.ui.chatfrag.messages.isEmpty || !Strings.stripColors(Vars.ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has disconnected.")))
                Vars.player.sendMessage(Core.bundle.format("client.disconnected", e.player.name))
        }

        Events.on(GameOverEventClient::class.java) {
            if (!Navigation.isFollowing || (Navigation.currentlyFollowing as? BuildPath)?.mineItems != null) Navigation.follow(MinePath(UnitTypes.gamma.mineItems, newGame = true)) // Afk players will start mining at the end of a game (kind of annoying but worth it)

            // TODO: Make this work in singleplayer
            if (it.winner == Vars.player.team()) {
                if (Core.settings.getString("gamewintext")?.isNotEmpty() == true) Call.sendChatMessage(Core.settings.getString("gamewintext"))
            } else {
                if (Core.settings.getString("gamelosetext")?.isNotEmpty() == true) Call.sendChatMessage(Core.settings.getString("gamelosetext"))
            }
        }

        Events.on(BlockDestroyEvent::class.java) {
            if (it.tile.block() is PowerVoid) {
                ChatFragment.ChatMessage.msgFormat()
                ui.chatfrag.addMessage(Core.bundle.format("client.voidwarn", it.tile.x, it.tile.y))
            }
        }

        // Warn about turrets that are built with an enemy void in range
        Events.on(BlockBuildBeginEventBefore::class.java) { event ->
            val block = event.newBlock
            if (block !is Turret) return@on
            clientThread.post { // Scanning through tiles can be exhaustive. Delegate it to the client thread.
                val voids = Seq<Building>()
                for (tile in Vars.world.tiles) if (tile.block() is PowerVoid) voids.add(tile.build)

                val void = voids.find { it.within(event.tile, block.range) }
                if (void != null) { // Code taken from LogicBlock.LogicBuild.configure
                    Core.app.post {
                        ChatMessage.msgFormat()
                        if (event.unit?.player != turretVoidWarnPlayer || turretVoidWarnPlayer == null || Time.timeSinceMillis(lastTurretVoidWarn) > 5e3) {
                            turretVoidWarnPlayer = event.unit?.player
                            turretVoidWarnCount = 1
                            val message = Core.bundle.format("client.turretvoidwarn", getName(event.unit),
                                event.tile.x, event.tile.y, void.tileX(), void.tileY()
                            )
                            turretVoidWarnMsg = ui.chatfrag.addMessage(message , null, null, "", message)
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
    }
}
