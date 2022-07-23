package mindustry.client.utils

import arc.math.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.type.*
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
    private val counts = IntArray(content.items().size)

    fun draw() {
        if (!debug || player.unit().item() == null) return
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
        val buildings = player.team().data().buildings ?: return
        val core = if (fromCores) player.closestCore() else null
        var held = player.unit().stack.amount

        counts.fill(0) // reset needed item counters
        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear())
        dest.filter { it.block.consumes.has(ConsumeType.item) && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
        .sort { b -> -b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
            if (accepted >= min(held, 5) && held > 0) { // Don't bother transferring items unless we're moving 5 or more, any less and we just waste ratelimit
                Call.transferInventory(player, it)
                held -= accepted
                ratelimitRemaining--
            }

            if (item == null && core != null) { // Automatically take needed item from core, only request once
                when (val cons = it.block.consumes.get<Consume>(ConsumeType.item)) { // Cursed af
                    is ConsumeItems -> {
                        cons.items.forEach { i ->
                            val acceptedC = it.acceptStack(i.item, it.getMaximumAccepted(i.item), player.unit())
                            if (acceptedC >= 7 && core.items.has(i.item, max(i.amount, minCoreItems))) { // FINISHME: Do not hardcode the minumum required number (7) here, this is awful
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    is ConsumeItemFilter -> {
                        content.items().forEach { i ->
                            val acceptedC = it.acceptStack(i, Int.MAX_VALUE, player.unit())
                            if (it.block.consumes.consumesItem(i) && acceptedC >= 7 && core.items.has(i, minCoreItems)) {
                                counts[i.id.toInt()] += acceptedC
                            }
                        }
                    }
                    is ConsumeItemDynamic -> {
                        cons.items.get(it).forEach { i -> // Get the current requirements
                            val acceptedC = it.acceptStack(i.item, i.amount, player.unit())
                            if (acceptedC >= 7 && core.items.has(i.item, max(i.amount, minCoreItems))) {
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("This should never happen. Report this.")
                }
            }
        }
        var maxID = 0
        for (i in 1 until counts.size) {
            if (counts[i] > counts[maxID]) maxID = i
        }
        if (counts[maxID] != 0) item = content.item(maxID) // This is cursed

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