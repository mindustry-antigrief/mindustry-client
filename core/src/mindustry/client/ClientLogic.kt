package mindustry.client

import arc.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
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
import mindustry.world.blocks.logic.*
import mindustry.world.blocks.sandbox.*

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
            Navigation.stopFollowing()
            Spectate.pos = null

            Timer.schedule({
                Core.app.post {
                    val arg = switchTo?.removeFirstOrNull() ?: return@post
                    if (arg is Host) NetClient.connect(arg.address, arg.port)
                    else {
                        if (arg is UnitType) ui.unitPicker.pickUnit(arg)
                        switchTo = null

                        // If no hh then send gamejointext
                        if (Core.settings.getString("gamejointext")?.isNotEmpty() == true) {
                            Call.sendChatMessage(Core.settings.getString("gamejointext"))
                        }

                        when (Core.settings.getInt("automapvote")) {
                            0 -> {}
                            1 -> Call.sendChatMessage("/downvote")
                            2 -> Call.sendChatMessage("/novote")
                            3 -> Call.sendChatMessage("/upvote")
                            4 -> Call.sendChatMessage(("/${arrayOf("no", "up", "down").random()}vote"))
                            else -> {}
                        }
                    }
                }
            }, .1F)

            if (Core.settings.getBool("onjoinfixcode")) { // FINISHME: Make this also work for singleplayer worlds
                ProcessorPatcher.fixCode(if (Core.settings.getBool("removeatteminsteadoffixing")) ProcessorPatcher.FixCodeMode.Remove else ProcessorPatcher.FixCodeMode.Fix)
            }

            Seer.players.clear()
            Groups.player.each(Seer::registerPlayer)
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            Core.app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            if (!syncing) {
                AutoTransfer.enabled = Core.settings.getBool("autotransfer") && !(state.rules.pvp && io())
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

            UnitTypes.horizon.itemCapacity = if (flood()) 20 else 0 // Horizons can pick up items in flood, this just allows the items to draw correctly
            UnitTypes.crawler.health = if (flood()) 100f else 200f // Crawler health is halved in flood
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading
            Core.app.post { // Run next frame as Vars.clientLoaded is true then and the load methods depend on it
                Musics.load() // Loading music isn't very important
                Sounds.load() // Same applies to sounds
            }

            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("discordrpc")) platform.startDiscord()
            if (Core.settings.getBool("mobileui")) mobile = !mobile
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
            if (Core.settings.has("gameovertext")) {
                if (Core.settings.getString("gameovertext").isNotBlank()) Core.settings.put("gamewintext", Core.settings.getString("gameovertext"))
                Core.settings.remove("gameovertext")
            }

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

            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !Strings.stripColors(ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has connected.")) && Time.timeSinceMillis(lastJoinTime) > 10000)
                player.sendMessage(Core.bundle.format("client.connected", e.player.name))
        }

        Events.on(PlayerLeave::class.java) { e -> // Run when a player leaves the server
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !Strings.stripColors(ui.chatfrag.messages.first().message).equals("${Strings.stripColors(e.player.name)} has disconnected.")))
                player.sendMessage(Core.bundle.format("client.disconnected", e.player.name))
        }

        Events.on(GameOverEventClient::class.java) {
            if (net.client()) {
                // Afk players will start mining at the end of a game (kind of annoying but worth it)
                if (!Navigation.isFollowing || (Navigation.currentlyFollowing as? BuildPath)?.mineItems != null) Navigation.follow(MinePath(UnitTypes.gamma.mineItems, newGame = true))

                // Save maps on game over if the setting is enabled
                if (Core.settings.getBool("savemaponend")) control.saves.addSave(state.map.name())
            }

            // TODO: Make this work in singleplayer
            if (it.winner == player.team()) {
                if (Core.settings.getString("gamewintext")?.isNotEmpty() == true) Call.sendChatMessage(Core.settings.getString("gamewintext"))
            } else {
                if (Core.settings.getString("gamelosetext")?.isNotEmpty() == true) Call.sendChatMessage(Core.settings.getString("gamelosetext"))
            }
        }

        Events.on(BlockDestroyEvent::class.java) {
            if (it.tile.block() is PowerVoid) {
                ui.chatfrag.addMessage(Core.bundle.format("client.voidwarn", it.tile.x, it.tile.y))
            }
        }

        // Warn about turrets that are built with an enemy void in range
        Events.on(BlockBuildBeginEventBefore::class.java) { event ->
            val block = event.newBlock
            if (block !is Turret) return@on
            clientThread.post { // Scanning through tiles can be exhaustive. Delegate it to the client thread.
                val voids = Seq<Building>()
                for (tile in world.tiles) if (tile.block() is PowerVoid) voids.add(tile.build)

                val void = voids.find { it.within(event.tile, block.range) }
                if (void != null) { // Code taken from LogicBlock.LogicBuild.configure
                    Core.app.post {
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
