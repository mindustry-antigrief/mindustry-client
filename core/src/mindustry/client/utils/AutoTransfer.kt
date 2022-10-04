package mindustry.client.utils

import arc.*
import arc.math.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.content.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.type.*
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

        fun init() {
            enabled = Core.settings.getBool("autotransfer", false)
            fromCores = Core.settings.getBool("fromcores", true)
            fromContainers = Core.settings.getBool("fromcontainers", true)
            minCoreItems = Core.settings.getInt("mincoreitems", 100)
            delay = Core.settings.getFloat("transferdelay", 60F)
        }
    }

    val dest = Seq<Building>()
    val containers = Seq<Building>()
    var item: Item? = null
    var timer = 0F
    val counts = IntArray(content.items().size)
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
        val buildings = player.team().data().buildingTree ?: return
        core = if (fromCores) player.closestCore() else null

        counts.fill(0) // reset needed item counters
        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear()) // grab all buildings in range

        if (fromContainers && (core == null || !player.within(core, itemTransferRange))) core = containers.selectFrom(dest) { it.block is StorageBlock }.min { it -> it.dst(player) }
        var held = player.unit().stack.amount

        dest.filter { it.block.findConsumer<Consume?> { it is ConsumeItems || it is ConsumeItemFilter || it is ConsumeItemDynamic } != null && it !is NuclearReactorBuild && player.within(it, itemTransferRange) }
        .sort { b -> -b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            if (player.unit().item() != Items.blastCompound || it.block.findConsumer<ConsumeItems> { it is ConsumeItemExplode } == null ) {
                val accepted = it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit())
                if (accepted >= min(held, 4) && held > 0) { // Don't bother transferring items unless we're moving 5 or more, any less and we just waste ratelimit
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
                            if (acceptedC >= 7 && core!!.items.has(i.item, max(i.amount, minItems))) { // FINISHME: Do not hardcode the minumum required number (7) here, this is awful
                                counts[i.item.id.toInt()] += acceptedC
                            }
                        }
                    }
                    is ConsumeItemFilter -> {
                        content.items().each { i ->
                            val acceptedC = if (item == Items.blastCompound && it.block.findConsumer<Consume> { it is ConsumeItemExplode } != null) 0 else it.acceptStack(i, Int.MAX_VALUE, player.unit())
                            if (it.block.consumesItem(i) && acceptedC >= 4 && core!!.items.has(i, minItems)) {
                                counts[i.id.toInt()] += acceptedC
                            }
                        }
                    }
                    is ConsumeItemDynamic -> {
                        cons.items.get(it).forEach { i -> // Get the current requirements
                            val acceptedC = it.acceptStack(i.item, i.amount, player.unit())
                            if (acceptedC >= 7 && core!!.items.has(i.item, max(i.amount, minItems))) {
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
        if (counts[maxID] != 0) item = content.item(maxID) // This is cursed

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
}