package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;

public class MinePath extends Path {
    Seq<Item> items = new Seq<>(16);
    static Interval timer = new Interval();
    int cap = Core.settings.getInt("minepathcap");
    Item lastItem = null; // Last item mined

    public MinePath() {
        items = player.team().data().mineItems;
    }

    public MinePath(Seq<Item> mineItems, int cap) {
        items = mineItems;
        this.cap = cap;
    }

    public MinePath (String args){
        for (String arg : args.split("\\s")) {
            arg = arg.toLowerCase();
            boolean added = false;
            for (Item item : content.items().select(indexer::hasOre)) {
                if (item.name.toLowerCase().equals(arg) || item.localizedName.toLowerCase().equals(arg)) {
                    items.add(item);
                    added = true;
                }
            }
            if (!added) { // Item not matched
                if (arg.equals("*") || arg.equals("all") || arg.equals("a")) {
                    items.addAll(content.items().select(indexer::hasOre)); // Add all items when the argument is "all" or similar
                } else if (Strings.parseInt(arg) >= 0) {
                    cap = Strings.parseInt(arg);
                } else {
                    player.sendMessage(Core.bundle.format("client.path.builder.invalid", arg));
                }
            }
        }
        if (items.isEmpty()) {
            if (cap == Core.settings.getInt("minepathcap")) player.sendMessage(Core.bundle.get("client.path.miner.allinvalid"));
            items = player.team().data().mineItems;
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.mining", items.toString(", "), cap == 0 ? "infinite" : cap));
        }
    }

    @Override
    public void setShow(boolean show) {

    }

    @Override
    public boolean getShow() {
        return false;
    }

    @Override
    public void follow() {
        CoreBlock.CoreBuild core = player.closestCore();
        if (core == null) return;
        Item item = items.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));
        if (item == null) return;

        if (lastItem != null && lastItem != item && core.items.get(lastItem) - core.items.get(item) < 100) item = lastItem; // Scuffed, don't switch mining until theres a 100 item difference, prevents constant switching of mine target
        lastItem = item;

        if (cap < core.storageCapacity && core.items.get(item) >= core.storageCapacity || cap != 0 && core.items.get(item) > cap) {  // Auto switch to BuildPath when core is sufficiently full
            player.sendMessage(Strings.format("[accent]Automatically switching to BuildPath as the core has @ items (this number can be changed in settings).", cap == 0 ? core.storageCapacity : cap));
            Navigation.follow(new BuildPath(items, cap == 0 ? core.storageCapacity : cap));
        }

        if (player.unit().maxAccepted(item) <= 1) { // drop off
            if (player.within(core, itemTransferRange - tilesize * 10) && timer.get(30)) {
                Call.transferInventory(player, core);
            } else {
                if (player.unit().type.canBoost) player.boosting = true;
                if (Core.settings.getBool("pathnav") && clientThread.taskQueue.size() == 0 && !player.within(core, itemTransferRange - tilesize * 15))
                    clientThread.taskQueue.post(() -> waypoints.set(Seq.with(Navigation.navigator.navigate(v1.set(player.x, player.y), v2.set(core.x, core.y), Navigation.obstacles.toArray(new TurretPathfindingEntity[0]))).filter(wp -> wp.dst(core) > itemTransferRange - tilesize * 15)));
                else waypoint.set(core.x, core.y, itemTransferRange - tilesize * 15, itemTransferRange - tilesize * 15);
            }

        } else { // mine
            Tile tile = indexer.findClosestOre(player.x, player.y, item);
            player.unit().mineTile = tile;
            if (tile == null) return;

            player.boosting = player.unit().type.canBoost && !player.within(tile, tilesize * 2); // FINISHME: Distance based on formation radius rather than just moving super close
            if (Core.settings.getBool("pathnav") && clientThread.taskQueue.size() == 0 && !player.within(tile, tilesize * 3))
                clientThread.taskQueue.post(() -> waypoints.set(Seq.with(Navigation.navigator.navigate(v1.set(player.x, player.y), v2.set(tile.getX(), tile.getY()), Navigation.obstacles.toArray(new TurretPathfindingEntity[0]))).filter(wp -> wp.dst(player) > tilesize)));
            else waypoint.set(tile.getX(), tile.getY(), tilesize, tilesize);
        }
        if (Core.settings.getBool("pathnav")) waypoints.follow();
        else waypoint.run(0);
    }

    @Override
    public void draw() {
        waypoints.draw();
    }

    @Override
    public float progress() {
        return 0;
    }

    @Override
    public void reset() {}

    @Override
    public Position next() {
        return null;
    }
}
