@file:Suppress("EnumEntryName")
@file:JvmName("ServerUtils")

package mindustry.client.utils

import arc.*
import arc.graphics.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.content.*
import mindustry.entities.abilities.*
import mindustry.entities.bullet.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.net.Packets.*
import mindustry.ui.fragments.ChatFragment.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.consumers.*
import java.lang.reflect.*
import kotlin.properties.*

enum class Server( // FINISHME: This is horrible. Why have I done this?
    private val groupName: String?,
    private val mapVote: MapVote? = null,
    @JvmField val whisper: Cmd = Cmd("/w", -1), // FINISHME: This system still sucks despite my best efforts at making it good
    private val rtv: Cmd = Cmd("/rtv", -1),
    @JvmField val freeze: Cmd = Cmd("/freeze", -1),
    @JvmField val ghost: Boolean = false,
    private val votekickString: String = "Type[orange] /vote <y/n>[] to agree."
) {
    other(null),
    nydus("nydus"),
    cn("Chaotic Neutral", rtv = Cmd("/rtv")),
    io("io", MapVote(), Cmd("/w"), Cmd("/rtv"), object : Cmd("/freeze", 4){
        override fun run(vararg args: String) { // Freeze command requires admin in game but the packet does not
            if (!player.admin) Call.serverPacketReliable("freeze_by_id", args[0]) // Yes this will cause a crash when args.size == 0, it shouldn't happen
            else super.run(*args)
        }
    }, votekickString = "Type[orange] /vote <y/n>[] to vote."){
        override fun handleBan(p: Player) {
            ui.showTextInput("@client.banreason.title", "@client.banreason.body", "Griefing") { reason ->
                val id = p.trace?.uuid ?: p.serverID
                if (id != null){
                    ui.showConfirm("@confirm", "@client.rollback.title") {
                        Call.sendChatMessage("/rollback $id 5")
                        Timer.schedule({
                            Call.sendChatMessage("/rollback f")
                        }, .1F)
                    }
                }
                Call.sendChatMessage("/ban ${p.id} $reason")
            }
        }

        override fun adminui() = ClientVars.rank >= 5
    },
    phoenix("Phoenix Network", MapVote(), Cmd("/w"), Cmd("/rtv"), Cmd("/freeze", 9), votekickString = "Type [cyan]/vote y"),
    korea("Korea", ghost = true),
    fish("Fish", null, Cmd("/msg")){ // FINISHME: Get fish to implement id based /msg as currently only works with player names which can contain spaces.
        override fun playerString(p: Player) = p.name.stripColors().substringBefore(' ')
    },
    darkdustry("Darkdustry")
    ;

    companion object {
        open class Cmd(val str: String, private val rank: Int = 0) { // 0 = anyone, -1 = disabled
            val enabled = rank != -1

            open fun canRun() = rank == 0 || enabled && ClientVars.rank >= rank

            operator fun invoke(p: Player, vararg args: String) = invoke(current.playerString(p), *args)

            open operator fun invoke(vararg args: String) = when {
                !enabled -> Log.err("Command $str is disabled on this server.")
                !canRun() -> Log.err("You do not have permission to run $str on this server.")
                else -> run(*args)
            }

            protected open fun run(vararg args: String) = Call.sendChatMessage("$str ${args.joinToString(" ")}")
        }

        private class MapVote(val down: String = "/downvote", val none: String = "/novote", val up: String = "/upvote") {
            operator fun get(i: Int) = if (i == 0) down else if (i == 1) none else if (i == 2) up else null // Yes this is horrible but it saves lines.
        }

        @JvmField var current = other
//        val ghostList by lazy { Core.settings.getJson("ghostmodeservers", Seq::class.java, String::class.java) { Seq<String>() } as Seq<String> }

        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic
        fun onServerJoin() { // Called once on server join before WorldLoadEvent (and by extension ServerJoinEvent), the player will not be added here hence the need for ServerJoinEvent
            val grouped = ui.join.communityHosts.groupBy({ it.group }) { it.address }
            val address = ui.join.lastHost?.address ?: ""
            if (ui.join.lastHost?.name?.contains("nydus") == true) current = nydus
            else entries.forEach {
                if (it.groupName != null && grouped[it.groupName]?.contains(address) == true) {
                    current = it
                    return@forEach
                }
            }
            Log.debug("Joining server, override set to: $current")
        }

        init {
            Events.on(MenuReturnEvent::class.java) {
                current = other
            }
        }
    }

    @JvmName("b") operator fun invoke() = current === this

    /** Converts a player object into a string for use in commands */
    open fun playerString(p: Player) = p.id.toString()

    /** Handle clickable vote buttons */
    open fun handleVoteButtons(msg: ChatMessage) {
        if (rtv.canRun()) msg.addButton(rtv.str, rtv::invoke) // FINISHME: I believe cn has a no option? not too sure
//        if (kick.canRun()) msg.addButton(kick.str, kick::invoke) FINISHME: Implement votekick buttons here
//        FINISHME: Add cn excavate buttons
    }

    /** Run when banning [p] */
    open fun handleBan(p: Player) = Call.adminRequest(p, AdminAction.ban)

    /** Whether or not the player has access to the admin ui in the player list */
    open fun adminui() = false

    /** Map like/dislike */
    fun mapVote(i: Int) {
        if (mapVote != null) Call.sendChatMessage(mapVote[i] ?: run { Log.err("Invalid vote $i"); return })
        else Log.warn("Map votes are not available on server $name")
    }

    fun isVotekick(msg: String) = votekickString in msg
}

