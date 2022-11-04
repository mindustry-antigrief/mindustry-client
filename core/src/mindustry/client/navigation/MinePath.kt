package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.*
import mindustry.world.blocks.storage.*

class MinePath @JvmOverloads constructor(
    var items: Seq<Item> = Seq<Item>(),
    var cap: Int = Core.settings.getInt("minepathcap"),
    val newGame: Boolean = false,
    args: String = Core.settings.getString("defaultminepathargs")
) : Path() {

    private var lastItem: Item? = null // Last item mined
    private var timer = Interval()
    private var coreIdle = false
    private var bestItem: Item? = null
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
        val maxCap = if (cap <= 0) core.storageCapacity else core.storageCapacity.coerceAtMost(cap)
        bestItem = items.min({ indexer.hasOre(it) && player.unit().canMine(it) }) { core.items[it].toFloat() } ?: return
        if (lastItem != null && player.unit().canMine(lastItem) && indexer.hasOre(lastItem) && core.items[lastItem] - core.items[bestItem] < 100 && core.items[lastItem] < maxCap) bestItem = lastItem!! // Scuffed, don't switch mining until there's a 100 item difference, prevents constant switching of mine target
        lastItem = bestItem

        if (!newGame && core.items[bestItem] >= maxCap && cap >= 0) {  // Auto switch to BuildPath when core is sufficiently full
            coreIdle = false
            player.sendMessage(Core.bundle.format("client.path.miner.build", maxCap))
            Navigation.follow(BuildPath(items, cap))
        }

        // idle at core
        if (coreIdle) {
            if (!player.within(core, itemTransferRange - tilesize * 15)) goTo(core, itemTransferRange - tilesize * 15)

            if (player.unit().hasItem()) player.unit().clearItem() // clear items to prepare for MinePath resumption

            if (core.items[bestItem] < maxCap / 2) {
                player.sendMessage(Core.bundle.get("client.path.miner.resume"))
                coreIdle = false
            }

            return
        }

        // go to core and transfer items
        // No need to drop to core if within mineTransferRange
        if (player.unit().maxAccepted(bestItem) <= 1) {
            if (player.within(core, itemTransferRange - tilesize * 10) && timer[30f]) {
                player.unit().mineTile = null
                Call.transferInventory(player, core)

                // idle at core if cap < 0 (never switch to build path)
                if (core.items[bestItem] >= maxCap && cap < 0) {
                    player.sendMessage(Core.bundle.format("client.path.miner.idle", maxCap))
                    coreIdle = true
                }
            } else {
                if (player.unit().type.canBoost) player.boosting = true
                goTo(core, itemTransferRange - tilesize * 15)
            }

        // mine
        } else {
            tile = indexer.findClosestOre(player.unit(), bestItem) ?: return
            if (player.within(tile, player.unit().type.mineRange)) player.unit().mineTile = tile
            player.boosting = player.unit().type.canBoost && !player.within(tile, player.unit().type.mineRange)
            goTo(tile, player.unit().type.mineRange - tilesize * 2)
        }
    }

    override fun draw() {
        if ((waypoints.waypoints.lastOrNull()?.dst(player) ?: 0F) > tilesize * 3) waypoints.draw()
    }

    override fun progress() = 0F

    override fun reset() = Unit

    override operator fun next(): Position? = null

    // FINISHME: Unjank core tp on mix tech maps
//    override fun allowCore(core: CoreBlock.CoreBuild): Boolean {
//        val type = (core.block() as CoreBlock).unitType
//
//        if (tile == null) return false
//        val item: Item? =
//            if ((type.mineFloor && tile!!.block() == Blocks.air)) tile!!.drop()
//            else if (type.mineWalls) tile!!.wallDrop()
//            else return false
//
//        return item != null && type.mineTier >= item.hardness
}
