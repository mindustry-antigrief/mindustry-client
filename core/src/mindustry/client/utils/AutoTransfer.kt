package mindustry.client.utils

import arc.math.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.navigation.MinePath
import mindustry.client.navigation.Navigation
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.type.*
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.power.NuclearReactor.*
import mindustry.world.consumers.*
import kotlin.math.*

/** An auto transfer setup based on Ferlern/extended-ui */
class AutoTransfer {
    companion object Settings {
        @JvmField var enabled = false
        var fromCores = true
        var minCoreItems = 100
        var delay = 60F
        var debug = false
    }

    private val dest = Seq<Building>()
    private var item: Item? = null
    private var timer = 0F

    fun draw() {
        if (!debug) return
        dest.forEach {
            val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
            Drawf.select(it.x, it.y, it.block.size * tilesize / 2f + 2f, if (accepted >= Mathf.clamp(player.unit().stack.amount, 1, 5)) Pal.place else Pal.noplace)
        }
    }

    fun update() {
        if (!enabled) return
        if (ratelimitRemaining <= 1) return
        player.unit().item() ?: return
        timer += Time.delta
        if (timer < delay) return
        timer = 0F

        val core = if (fromCores) player.closestCore() else null
        if (Navigation.currentlyFollowing is MinePath) { // Only allow autotransfer + minepath when within mineTransferRange
            if (core != null && (Navigation.currentlyFollowing as MinePath).tile?.within(core, mineTransferRange - tilesize * 10) != true) return
        } // Ngl this looks spaghetti

        val buildings = player.team().data().buildings ?: return
        var held = player.unit().stack.amount

        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear())
        dest.filter { it.block.consumes.has(ConsumeType.item) && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
        .sort { b -> b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
            if (accepted >= min(held, 5) && held > 0) { // Don't bother transferring items unless we're moving 5 or more, any less and we just waste ratelimit
                Call.transferInventory(player, it)
                held -= accepted
                ratelimitRemaining--
            }

            if (item == null && core != null) { // Automatically take needed item from core, only request once FINISHME: int[content.items().size)] that keeps track of number of each item needed so that this can be more efficient
                item = run<Item?> { // FINISHME: I should really just make this its own function
                    when (val cons = it.block.consumes.get<Consume>(ConsumeType.item)) { // Cursed af
                        is ConsumeItems -> {
                            cons.items.forEach { i ->
                                    if (it.acceptStack(i.item, it.getMaximumAccepted(i.item), player.unit()) >= 7 && core.items.has(i.item, max(i.amount, minCoreItems))) { // FINISHME: Do not hardcode the minumum required number (7) here, this is awful
                                    return@run i.item
                                }
                            }
                        }
                        is ConsumeItemFilter -> {
                            if (it.block is ItemTurret) {
                                var ammo: Item? = null
                                var damage = 0
                                content.items().forEach { i ->
                                    if (it.block.consumes.consumesItem(i) && it.acceptStack(i, Int.MAX_VALUE, player.unit()) >= 7 && core.items.has(i, minCoreItems) && (it.block as ItemTurret).ammoTypes.get(i).damage > damage) {
                                        damage = (it.block as ItemTurret).ammoTypes.get(i).damage.toInt()
                                        ammo = i
                                    }
                                }
                                return@run ammo
                            } else {
                                content.items().forEach { i ->
                                    if (it.block.consumes.consumesItem(i) && it.acceptStack(i, Int.MAX_VALUE, player.unit()) >= 7 && core.items.has(i, minCoreItems)) {
                                        return@run i
                                    }
                                }
                            }
                        }
                        is ConsumeItemDynamic -> {
                            cons.items.get(it).forEach { i -> // Get the current requirements
                                if (it.acceptStack(i.item, i.amount, player.unit()) >= 7 && core.items.has(i.item, max(i.amount, minCoreItems))) {
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

        Time.run(delay/2F) {
            if (item != null && core != null && player.within(core, itemTransferRange) && ratelimitRemaining > 1) {
                if (held > 0 && item != player.unit().stack.item) Call.transferInventory(player, core)
                else Call.requestItem(player, core, item, Int.MAX_VALUE)
                item = null
                ratelimitRemaining--
            }
        }
    }
}