enum class CustomMode {
    none,
    flood {
        private var foreshadowBulletVanilla: BulletType? = null // Flood changes the bullet type so we need to keep it here to restore it later

        override fun enable() {
            super.enable()

            overwrites( // This system is awful but it (mostly) works and it wasn't hard to implement.
                UnitTypes.pulsar, "abilities", Seq<Ability>(0), // Pulsar shield regen field removed
                UnitTypes.crawler, "health", 100f,
                UnitTypes.crawler, "speed", 1.5f,
                UnitTypes.crawler, "accel", 0.08f,
                UnitTypes.crawler, "drag", 0.016f,
                UnitTypes.crawler, "flying", true,
                UnitTypes.atrax, "speed", 0.5f,
                UnitTypes.spiroct, "speed", 0.4f,
                UnitTypes.spiroct, "targetAir", false,
                UnitTypes.arkyid, "speed", 0.5f ,
                UnitTypes.arkyid, "targetAir", false,
                UnitTypes.toxopid, "targetAir", false,
                UnitTypes.flare, "health", 275,
                UnitTypes.flare, "range", 140,
                UnitTypes.horizon, "itemCapacity", 20, // Horizons can pick up items in flood, this just allows the items to draw correctly
                UnitTypes.horizon, "health", 440,
                UnitTypes.horizon, "speed", 1.7f,
                UnitTypes.zenith, "health", 1400,
                UnitTypes.zenith, "speed", 1.8f,
                UnitTypes.oct, "abilities", Seq.with(ForceFieldAbility(140f, 16f, 15000f, 60f * 8)), // Oct heal removed, force field buff
                UnitTypes.bryde, "abilities", Seq<Ability>(0), // Bryde shield regen field removed

                Blocks.surgeWall, "lightningChance", 0f,
                Blocks.reinforcedSurgeWall, "lightningChance", 0f,
                Blocks.mender, "healAmount", 6f,
                Blocks.mender, "phaseBoost", 2f,
                Blocks.mendProjector, "phaseBoost", 12f,
                Blocks.mendProjector, "phaseBoost", 2f,
                Blocks.forceProjector, "shieldHealth", 2500f,
                Blocks.forceProjector, "coolantConsumer", ConsumeCoolant(.1f), // FINISHME: This one probably breaks things, also it wont display correctly in the stats page
                Blocks.radar, "health", 500,
                Blocks.regenProjector, "healPercent", 12f, // Nice balance, a casual 180x buff
                Blocks.shockwaveTower, "health", 2000,
//                Blocks.shockwaveTower, "consumeLiquids", Blocks.shockwaveTower.consumeLiquids(*LiquidStack.with(Liquids.cyanogen, 1f / 60f)) // FINISHME: This one too, consumers are annoying
                Blocks.plastaniumConveyor, "absorbLasers", true,
                Blocks.plastaniumConveyor, "health", 225,
                Blocks.thoriumReactor, "health", 1400,
                (Blocks.lancer as PowerTurret).shootType, "damage", 10,
                (Blocks.arc as PowerTurret).shootType, "damage", 4,
                (Blocks.arc as PowerTurret).shootType, "lightningLength", 15,
                (Blocks.swarmer as ItemTurret).shoot, "shots", 5,
                (Blocks.swarmer as ItemTurret).shoot, "shotDelay", 4f,
                Blocks.segment, "range", 160f,
                Blocks.segment, "reload", 9f,
                Blocks.tsunami, "reload", 2f,
                (Blocks.fuse as ItemTurret).ammoTypes.get(Items.titanium), "pierce", false,
                (Blocks.fuse as ItemTurret).ammoTypes.get(Items.titanium), "damage", 10f,
                (Blocks.fuse as ItemTurret).ammoTypes.get(Items.thorium), "pierce", false,
                (Blocks.fuse as ItemTurret).ammoTypes.get(Items.thorium), "damage", 20f,
                Blocks.breach, "targetUnderBlocks", true,
                Blocks.diffuse, "targetUnderBlocks", true,
                Blocks.scathe, "targetUnderBlocks", true,
            )

            val fsAmmo = (Blocks.foreshadow as ItemTurret).ammoTypes
            foreshadowBulletVanilla = fsAmmo.get(Items.surgeAlloy)
            fsAmmo.put(Items.surgeAlloy, foreshadowBulletFlood)
        }

        override fun disable() {
            super.disable()

            (Blocks.foreshadow as ItemTurret).ammoTypes.put(Items.surgeAlloy, foreshadowBulletVanilla)
        }
    },
    defense;

