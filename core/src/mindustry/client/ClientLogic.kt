package mindustry.client

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.struct.Seq
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import arc.util.Timer
import mindustry.Vars
import mindustry.Vars.ui
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.ConfigRequest
import mindustry.client.antigrief.PowerInfo
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
 * FINISHME: Move the 9000 different bits of code throughout the client to here */
class ClientLogic {
    private var switchTo: MutableList<Any>? = null

    private var turretVoidWarnMsg: ChatMessage? = null
    private var turretVoidWarnCount = 0
    private var turretVoidWarnPlayer: Player? = null

    /** Create event listeners */
    init {
        Events.on(ServerJoinEvent::class.java) { // Run just after the player joins a server
            if (Navigation.currentlyFollowing is AssistPath ||
                Navigation.currentlyFollowing is UnAssistPath ||
                Navigation.currentlyFollowing is WaypointPath<*>
            ) Navigation.stopFollowing()
            Spectate.pos = null
            AutoTransfer.enabled = Core.settings.getBool("autotransfer") && !(Vars.state.rules.pvp && io())

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
                    else if (Core.settings.getString("gamejointext")?.isNotEmpty() == true) {
                        Call.sendChatMessage(Core.settings.getString("gamejointext"))
                    }
                }
            }, 1F)

            if (Core.settings.getBool("onjoinfixcode")) { // TODO: Make this also work for singleplayer worlds
                Core.app.post {
                    val builds = Seq<LogicBlock.LogicBuild>()
                    @Suppress("unchecked_cast") Vars.player.team().data().buildings.getObjects(builds as Seq<Building>) // Must be done on the main thread
                    clientThread.post {
                        builds.removeAll { it !is LogicBlock.LogicBuild }
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
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            Core.app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            if (!syncing){
                Player.persistPlans.clear()
                Vars.frozenPlans.clear()
            }
            lastJoinTime = Time.millis()
            PowerInfo.initialize()
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
            if(coreItems == null) coreItems = ItemModule(true)
            else {
                coreItems.update(false)
                coreItems.clear()
            }
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading
            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("discordrpc")) Vars.platform.startDiscord()
            if (Core.settings.getBool("mobileui")) Vars.mobile = !Vars.mobile
            if (Core.settings.getBool("viruswarnings")) LExecutor.virusWarnings = true
            if (Core.settings.getBool("autotransfer")) AutoTransfer.enabled = true

            Autocomplete.autocompleters.add(BlockEmotes(), PlayerCompletion(), CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()

            // Hitbox setting was changed, this updates it. FINISHME: Remove a while after v7 release.
            if (Core.settings.getBool("drawhitboxes") && Core.settings.getInt("hitboxopacity") == 0) { // Old setting was enabled and new opacity hasn't been set yet
                Core.settings.put("hitboxopacity", 30)
                UnitType.hitboxAlpha = Core.settings.getInt("hitboxopacity") / 100f
            }

            // FINISHME: Remove these at some point
            Core.settings.remove("drawhitboxes")
            Core.settings.remove("signmessages")
            if (Core.settings.getString("gameovertext")?.isNotEmpty() == true) {
                Core.settings.put("gamewintext", Core.settings.getString("gameovertext"))
                Core.settings.remove("gameovertext")
            }

            // How about I enable it anyways :)
//            if (OS.hasProp("policone")) { // People spam these and its annoying. add some argument to make these harder to find
                Client.register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
                    sendMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
                }

                Client.register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
                    sendMessage("Silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance. They are not the same, please get it right!")
                }

                Client.register("hh [h]", "!") { args, _ ->
                    if (!Vars.net.client()) return@register
                    val u = if (args.any()) Vars.content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) } else Vars.player.unit().type
                    val current = (Vars.ui.join.lastHost?.modeName?.first() ?: Vars.ui.join.lastHost?.mode?.name?.get(0) ?: 'f').lowercaseChar()
                    switchTo = mutableListOf<Any>('a', 'p', 's', 'f', 't').apply { remove(current); add(current); add(u) }
                    Call.sendChatMessage("/switch ${switchTo!!.removeFirst()}")
                }
//            }

            val encoded = Main.keyStorage.cert()?.encoded
            if (encoded != null && Main.keyStorage.builtInCerts.any { it.encoded.contentEquals(encoded) }) {
                Client.register("update <name/id...>") { args, _ ->
                    val name = args.joinToString(" ")
                    val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { Strings.levenshtein(Strings.stripColors(it.name), name) }!!
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
                        if (event.unit?.player != turretVoidWarnPlayer || turretVoidWarnPlayer == null) {
                            turretVoidWarnPlayer = event.unit?.player
                            turretVoidWarnCount = 1
                            turretVoidWarnMsg = ui.chatfrag.addMessage(
                                Strings.format(
                                    "[accent]Turret placed by @[accent] at (@, @) is within void (@, @) range", // TODO: Bundle
                                    getName(event.unit), event.tile.x, event.tile.y, void.tileX(), void.tileY()
                                ), null as Color?
                            )
                        } else {
                            ui.chatfrag.messages.remove(turretVoidWarnMsg)
                            ui.chatfrag.messages.insert(0, turretVoidWarnMsg)
                            ui.chatfrag.doFade(6f); // Reset fading
                            turretVoidWarnMsg!!.prefix = "[scarlet](x${++turretVoidWarnCount}) "
                            turretVoidWarnMsg!!.format()
                        }
                        turretVoidWarnPlayer = event.unit?.player
                    }
                }
            }
        }
    }
}
