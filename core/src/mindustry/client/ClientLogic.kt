package mindustry.client

import arc.*
import arc.util.*
import mindustry.*
import mindustry.client.ClientVars.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.logic.*
import mindustry.type.*
import mindustry.world.blocks.logic.*

/** WIP client logic class, similar to [mindustry.core.Logic] but for the client.
 * Handles various events and such.
 * FINISHME: Move the 9000 different bits of code throughout the client to here */
class ClientLogic {
    private var switchTo: MutableList<Any>? = null

    /** Create event listeners */
    init {
        Events.on(ServerJoinEvent::class.java) { // Run just after the player joins a server
            Navigation.stopFollowing()
            Spectate.pos = null
            AutoTransfer.enabled = Core.settings.getBool("autotransfer") && !(Vars.state.rules.pvp && io())

            Timer.schedule({
                Core.app.post {
                    val switchTo = switchTo
                    if (switchTo != null) {
                        Call.sendChatMessage("/${arrayOf("no", "up", "down").random()}vote")
                        if (switchTo.firstOrNull() is Char) Call.sendChatMessage("/switch ${switchTo.removeFirst()}")
                        else {
                            if (switchTo.firstOrNull() is UnitType) Vars.ui.unitPicker.pickUnit(switchTo.first() as UnitType)
                            this.switchTo = null
                        }
                    }
                }
            }, 1F)
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            Core.app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            if (!syncing) {
                Player.persistPlans.clear()
                processorConfigs.clear()
            }
            lastJoinTime = Time.millis()
            PowerInfo.initialize()
            Navigation.obstacles.clear()
            configs.clear()
            Vars.control.input.lastVirusWarning = null
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            hidingAirUnits = false
            showingTurrets = false
            if (Vars.state.rules.pvp) Vars.ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
            overdrives.clear()
            Client.tiles.clear()
        }

        Events.on(ClientLoadEvent::class.java) { // Run when the client finishes loading
            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("debug")) Log.level = Log.LogLevel.debug // Set log level to debug if the setting is checked
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
            Core.settings.remove("drawhitboxes") // Don't need this old setting anymore
            Core.settings.remove("signmessages") // same as above FINISHME: Remove this at some point

            if (OS.hasProp("policone")) { // People spam these and its annoying. add some argument to make these harder to find
                Client.register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
                    sendMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
                }

                Client.register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
                    sendMessage("\"Silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance.\" They are not the same, please get it right!")
                }

                Client.register("hh [h]", "!") { args, _ ->
                    if (!Vars.net.client()) return@register
                    val u = if (args.any()) Vars.content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) } else Vars.player.unit().type
                    val current = (Vars.ui.join.lastHost?.modeName?.first() ?: Vars.ui.join.lastHost?.mode?.name?.get(0) ?: 'f').lowercaseChar()
                    switchTo = mutableListOf<Any>('a', 'p', 's', 'f', 't').apply { remove(current); add(current); add(u) }
                    Call.sendChatMessage("/switch ${switchTo!!.removeFirst()}")
                }
            }

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

        Events.on(BlockBuildEndEvent::class.java) { e -> // Configure logic after construction
            if (e.unit == null || e.team != Vars.player.team() || !Core.settings.getBool("processorconfigs")) return@on
            val build = e.tile.build as? LogicBlock.LogicBuild ?: return@on
            val packed = e.tile.pos()
            if (!processorConfigs.containsKey(packed)) return@on

            if (build.code.any() || build.links.any()) processorConfigs.remove(packed) // Someone else built a processor with data
            else configs.add(ConfigRequest(e.tile.x.toInt(), e.tile.y.toInt(), processorConfigs.remove(packed)))
        }

        Events.on(GameOverEventClient::class.java) {
            if (!Navigation.isFollowing() || (Navigation.currentlyFollowing as? BuildPath)?.mineItems != null) Navigation.follow(MinePath(UnitTypes.gamma.mineItems, newGame = true)) // Afk players will start mining at the end of a game (kind of annoying but worth it)
        }
    }
}