    @OptIn(ExperimentalStdlibApi::class)
    companion object {
        @JvmStatic var current by Delegates.observable(none) { _, oldValue, newValue ->
            if (oldValue == newValue) return@observable // This can happen.
            Log.debug("Swapping custom gamemode from $oldValue to $newValue")
            oldValue.disable()
            newValue.enable()
        }

        init {
            Events.on(WorldLoadEvent::class.java) {
                val modeName = if (!net.client() || ui.join.lastHost?.modeName?.isBlank() != false) state.rules.modeName?.lowercase() else ui.join.lastHost.modeName.lowercase()
                current = entries.find { it.name == modeName } ?: none
            }

            Events.on(MenuReturnEvent::class.java) {
                Log.debug("Returning to menu")
                current = none
            }
        }

        private var defaults: MutableList<Triple<*, Field, *>> = mutableListOf()

        private fun overwrites(vararg args: Any) {
            for (i in args.indices step 3) overwrite(args[i], args[i + 1] as String, args[i + 2])
        }

        private fun <O: Any, T: Any> overwrite(obj: O, name: String, value: T) {
            // FINISHME: Split name at . and call this method recursively so that the casting horribleness above isn't needed
            val field = obj::class.java.getField(name)
            defaults.add(Triple(obj, field, field.get(obj)))
            field.isAccessible = true
            field.set(obj, value)
        }
    }

    @JvmName("b") operator fun invoke() = CustomMode.current === this

    /** Called when this gamemode is detected */
    protected open fun enable() {
        defaults = mutableListOf()
    }

    /** Called when switching to a different gamemode */
    protected open fun disable() {
        defaults.forEach { (obj, field, value) ->
            field.set(obj, value)
        }
    }
}

private val foreshadowBulletFlood = LaserBulletType().apply {
    length = 460f
    damage = 560f
    width = 75f
    lifetime = 65f
    lightningSpacing = 35f
    lightningLength = 5
    lightningDelay = 1.1f
    lightningLengthRand = 15
    lightningDamage = 50f
    lightningAngleRand = 40f
    largeHit = true
    lightningColor = Pal.heal
    lightColor = lightningColor
    shootEffect = Fx.greenLaserCharge
    sideAngle = 15f
    sideWidth = 0f
    sideLength = 0f
    colors = arrayOf(Pal.heal.cpy().a(0.4f), Pal.heal, Color.white)
}

fun handleKick(reason: String) {
    Log.debug("Kicked from server '${ui.join.lastHost?.name ?: "unknown"}' for: '$reason'.")
    if (reason == "Custom client detected.") {

    }
}
