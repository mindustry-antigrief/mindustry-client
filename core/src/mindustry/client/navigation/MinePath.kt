package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.utils.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.type.*

class MinePath @JvmOverloads constructor(var items: Seq<Item> = player.unit().type.mineItems, var cap: Int = Core.settings.getInt("minepathcap"), val newGame: Boolean = false) : Path() {
    private var lastItem: Item? = null // Last item mined
    private var timer = Interval()

    companion object {
        init {
            Events.on(EventType.WorldLoadEvent::class.java) {
                (Navigation.currentlyFollowing as? MinePath)?.lastItem = null // Reset on world load to prevent stupidity
            }
        }
    }
    constructor(args: String) : this(Seq()) {
        val split = args.split("\\s".toRegex())
        for (a in split) {
            content.items().find { a.equals(it.localizedName, true) && indexer.hasOre(it) }?.run(items::add) ?:
            if (a == "*" || a == "all" || a == "a") items.addAll(content.items().select(indexer::hasOre))
            else if (Strings.canParseInt(a)) cap = a.toInt().coerceAtLeast(0) // Specified cap, <= 0 results in infinite cap
            else player.sendMessage(Core.bundle.format("client.path.builder.invalid", a))
        }

        if (items.isEmpty) {
            items = player.unit().type.mineItems
            if (split.none { Strings.parseInt(it) > 0 }) player.sendMessage("client.path.miner.allinvalid".bundle())
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.mining", items.joinToString(), if (cap == 0) "∞" else cap))
        }
    }

    override fun setShow(show: Boolean) = Unit
    override fun getShow() = false

    override fun follow() {
        val core = player.closestCore() ?: return
        var item = items.min({ indexer.hasOre(it) && player.unit().canMine(it) }) { core.items[it].toFloat() } ?: return
        if (lastItem != null && player.unit().canMine(lastItem) && core.items[lastItem] - core.items[item] < 100) item = lastItem!! // Scuffed, don't switch mining until there's a 100 item difference, prevents constant switching of mine target
        lastItem = item
        if (cap < core.storageCapacity && core.items[item] >= core.storageCapacity || cap != 0 && core.items[item] > cap) {  // Auto switch to BuildPath when core is sufficiently full
            player.sendMessage(Strings.format("[accent]Automatically switching to BuildPath as the core has @ items (this number can be changed in settings).", if (cap == 0) core.storageCapacity else cap))
            Navigation.follow(BuildPath(items, cap))
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
        if ((waypoints.waypoints.lastOrNull()?.dst(player) ?: 0F) > tilesize * 3) waypoints.draw()
    }

    override fun progress() = 0F

    override fun reset() = Unit

    override operator fun next(): Position? = null
}
