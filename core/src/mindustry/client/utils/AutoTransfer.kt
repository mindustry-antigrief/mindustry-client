package mindustry.client.utils

import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.consumers.*

///** A work in progress auto transfer setup based on ilya246's javascript version */
//class AutoTransfer {
//    companion object Settings {
//        var enabled = false
//        var fromCores = true // Whether we take from cores
//        val items = Seq<Item>() // The items to take
//        val source = Seq<Block>()
//        val dest = Seq<Block>()
//        var range = itemTransferRange
//        var delay = 60F // Delay in ticks between actions FINISHME: This should also probably communicate with the config queue setup or something
//        var reflimit = 5 // actions per burst FINISHME: Use ratelimit system instead
//        var ammoThreshold = 0.85F // prevents turret infinite refill desync FINISHME: Handle this properly
//        var fullEnough = .2F // ?? FINISHME: What is this?
//    }
//
//    private var item = Items.copper!! // The currently used item
//    private var chitemi = 0 // ??
//    private var timer = 0F
//
//    fun update() {
//        if (!enabled) return
//        if (items.isEmpty) return
//        if (dest.isEmpty) return
//        if (source.isEmpty && !fromCores) return
//        if (ratelimitRemaining <= 1) return // Don't eat the whole ratelimit
//
//        timer += Time.delta
//        if (timer < delay) return
//        timer = 0F
//
//        val core = player.unit().closestCore() ?: return
//
//        if (fromCores && player.unit().acceptsItem(item)) Call.requestItem(player, core, item, Int.MAX_VALUE) // FINISHME: Implement a range check
//
//        var refi = 0
//        val buildings = player.team().data().buildings ?: return
//        buildings.intersect(player.x - range, player.y - range, range * 2, range * 2) {
//            if (refi >= reflimit || ratelimitRemaining <= 1) return@intersect // FINISHME:
//            if (player.unit().maxAccepted(item) > player.unit().itemCapacity() * fullEnough && source.contains(it.block) && it.items.has(item)) { // Take item
//                Call.requestItem(player, it, item, Int.MAX_VALUE)
//                ratelimitRemaining--
//                refi++
//                return@intersect
//            }
//
//            if ((source.contains(it.block) || // Drop item
//                dest.contains(it.block) && player.unit().hasItem() && it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0 &&
//                (it !is TurretBuild || it.totalAmmo / (it.block as Turret).maxAmmo.toFloat() < ammoThreshold)))
//            {
//                ratelimitRemaining--
//                Call.transferInventory(player, it)
//                refi++
//            }
//        }
//        chitemi = (chitemi + 1) % items.size
//        item = items[chitemi]
//        if (fromCores && player.unit().hasItem() && player.unit().item() != item) Call.transferInventory(player, core) // FINISHME: Doesn't handle core overflow, should return to source if possible
//    }
//}

/** An auto transfer setup based on Ferlern/extended-ui */
class AutoTransfer {
    companion object Settings {
        var enabled = false
        var fromCores = true
        var minCoreItems = 20
            set(_) = TODO("Min core items not yet implemented")
        var delay = 30F
    }

    private val dest = Seq<Building>()
    private var item: Item? = null
    private var timer = 0F

    fun update() {
        if (!enabled) return
        if (ratelimitRemaining <= 1) return
        timer += Time.delta
        if (timer < AutoTransfer.delay) return
        timer = 0F
        val buildings = player.team().data().buildings ?: return
        val core = if (fromCores) player.closestCore() else null

        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear())
        dest.filter { it.block.consumes.has(ConsumeType.item) }.sort { b -> b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            if (it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0) {
                Call.transferInventory(player, it)
                ratelimitRemaining--
            }

            if (item == null && core != null) { // Automatically take needed item from core, only request once FINISHME: int[content.items().size)] that keeps track of number of each item needed so that this can be more efficient
                item = run<Item?> { // FINISHME: I should really just make this its own function
                    when (val cons = it.block.consumes.get<Consume>(ConsumeType.item)) { // Cursed af
                        is ConsumeItems -> {
                            cons.items.forEach { i ->
                                if (it.acceptStack(i.item, it.getMaximumAccepted(i.item), player.unit()) >= 7) { // FINISHME: Do not hardcode the minumum required number (7) here, this is awful
                                    return@run i.item
                                }
                            }
                        }
                        is ConsumeItemFilter -> {
                            content.items().forEach { i ->
                                if (it.block.consumes.consumesItem(i) && it.acceptStack(i, Int.MAX_VALUE, player.unit()) >= 7) {
                                    return@run item
                                }
                            }
                        }
                        is ConsumeItemDynamic -> {
                            cons.items.get(it).forEach { i -> // Get the current requirements
                                if (it.acceptStack(i.item, i.amount, player.unit()) >= 7) {
                                    return@run i.item
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("This should never happen. Report this.")
                    }
                    return@run null
                }
            }
        }

        if (item != null && core != null && player.within(core, itemTransferRange)) {
            if (player.unit().hasItem()) Call.transferInventory(player, core)
            else Call.requestItem(player, core, item, Int.MAX_VALUE)
            item = null
            ratelimitRemaining--
        }
    }
}