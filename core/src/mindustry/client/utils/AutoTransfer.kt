package mindustry.client.utils

import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.content.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.*
import mindustry.world.blocks.defense.turrets.*
import mindustry.world.blocks.defense.turrets.Turret.*

/** A work in progress auto transfer setup based on ilya246's javascript version */
class AutoTransfer {
    companion object Settings {
        var enabled = false
        var fromCores = true // Whether we take from cores
        val items = Seq<Item>() // The items to take
        val source = Seq<Block>()
        val dest = Seq<Block>()
        var range = itemTransferRange
        var delay = 60F // Delay in ticks between actions FINISHME: This should also probably communicate with the config queue setup or something
        var reflimit = 5 // actions per burst FINISHME: Use ratelimit system instead
        var ammoThreshold = 0.85F // prevents turret infinite refill desync FINISHME: Handle this properly
        var fullEnough = .2F // ?? FINISHME: What is this?
    }

    private var item = Items.copper!! // The currently used item
    private var chitemi = 0 // ??
    private var timer = 0F

    fun update() {
        if (!enabled) return
        if (items.isEmpty) return
        if (dest.isEmpty) return
        if (source.isEmpty && !fromCores) return
        if (ratelimitRemaining <= 1) return // Don't eat the whole ratelimit

        timer += Time.delta
        if (timer < delay) return
        timer = 0F

        val core = player.unit().closestCore() ?: return

        if (fromCores && player.unit().acceptsItem(item)) Call.requestItem(player, core, item, Int.MAX_VALUE) // FINISHME: Implement a range check

        var refi = 0
        player.team().data().buildings.intersect(player.x - range, player.y - range, range * 2, range * 2) {
            if (refi >= reflimit || ratelimitRemaining <= 1) return@intersect // FINISHME:
            if (player.unit().maxAccepted(item) > player.unit().itemCapacity() * fullEnough && source.contains(it.block) && it.items.has(item)) { // Take item
                Call.requestItem(player, it, item, Int.MAX_VALUE)
                ratelimitRemaining--
                refi++
                return@intersect
            }

            if ((source.contains(it.block) || // Drop item
                dest.contains(it.block) && player.unit().hasItem() && it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0 &&
                (it !is TurretBuild || it.totalAmmo / (it.block as Turret).maxAmmo.toFloat() < ammoThreshold)))
            {
                ratelimitRemaining--
                Call.transferInventory(player, it)
                refi++
            }
        }
        chitemi = (chitemi + 1) % items.size
        item = items[chitemi]
        if (fromCores && player.unit().hasItem() && player.unit().item() != item) Call.transferInventory(player, core) // FINISHME: Doesn't handle core overflow, should return to source if possible
    }
}