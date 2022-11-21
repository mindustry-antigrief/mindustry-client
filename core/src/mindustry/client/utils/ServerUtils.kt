@file:Suppress("EnumEntryName")
@file:JvmName("ServerUtils")

package mindustry.client.utils

import arc.*
import arc.graphics.*
import arc.struct.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.Vars.*
import mindustry.content.*
import mindustry.ctype.*
import mindustry.entities.abilities.*
import mindustry.entities.bullet.*
import mindustry.game.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.io.*
import mindustry.type.*
import mindustry.ui.fragments.ChatFragment.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.consumers.*
import java.lang.reflect.*
import kotlin.properties.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

enum class Server(@JvmField val canWhisper: Boolean, private val rtvConfirm: String? = "/rtv", @JvmField val ghost: Boolean = false) {
    other(false, null),
    nydus(false),
    cn(false),
    io(true),
    phoenix(true),
    korea(false, null, true);

    companion object {
        @JvmField var current = other

        @JvmStatic
        fun onServerJoin() { // Called just before ServerJoinEvent is fired
            current = when { // FINISHME: Lots of these are similar, iterate through Server.values() to find the correct server
                ui.join.lastHost != null && net.client() && ui.join.lastHost.name.contains("nydus") -> nydus
                ui.join.communityHosts.contains { it.group == "Chaotic Neutral" && it.address == ui.join.lastHost?.address } -> cn
                ui.join.communityHosts.contains { it.group == "io" && it.address == ui.join.lastHost?.address } -> io
                ui.join.communityHosts.contains { it.group == "Phoenix Network" && it.address == ui.join.lastHost?.address } -> phoenix
                ui.join.communityHosts.contains { it.group == "Korea" && it.address == ui.join.lastHost?.address } -> korea
                else -> other
            }
        }

        init {
            Events.on(MenuReturnEvent::class.java) {
                current = other
            }
        }
    }

    @JvmName("b") operator fun invoke() = current == this

    /** Whisper a message to a player (or log an error if whispers are not enabled here). */
    fun whisper(p: Player, msg: String) {
        if (canWhisper) Call.sendChatMessage("/w ${p.id} $msg")
        else Log.warn("Whispers are not enabled on server $name")
    }

    /** Rock the vote clickable button, set [rtvConfirm] to null to disable */
    fun handleRtv(msg: ChatMessage) {
        msg.addButton(rtvConfirm ?: return) { Call.sendChatMessage(rtvConfirm) }
    }
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

    companion object {
        @JvmStatic var current by Delegates.observable(none) { _, oldValue, newValue ->
            if (oldValue == newValue) return@observable // This can happen.
            Log.debug("Swapping custom gamemode from ${oldValue.name} to ${newValue.name}")
            oldValue.disable()
            newValue.enable()
        }

        init {
            Events.on(WorldLoadEvent::class.java) {
                val modeName = if (!net.client() || ui.join.lastHost?.modeName?.isBlank() != false) state.rules.modeName?.lowercase() else ui.join.lastHost.modeName.lowercase()
                current = values().find { it.name == modeName } ?: none
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

    @JvmName("b") operator fun invoke() = CustomMode.current == this

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

private val foreshadowBulletFlood = object : LaserBulletType() {
    init {
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
}