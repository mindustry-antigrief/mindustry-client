package mindustry.client.utils

import arc.*
import arc.math.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.navigation.*
import mindustry.content.*
import mindustry.entities.bullet.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.type.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.power.NuclearReactor.*
import mindustry.world.blocks.production.*
import mindustry.world.blocks.production.GenericCrafter.*
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
        var drain = false

        fun init() {
            enabled = Core.settings.getBool("autotransfer", false)
            fromCores = Core.settings.getBool("autotransfer-fromcores", true)
            fromContainers = Core.settings.getBool("autotransfer-fromcontainers", true)
            minCoreItems = Core.settings.getInt("autotransfer-mincoreitems", 100)
            delay = Core.settings.getFloat("autotransfer-transferdelay", 60F)
            minTransferTotal = Core.settings.getInt("autotransfer-mintransfertotal", 10)
            minTransfer = Core.settings.getInt("autotransfer-mintransfer", 2)
            drain = Core.settings.getBool("autotransfer-drain", false) // Undocumented for now as drain is very experimental
        }
    }

    val builds = Seq<Building>()
    val containers = Seq<Building>()
    var item: Item? = null
    var timer = 0F
    val counts = IntArray(content.items().size)
    val ammoCounts = IntArray(content.items().size)
    val dpsCounts = FloatArray(content.items().size)
    var core: Building? = null

    fun draw() {
        if (!debug || player.unit().item() == null) return
        builds.forEach {
            val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
            Drawf.select(it.x, it.y, it.block.size * tilesize / 2f + 2f, if (accepted >= Mathf.clamp(player.unit().stack.amount, 1, 5)) Pal.place else Pal.noplace)
        }
    }

    fun update() {
        if (!enabled) return
        if (state.rules.onlyDepositCore) return
        if (ratelimitRemaining <= 1) return // Leave one config for other stuff
        player.unit().item() ?: return
        timer += Time.delta
        if (timer < delay) return
        timer -= delay
        if (drain && drain()) return

        core = if (fromCores) player.closestCore() else null
        if (Navigation.currentlyFollowing is MinePath) { // Only allow autotransfer + minepath when within mineTransferRange
            if (core != null && (Navigation.currentlyFollowing as MinePath).tile?.within(core, mineTransferRange - tilesize * 10) != true) return
        } // Ngl this looks spaghetti

        val buildTree = player.team().data().buildingTree ?: return
        var held = player.unit().stack.amount

        counts.fill(0) // reset needed item counters
        ammoCounts.fill(0)
        dpsCounts.fill(0f)
        buildTree.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, builds.clear()) // grab all buildings in range

        if (fromContainers && (core == null || !player.within(core, itemTransferRange))) core = containers.selectFrom(builds) { it.block is StorageBlock && (item == null || it.items.has(item)) }.min { it -> it.dst(player) }

        builds.filter { it.block.findConsumer<Consume?> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic } != null && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
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
                            val acceptedC = if (i == Items.blastCompound && it.block.findConsumer<Consume> { it is ConsumeItemExplode } != null) 0 else it.acceptStack(i, Int.MAX_VALUE, player.unit())
                            if (acceptedC >= minTransfer && it.block.consumesItem(i) && core!!.items.has(i, minItems)) {
                                // Turrets have varying ammo, add an offset to prioritize some than others
                                ammoCounts[i.id.toInt()] += acceptedC
                                dpsCounts[i.id.toInt()] += acceptedC * getAmmoScore((it.block as? ItemTurret)?.ammoTypes?.get(i))
                            }
                        }
                    }
                    is ConsumeItemDynamic -> {
                        cons.items.get(it).forEach { i -> // Get the current requirements
                            val acceptedC = it.getMaximumAccepted(i.item) - it.items.get(i.item)
                            if (acceptedC >= minTransfer && core!!.items.has(i.item, max(i.amount, minItems))) {
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    else -> throw IllegalStateException("This should never happen. Report this.")
                }
            }
        }
        var maxID = 0 // FINISHME: Also include the items from nearby containers since otherwise we night never find those items
        for (i in 1 until counts.size) {
            if (counts[i] > counts[maxID]) maxID = i
        }

        var maxAmmoID = 0
        // FINISHME: This should prob be `(ammoCount+count)/2F > counts[maxID]` or something
        val doAmmo = ammoCounts.any { (it + 1) / 2F > counts[maxID] } // If ammo requirements are over half as much as other requirements, we prioritize ammo
        if (doAmmo) { // FINISHME: We should also prioritize ammo when turrets are empty probably?
            for (i in 1 until counts.size) {
                if (dpsCounts[i] > dpsCounts[maxAmmoID]) maxAmmoID = i
            }
        }

        item =
            if (doAmmo && ammoCounts[maxAmmoID] >= minTransferTotal) content.item(maxAmmoID)
            else if (counts[maxID] >= minTransferTotal) content.item(maxID)
            else null

        Time.run(delay/2F) {
            if (item != null && core != null && player.within(core, itemTransferRange) && ratelimitRemaining > 1) {
                if (held > 0 && item != player.unit().stack.item && (!net.server() || player.unit().stack.amount > 0)) Call.transferInventory(player, core)
                else if (held == 0 || item != player.unit().stack.item || counts[maxID] > held) Call.requestItem(player, core, item, Int.MAX_VALUE)
                else ratelimitRemaining++ // Yes im this lazy
                item = null
                ratelimitRemaining--
            }
        }
    }

    private fun drain(): Boolean { // Until this class is refactored to have a more generic input output system I'm just gonna copy a lot of code into this function
        core = player.closestCore()
        if (!player.within(core, itemTransferRange)) return false // FINISHME: Still drain anyways, also have option to deposit to containers with no nullloaders or appropriately configured loader as well as specific dest

        val buildTree = player.team().data().buildingTree ?: return false
        buildTree.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, builds.clear()) // grab all buildings in range

        counts.fill(0)
        builds.filter { it is GenericCrafterBuild && !it.shouldConsume() }.forEach { // Crafters that are completely full FINISHME: Do for all buildings with >= mintransfer instead
            val block = it.block as GenericCrafter
            if (block.outputItems == null) return@forEach

            for (out in block.outputItems) counts[out.item.id.toInt()] += it.items[out.item.id.toInt()]
        }

        var maxID = 0; var maxCount = counts[0]
        for (i in 1 until counts.size) {
            val count = counts[i]
            if (count > maxCount) {
                maxID = i
                maxCount = count
            }
        }

        item = if (counts[maxID] >= minTransferTotal) content.item(maxID) else null
        if (item == null) return false

        maxCount = maxCount.coerceAtMost(player.unit().maxAccepted(item))
        builds.sort { b -> b.items[maxID].toFloat() - b.getMaximumAccepted(item) }.forEach { // FINISHME: Don't grab items if they're gonna get stuck in the player inv cause the core is full
            if (ratelimitRemaining <= 1 || it.items[maxID] < minTransfer || maxCount < minTransfer) return@forEach // No ratelimit left or this building doesn't have enough of the item or the player unit is full

            Call.requestItem(player, it, item, maxCount)
            ratelimitRemaining--
            maxCount -= it.items[maxID]
        }

        Time.run(delay/2F) {
            if (ratelimitRemaining > 1 && (maxCount != player.unit().maxAccepted(item)) || maxCount == 0) { // If theres ratelimit remaining and the player has grabbed anything
                Call.transferInventory(player, core)                                                        // or if the player already has a different item FINISHME: should cut delay in half in the second case but im lazy
                ratelimitRemaining--
            }
        }

        return true
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
