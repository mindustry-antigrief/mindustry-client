package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.*;

import static mindustry.Vars.*;

public class MinePath extends Path {
    Seq<Item> items = new Seq<>(16);
    StringBuilder itemString = new StringBuilder();
    static Interval timer = new Interval();
    int cap = Core.settings.getInt("minepathcap");

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
                    itemString.append(item.localizedName).append(", ");
                    added = true;
                }
            }
            if (!added) { // Item not matched
                if (arg.equals("*") || arg.equals("all") || arg.equals("a")) {
                    items.addAll(content.items().select(indexer::hasOre)); // Add all items when the argument is "all" or similar
                    itemString.append("Everything, ");
                } else if (Strings.canParsePositiveInt(arg)) cap = Strings.parsePositiveInt(arg);
                else player.sendMessage(Core.bundle.format("client.path.builder.invalid", arg));
            }
        }
        if (items.isEmpty()) {
            player.sendMessage(Core.bundle.get("client.path.miner.allinvalid"));
            items = player.team().data().mineItems;
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.mining", itemString.substring(0, itemString.length() - 2), cap == 0 ? "infinite" : cap)); // FINISHME: Terrible
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

        if (core.items.get(item) >= core.storageCapacity || cap != 0 && core.items.get(item) > cap) Navigation.follow(new BuildPath(items, core.items.get(item) >= core.storageCapacity ? core.storageCapacity : cap)); // Start building when the core has over 1000 of everything.

        if (player.unit().maxAccepted(item) == 0) { // drop off
            if (player.within(core, itemTransferRange - tilesize * 2) && timer.get(30)) {
                Call.transferInventory(player, core);
            } else {
                if (player.unit().type.canBoost) player.boosting = true;
                new PositionWaypoint(core.x, core.y, itemTransferRange - tilesize * 4, itemTransferRange - tilesize * 4).run();
            }

        } else { // mine
            Tile tile = indexer.findClosestOre(player.x, player.y, item);
            player.unit().mineTile = tile;
            if (tile == null) return;

            player.boosting = player.unit().type.canBoost && !player.within(tile, tilesize * 2); // FINISHME: Distance based on formation radius rather than just moving super close
            new PositionWaypoint(tile.getX(), tile.getY(), tilesize, tilesize).run();
        }
    }

    @Override
    public float progress() {
        return 0;
    }

    @Override
    public void reset() {

    }

    @Override
    public Position next() {
        return null;
    }
}
