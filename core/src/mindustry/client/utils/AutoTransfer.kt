package mindustry.client.utils

import arc.*
import arc.math.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.content.*
import mindustry.client.navigation.*
import mindustry.entities.bullet.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.type.*
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.power.NuclearReactor.*
import mindustry.world.blocks.storage.*
import mindustry.world.consumers.*
import kotlin.math.*

/** An auto transfer setup based on Ferlern/extended-ui */
class AutoTransfer {
    companion object Settings {
        // All of these settings (aside from debug) are overwritten on init()
        @JvmField var enabled = false
        var fromCores = false
        var fromContainers = false
        var minCoreItems = -1
        var delay = -1F
        var debug = false
        var minTransferTotal = -1
        var minTransfer = -1

        fun init() {
            enabled = Core.settings.getBool("autotransfer", false)
            fromCores = Core.settings.getBool("autotransfer-fromcores", true)
            fromContainers = Core.settings.getBool("autotransfer-fromcontainers", true)
            minCoreItems = Core.settings.getInt("autotransfer-mincoreitems", 100)
            delay = Core.settings.getFloat("autotransfer-transferdelay", 60F)
            minTransferTotal = Core.settings.getInt("autotransfer-mintransfertotal", 10)
            minTransfer = Core.settings.getInt("autotransfer-mintransfer", 2)
        }
    }

    val dest = Seq<Building>()
    val containers = Seq<Building>()
    var item: Item? = null
    var timer = 0F
    val counts = IntArray(content.items().size)
    val countsAdditional = FloatArray(content.items().size)
    var core: Building? = null

    fun draw() {
        if (!debug || player.unit().item() == null) return
        dest.forEach {
            val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
            Drawf.select(it.x, it.y, it.block.size * tilesize / 2f + 2f, if (accepted >= Mathf.clamp(player.unit().stack.amount, 1, 5)) Pal.place else Pal.noplace)
        }
    }

    fun update() {
        if (!enabled) return
        if (state.rules.onlyDepositCore) return
        if (ratelimitRemaining <= 1) return
        player.unit().item() ?: return
        timer += Time.delta
        if (timer < delay) return
        timer = 0F

        core = if (fromCores) player.closestCore() else null
        if (Navigation.currentlyFollowing is MinePath) { // Only allow autotransfer + minepath when within mineTransferRange
            if (core != null && (Navigation.currentlyFollowing as MinePath).tile?.within(core, mineTransferRange - tilesize * 10) != true) return
        } // Ngl this looks spaghetti

        val buildings = player.team().data().buildingTree ?: return
        var held = player.unit().stack.amount

        counts.fill(0) // reset needed item counters
        countsAdditional.fill(0f)
        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear()) // grab all buildings in range

        if (fromContainers && (core == null || !player.within(core, itemTransferRange))) core = containers.selectFrom(dest) { it.block is StorageBlock && (item == null || it.items.has(item)) }.min { it -> it.dst(player) }

        dest.filter { it.block.findConsumer<Consume?> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic } != null && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
        .sort { b -> -b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            if (player.unit().item() != Items.blastCompound || it.block.findConsumer<ConsumeItems> { it is ConsumeItemExplode } == null ) {
                val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
                if (accepted > 0 && held > 0) {
                    Call.transferInventory(player, it)
                    held -= accepted
                    ratelimitRemaining--
                }
            }

            val minItems = if (core is CoreBlock.CoreBuild) minCoreItems else 1
            if (core != null) { // Automatically take needed item from core
                when (val cons = it.block.findConsumer<Consume> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic }) { // Cursed af
                    is ConsumeItems -> {
                        cons.items.forEach { i ->
                            val acceptedC = it.acceptStack(i.item, it.getMaximumAccepted(i.item), player.unit())
                            if (acceptedC >= minTransfer && core!!.items.has(i.item, max(i.amount, minItems))) {
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    is ConsumeItemFilter -> {
                        content.items().each { i ->
                            val acceptedC = if (item == Items.blastCompound && it.block.findConsumer<Consume> { it is ConsumeItemExplode } != null) 0 else it.acceptStack(i, Int.MAX_VALUE, player.unit())
                            if (acceptedC >= minTransfer && it.block.consumesItem(i) && core!!.items.has(i, minItems)) {
                                // Turrets have varying ammo, add an offset to prioritize some than others
                                counts[i.id.toInt()] += acceptedC
                                countsAdditional[i.id.toInt()] += acceptedC * getAmmoScore((it.block as? ItemTurret)?.ammoTypes?.get(i))
                            }
                        }
                    }
                    is ConsumeItemDynamic -> {
                        cons.items.get(it).forEach { i -> // Get the current requirements
                            val acceptedC = it.acceptStack(i.item, i.amount, player.unit())
                            if (acceptedC >= minTransfer && core!!.items.has(i.item, max(i.amount, minItems))) {
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    else -> throw IllegalStateException("This should never happen. Report this.")
                }
            }
        }
        var maxID = 0; var maxCount = 0f // FINISHME: Also include the items from nearby containers since otherwise we night never find those items
        for (i in 1 until counts.size) {
            val count = counts[i] + countsAdditional[i]
            if (count > maxCount) {
                maxID = i
                maxCount = count
            }
        }
        if (counts[maxID] >= minTransferTotal) item = content.item(maxID)

        Time.run(delay/2F) {
            if (item != null && core != null && player.within(core, itemTransferRange) && ratelimitRemaining > 1) {
                if (held > 0 && item != player.unit().stack.item) Call.transferInventory(player, core)
                else if (held == 0 || item != player.unit().stack.item || counts[maxID] > held) Call.requestItem(player, core, item, Int.MAX_VALUE)
                else ratelimitRemaining++ // Yes im this lazy
                item = null
                ratelimitRemaining--
            }
        }
    }

    private fun getAmmoScore(ammo: BulletType?): Float {
        return ammo?.estimateDPS() ?: 0f
        /* Commented out for future reference in case I do need my own dps estimation function
//        return (((ammo.damage * if (ammo.pierceBuilding || ammo.pierce) ammo.pierceCap else 1) +
//                    ammo.splashDamage +
//                    ammo.fragBullets * getAmmoScore(ammo.fragBullet)
//                ) * ammo.ammoMultiplier * ammo.reloadMultiplier).toInt()
         */
    }
}
