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
import mindustry.world.blocks.production.Drill.*
import mindustry.world.blocks.production.GenericCrafter.*
import mindustry.world.blocks.storage.*
import mindustry.world.blocks.storage.Unloader.*
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
        var drainToContainers = false

        fun init() {
            // Main settings
            enabled = Core.settings.getBool("autotransfer", false)
            fromCores = Core.settings.getBool("autotransfer-fromcores", true)
            fromContainers = Core.settings.getBool("autotransfer-fromcontainers", true)
            minCoreItems = Core.settings.getInt("autotransfer-mincoreitems", 100)
            delay = Core.settings.getFloat("autotransfer-transferdelay", 60F)
            minTransferTotal = Core.settings.getInt("autotransfer-mintransfertotal", 10)
            minTransfer = Core.settings.getInt("autotransfer-mintransfer", 2)
            // Drain settings, undocumented for now as drain is still experimental
            drain = Core.settings.getBool("autotransfer-drain", false)
            drainToContainers = Core.settings.getBool("autotransfer-draintocontainers", false)
        }
    }

    val builds = Seq<Building>(false) // Not ordered as we sort *after* mutation is finished.
    val containers = Seq<Building>()
    var item: Item? = null
    var timer = 0F
    val counts = IntArray(content.items().size)
    val ammoCounts = IntArray(content.items().size)
    val dpsCounts = FloatArray(content.items().size)
    var core: Building? = null
    var justTransferred = false

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
        counts.fill(0) // reset needed item counters
        ammoCounts.fill(0)
        dpsCounts.fill(0f)
        if (!justTransferred && drain && drain()) return
        justTransferred = false
        transfer()
    }

    /** Transfers items from core/containers into buildings */
    private fun transfer() {
        core = if (fromCores) player.closestCore() else null
        if (Navigation.currentlyFollowing is MinePath) { // Only allow autotransfer + minepath when within mineTransferRange
            if (core != null && (Navigation.currentlyFollowing as MinePath).tile?.within(core, mineTransferRange - tilesize * 10) != true) return
        } // Ngl this looks spaghetti

        val buildTree = player.team().data().buildingTree ?: return
        var held = player.unit().stack.amount

        buildTree.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, builds.clear()) // grab all buildings in range

        if (fromContainers && (core == null || !player.within(core, itemTransferRange))) core = containers.selectFrom(builds) { it.block is StorageBlock && (item == null || it.items.has(item)) }.min { it -> it.dst(player) }

        builds.retainAll { it.block.findConsumer<Consume?> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic } != null && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
            .sort { b -> -b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
            .forEach {
                if (ratelimitRemaining <= 1) return@forEach

                held = depositIntoBuilding(it, held)

                val minItems = if (core is CoreBlock.CoreBuild) minCoreItems else 1 // FINISHME: Is this else 1 right? It seems odd...
                if (core != null) { // Automatically take needed item from core
                    processTransferTarget(it, minItems)
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
                item = null
                justTransferred = true
            } else {
                timer = delay
            }
        }
    }

    /** Transfers outputs from blocks into core/containers */
    private fun drain(): Boolean { // FINISHME: Until this class is refactored to have a more generic input output system I'm just gonna copy a lot of code into this function
        core = player.closestCore()
        val nearCore = player.within(core, itemTransferRange)
        if (!nearCore) core = null

        val buildTree = player.team().data().buildingTree ?: return false
        buildTree.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, builds.clear()) // grab all buildings in range

        val bestContainers = if (!nearCore && drainToContainers) findDrainDestinations() else emptyArray() // This uses the nearby builds so we do this after the intersect

        val nonContainerBuilds = builds.select { it.block.findConsumer<Consume?> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic } != null && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
            .sort { b -> -b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }

        nonContainerBuilds.each { processTransferTarget(it, 0) }

        val nonContainerDrainCounts = counts.copyOf() // Direct to factory drain targets. This is scuffed but oh well
        for ((index, i) in ammoCounts.withIndex()) nonContainerDrainCounts[index] += i // Include turret ammo counts as they're separate FINISHME: hack

        // Find the drainable items
        counts.fill(0)
        processDrainSources()

        var maxID = -1
        var maxCount = 0
        if (nearCore) { // Draining to core: Drain as much as possible always (without overfilling cores)
            val playerCap = player.unit().itemCapacity()
            val reasonableCoreLimit = (core!! as CoreBlock.CoreBuild).storageCapacity - 300 - playerCap * 3 // Why is this reasonable? Because it feels right. There is no other reason.
            for (i in counts.indices) {
                val count = counts[i]
                if (count > maxCount && core!!.items.get(i) < reasonableCoreLimit) {
                    maxID = i
                    maxCount = count
                }
            }
        } else { // Draining to multiple inventories: Drain the thing that is most needed FINISHME: We should make the whole autotransfer/drain system smart enough to select the highest average items per transfer instead of just moving the most items. Moving 10 items to 3 inventories is less worth it than moving 9 items to 2 inventories as it will exhaust more ratelimit.
            for (i in nonContainerDrainCounts.indices) {
                val count = nonContainerDrainCounts[i]
                if (count > maxCount && counts[i] > minTransferTotal) {
                    maxID = i
                    maxCount = count
                }
            }
            // Nothing to drain to factories: Drain to a single container with a configured unloader
            if (maxID == -1 && drainToContainers) {
                for (i in counts.indices) {
                    val count = counts[i]
                    if (count > maxCount && bestContainers[i] != null) {
                        maxID = i
                        maxCount = count
                    }
                }
                if (maxID != -1) core = bestContainers[maxID]
            }
        }
        if (maxID == -1) return false // No core/container/factory was found, perform a normal transfer round instead

        item = if (counts[maxID] >= minTransferTotal) content.item(maxID) else null
        if (item == null) return false

        maxCount = maxCount.coerceAtMost(player.unit().maxAccepted(item))
        builds.sort { b -> b.items[maxID].toFloat() - b.getMaximumAccepted(item) }.forEach {
            if (ratelimitRemaining <= 1 || it.items[maxID] < minTransfer || maxCount < minTransfer) return@forEach // No ratelimit left or this building doesn't have enough of the item or the player unit is full

            Call.requestItem(player, it, item, maxCount)
            maxCount -= it.items[maxID]
        }

        Time.run(delay/2F) {
            if (core != null) { // Standard single target drain
                if (ratelimitRemaining > 1 && (maxCount != player.unit().maxAccepted(item) || maxCount == 0)) { // If theres ratelimit remaining and the player has grabbed anything or if the player is holding something else
                    if (maxCount == 0) { // We're holding something else and we need to dispose of it somehow
                        justTransferred = true // Force an autotransfer next time, draining probably won't fix much
                        timer = delay // Immediately pick up items on the next frame
                    }
                    if (core!!.getMaximumAccepted(item) > 0) Call.transferInventory(player, core) // Drain to the block
                }
            } else { // Drain into (possibly) multiple buildings
                var held = counts[maxID] // FINISHME: This number will probably be wrong, we should somehow fix this to save ratelimit
                nonContainerBuilds.forEach {
                    if (ratelimitRemaining <= 1) return@forEach
                    held = depositIntoBuilding(it, held)
                }
            }
        }

        return true
    }

    /** Gathers the counts for all drainable buildings. */
    private fun processDrainSources() {
        for (i in builds.size - 1 downTo 0) {
            when (val build = builds[i]) {
                is GenericCrafterBuild -> { // Crafters that are near full
                    if (!build.block.outputsItems()) builds.remove(i)
                    else if ((build.block as GenericCrafter).outputItems.any { (build.items[it.item] + it.amount) >= build.block.itemCapacity }) (build.block as GenericCrafter).outputItems.forEach { counts[it.item.id.toInt()] += build.items[it.item.id.toInt()] } // FINISHME: Use the item cap instead of shouldConsume as shouldConsume is false for disabled blocks which will cause transfer attempts not to mention that shouldConsume does more work than needed.

                }
                is DrillBuild -> { // Drills that are full
                    if (build.dominantItem == null || build.items.total() < build.block.itemCapacity) builds.remove(i)
                    else counts[build.dominantItem.id.toInt()] += build.items.total() // FINISHME: This can likely be wrong but it shouldn't matter, right?
                }
                else -> builds.remove(i)
            }
        }
    }

    /** Finds the best StorageBlock to drain to for each building. */
    private fun findDrainDestinations(): Array<Building?> {
        val has = BooleanArray(content.items().size) // We don't really care about these allocations, honestly
        val loadables = arrayOfNulls<Building>(content.items().size) // Array of the most empty container for each item type
        builds.each {
            if (it.block !is StorageBlock || it.block is CoreBlock || !player.within(it, itemTransferRange)) return@each
            has.fill(false)
            for (i in 0 ..< it.proximity.size) { // Returning from a Seq loop creates garbage, using a for i loop solves this
                val prox = it.proximity[i]
                if (prox !is UnloaderBuild) continue
                if (prox.sortItem == null) return@each // Nulloaders will cause issues, ignore containers with them FINISHME: Instead of fully ignoring them, we should just insert items that are already in the container
                has[prox.sortItem.id.toInt()] = true
            }

            val cap = it.block.itemCapacity
            for (i in has.indices) { // Iterate all containers for this item
                if (has[i]) {
                    if (loadables[i] == null) { // First container for this item, set it up
                        loadables[i] = it
                        continue
                    }
                    val container = loadables[i]!!
                    if (cap - it.items[i] > container.block.itemCapacity - container.items[i]) loadables[i] = it
                }
            }
        }
        return loadables
    }

    /** Attempts to make a deposit. Returns the remaining [held] value. */
    private fun depositIntoBuilding(build: Building, held: Int): Int {
        if (held <= 0
        || player.unit().item() == Items.blastCompound && build.block.findConsumer<ConsumeItems> { it is ConsumeItemExplode } != null // Don't explode things
        || build.block.findConsumer<ConsumeItems> { it.booster && it is ConsumeItems && it.items.any { it.item == player.unit().item()} } != null // Don't provide boosters
        ) return held
        val accepted = build.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())

        if (accepted <= 0) return held // FINISHME: Shouldn't we be enforcing minTransfer here too?
        Call.transferInventory(player, build)
        return held - accepted
    }

    /** Adds the possible deposits for the [build] to [counts], [ammoCounts], and [dpsCounts] as needed. */
    private fun processTransferTarget(build: Building, minItems: Int) {
        fun hasMinItems(item: Item, min: Int = minItems) = minItems == 0 || core!!.items.has(item, min)

        when (val cons = build.block.findConsumer<Consume> { (it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic) && it !is ConsumeItemExplode } ?: build.block.findConsumer { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic }) { // Cursed af
            is ConsumeItems -> {
                cons.items.forEach { i ->
                    if (cons.booster) return@forEach // Don't boost menders, projectors or overdrives
                    val acceptedC = build.acceptStack(i.item, build.getMaximumAccepted(i.item), player.unit())
                    if (acceptedC >= minTransfer && hasMinItems(i.item, max(i.amount, minItems))) {
                        counts[i.item.id.toInt()] += acceptedC
                    }
                }
            }
            is ConsumeItemFilter -> {
                content.items().each { i ->
                    val acceptedC = if (i == Items.blastCompound && build.block.findConsumer<Consume> { it is ConsumeItemExplode } != null) 0 else build.acceptStack(i, Int.MAX_VALUE, player.unit())
                    if (acceptedC >= minTransfer && build.block.consumesItem(i) && hasMinItems(i)) {
                        if (build.block is ItemTurret) { // Turrets have varying ammo, add an offset to prioritize some than others
                            ammoCounts[i.id.toInt()] += acceptedC
                            dpsCounts[i.id.toInt()] += acceptedC * getAmmoScore((build.block as? ItemTurret)?.ammoTypes?.get(i))
                        } else {
                            counts[i.id.toInt()] += acceptedC
                        }
                    }
                }
            }
            is ConsumeItemDynamic -> {
                cons.items.get(build).forEach { i -> // Get the current requirements
                    val acceptedC = build.getMaximumAccepted(i.item) - build.items.get(i.item)
                    if (acceptedC >= minTransfer && hasMinItems(i.item, max(i.amount, minItems))) {
                        counts[i.item.id.toInt()] += acceptedC
                    }
                }
            }
            else -> throw IllegalStateException("This should never happen. Report this.")
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
