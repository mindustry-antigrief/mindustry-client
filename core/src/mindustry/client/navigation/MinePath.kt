package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.utils.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.Tile

class MinePath @JvmOverloads constructor(
    var items: Seq<Item> = Seq<Item>(),
    var cap: Int = Core.settings.getInt("minepathcap"),
    val newGame: Boolean = false,
    args: String = Core.settings.getString("defaultminepathargs")
) : Path() {

    private var lastItem: Item? = null // Last item mined
    private var timer = Interval()
    private var coreIdle = false
    var tile: Tile? = null

    init {
        val split = args.split("\\s".toRegex())
        if (items.isEmpty) {
            for (a in split) {
                if (a == "*" || a == "all" || a == "a") items.addAll(content.items().select(indexer::hasOre))
                else if (Strings.canParseInt(a)) cap = a.toInt().coerceAtLeast(0) // Specified cap, <= 0 results in infinite cap
                else content.items().find { a.equals(it.name, true) && indexer.hasOre(it) }?.apply(items::add) ?:
                player.sendMessage(Core.bundle.format("client.path.builder.invalid", a))
            }
        }

        if (items.isEmpty) {
            items.addAll(player.unit().type.mineItems)
            if (split.none { Strings.parseInt(it) > 0 }) player.sendMessage("client.path.miner.allinvalid".bundle())
        }
        else if (cap >= 0) {
            player.sendMessage(Core.bundle.format("client.path.miner.tobuild", items.joinToString(), if (cap == 0) "∞" else cap))
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.toidle", items.joinToString(), player.closestCore().storageCapacity))
        }
    }

    companion object {
        init {
            Events.on(EventType.WorldLoadEvent::class.java) {
                (Navigation.currentlyFollowing as? MinePath)?.lastItem = null // Reset on world load to prevent stupidity
            }
        }
    }

    override fun setShow(show: Boolean) = Unit
    override fun getShow() = false

    override fun follow() {
        val core = player.closestCore() ?: return
        var item = items.min({ indexer.hasOre(it) && player.unit().canMine(it) }) { core.items[it].toFloat() } ?: return
        val maxCap = if (cap <= 0) core.storageCapacity else core.storageCapacity.coerceAtMost(cap)
        if (lastItem != null && player.unit().canMine(lastItem) && indexer.hasOre(lastItem) && core.items[lastItem] - core.items[item] < 100 && core.items[lastItem] < maxCap) item = lastItem!! // Scuffed, don't switch mining until there's a 100 item difference, prevents constant switching of mine target
        lastItem = item

        if (!newGame && core.items[item] >= maxCap && cap >= 0) {  // Auto switch to BuildPath when core is sufficiently full
            coreIdle = false
            player.sendMessage(Core.bundle.format("client.path.miner.build", maxCap))
            Navigation.follow(BuildPath(items, cap))
        }

        // idle at core
        if (coreIdle) {
            if (!player.within(core, itemTransferRange - tilesize * 15)) goTo(core, itemTransferRange - tilesize * 15)

            if (player.unit().hasItem()) player.unit().clearItem() // clear items to prepare for MinePath resumption

            if (core.items[item] < maxCap / 2) {
                player.sendMessage(Core.bundle.get("client.path.miner.resume"))
                coreIdle = false
            }

            return
        }

        // go to core and transfer items
        // No need to drop to core if within mineTransferRange
        if (player.unit().maxAccepted(item) <= 1 && tile?.within(core, mineTransferRange - tilesize * 10) == false) {
            if (player.within(core, itemTransferRange - tilesize * 10) && timer[30f]) {
                player.unit().mineTile = null
                Call.transferInventory(player, core)

                // idle at core if cap < 0 (never switch to build path)
                if (core.items[item] >= maxCap && cap < 0) {
                    player.sendMessage(Core.bundle.format("client.path.miner.idle", maxCap))
                    coreIdle = true
                }
            } else {
                if (player.unit().type.canBoost) player.boosting = true
                goTo(core, itemTransferRange - tilesize * 15)
            }

        // mine
        } else {
            val tile = indexer.findClosestOre(player.unit(), item) // FINISHME: Ignore blocked tiles
            if (player.unit().validMine(tile) || tile == null) player.unit().mineTile = tile
            if (tile == null) return
            player.boosting = player.unit().type.canBoost && !player.within(tile, tilesize * 3F)
            goTo(tile, player.unit().type.mineRange - tilesize * 2)
        }
    }

    override fun draw() {
        if ((waypoints.waypoints.lastOrNull()?.dst(player) ?: 0F) > tilesize * 3) waypoints.draw()
    }

    override fun progress() = 0F

    override fun reset() = Unit

    override operator fun next(): Position? = null
}
