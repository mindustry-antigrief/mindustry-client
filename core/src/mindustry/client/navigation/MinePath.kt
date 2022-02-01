package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.utils.*
import mindustry.gen.*
import mindustry.type.*

class MinePath : Path {
    var items = Seq<Item>()
    var cap = Core.settings.getInt("minepathcap")
    private var lastItem: Item? = null // Last item mined

    constructor() {
        items = player.unit().type.mineItems
    }

    constructor(mineItems: Seq<Item>, cap: Int = Core.settings.getInt("minepathcap")) {
        items = mineItems
        this.cap = cap
    }

    constructor(args: String) {
        val split = args.split("\\s".toRegex())
        for (a in split) {
            content.items().find { a.equals(it.localizedName, true) && indexer.hasOre(it) }?.run(items::add) ?:
            if (a == "*" || a == "all" || a == "a") items.addAll(content.items().select(indexer::hasOre))
            else if (Strings.parseInt(a) > 0) cap = a.toInt()
            else player.sendMessage(Core.bundle.format("client.path.builder.invalid", a))
        }

        if (items.isEmpty) {
            if (split.none { Strings.parseInt(it) > 0 }) player.sendMessage("client.path.miner.allinvalid".bundle())
            items = player.unit().type.mineItems
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.mining", items.joinToString(), if (cap == 0) "infinite" else cap))
        }
    }

    override fun setShow(show: Boolean) = Unit
    override fun getShow() = false

    override fun follow() {
        val core = player.closestCore() ?: return
        var item = items.min({ indexer.hasOre(it) && player.unit().canMine(it) }) { core.items[it].toFloat() } ?: return
        if (lastItem != null && core.items[lastItem] - core.items[item] < 100) item = lastItem!! // Scuffed, don't switch mining until there's a 100 item difference, prevents constant switching of mine target
        lastItem = item
        if (cap < core.storageCapacity && core.items[item] >= core.storageCapacity || cap != 0 && core.items[item] > cap) {  // Auto switch to BuildPath when core is sufficiently full
            player.sendMessage(Strings.format("[accent]Automatically switching to BuildPath as the core has @ items (this number can be changed in settings).", if (cap == 0) core.storageCapacity else cap))
            Navigation.follow(BuildPath(items, if (cap == 0) core.storageCapacity else cap))
        }

        if (player.unit().maxAccepted(item) <= 1) { // drop off
            if (player.within(core, itemTransferRange - tilesize * 10) && timer[30f]) {
                Call.transferInventory(player, core)
            } else {
                if (player.unit().type.canBoost) player.boosting = true
                goTo(core, itemTransferRange - tilesize * 15)
            }

        } else { // mine
            val tile = indexer.findClosestOre(player.unit(), item) // FINISHME: Ignore blocked tiles
            player.unit().mineTile = tile
            if (tile == null) return
            player.boosting = player.unit().type.canBoost && !player.within(tile, tilesize * 3F)
            if (player.dst(tile) > 2 * tilesize) goTo(tile, tilesize.toFloat()) // FINISHME: Distance based on formation radius rather than just moving super close
        }
    }

    override fun draw() {
        if (waypoints.waypoints.any() && waypoints.waypoints.peek().dst(player) > tilesize * 3) waypoints.draw()
    }

    override fun progress() = 0F

    override fun reset() = Unit

    override operator fun next(): Position? = null

    companion object {
        var timer = Interval()
    }
}