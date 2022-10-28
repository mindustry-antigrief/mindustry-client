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
import mindustry.core.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.logic.*
import mindustry.net.*
import mindustry.type.*
import mindustry.world.blocks.logic.*

/** WIP client logic class, similar to [mindustry.core.Logic] but for the client.
 * Handles various events and such.
 * FINISHME: Move the 9000 different bits of code throughout the client to here. Update: this was an awful idea lmao */
class ClientLogic {
    companion object {
        var switchTo: MutableList<Any>? = null
    }

    /** Create event listeners */
    init {
        Events.on(ServerJoinEvent::class.java) { // Run just after the player joins a server
            Navigation.stopFollowing()
            Spectate.pos = null

            Timer.schedule({
                Core.app.post {
                    val arg = switchTo?.removeFirstOrNull() ?: return@post
                    Call.sendChatMessage("/${arrayOf("no", "up", "down").random()}vote")
                    if (arg is Host) NetClient.connect(arg.address, arg.port)
                    else {
                        if (arg is UnitType) Vars.ui.unitPicker.pickUnit(arg)
                        switchTo = null
                    }
                }
            }, .1F)
        }

        Events.on(WorldLoadEvent::class.java) { // Run when the world finishes loading (also when the main menu loads and on syncs)
            Core.app.post { syncing = false } // Run this next frame so that it can be used elsewhere safely
            if (!syncing) {
                AutoTransfer.enabled = Core.settings.getBool("autotransfer") && !(Vars.state.rules.pvp && io())
                Player.persistPlans.clear()
                processorConfigs.clear()
            }
            lastJoinTime = Time.millis()
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
            Core.settings.remove("drawhitboxes") // Don't need this old setting anymore
            Core.settings.remove("signmessages") // same as above FINISHME: Remove this at some point
            Core.settings.remove("firescl") // firescl, effectscl and cmdwarn were added in sept 2022, remove them in mid 2023 or something
            Core.settings.remove("effectscl")
            Core.settings.remove("commandwarnings")

            if (OS.hasProp("policone")) { // People spam these and its annoying. add some argument to make these harder to find
                register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
                    sendMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
                }

                register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
                    sendMessage("\"Silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance.\" They are not the same, please get it right!")
                }

                register("hh [h]", "!") { args, _ ->
                    if (!Vars.net.client()) return@register
                    val u = if (args.any()) Vars.content.units().min { u -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], u.localizedName) } else Vars.player.unit().type
                    val current = Vars.ui.join.lastHost ?: return@register
                    if (current.group == null) current.group = Vars.ui.join.communityHosts.find { it == current } ?.group ?: return@register
                    switchTo = Vars.ui.join.communityHosts.filterTo(mutableListOf<Any>()) { it.group == current.group && it != current && !it.equals("135.181.14.60:6567") }.apply { add(current); add(u) } // IO attack has severe amounts of skill issue currently hence why its ignored
                    val first = switchTo!!.removeFirst() as Host
                    NetClient.connect(first.address, first.port)
                }
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
            if (Vars.net.client()) {
                // Afk players will start mining at the end of a game (kind of annoying but worth it)
                if (!Navigation.isFollowing || (Navigation.currentlyFollowing as? BuildPath)?.mineItems != null) Navigation.follow(MinePath(UnitTypes.gamma.mineItems, newGame = true))

                // Save maps on game over if the setting is enabled
                if (Core.settings.getBool("savemaponend")) Vars.control.saves.addSave(Vars.state.map.name())
            }
        }
    }
}