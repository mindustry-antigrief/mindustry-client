package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MinePath extends Path {
    Seq<Item> items = new Seq<>(16);
    StringBuilder itemString = new StringBuilder();
    static Interval timer = new Interval();

    public MinePath() {
        items = player.team().data().mineItems;
    }

    public MinePath(Seq<Item> mineItems) {
        items = mineItems;
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
                }
                else player.sendMessage(Core.bundle.format("client.path.builder.invalid", arg));
            }
        }
        if (items.isEmpty()) {
            player.sendMessage(Core.bundle.get("client.path.miner.allinvalid"));
            items = player.team().data().mineItems;
        } else {
            player.sendMessage(Core.bundle.format("client.path.miner.mining", itemString.substring(0, itemString.length() - 2))); // TODO: Terrible
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
        Building core = player.closestCore();
        if (core == null) return;
        Item item = items.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));
        if (item == null) return;

        if (Core.settings.getInt("minepathcap") != 0 && core.items.get(item) > Core.settings.getInt("minepathcap")) Navigation.follow(new BuildPath(items)); // Start building when the core has over 1000 of everything.

        if (player.unit().maxAccepted(item) == 0) { // drop off
            if (player.within(core, itemTransferRange - tilesize * 2) && timer.get(30)) {
                Call.transferInventory(player, core);
            } else {
                if (player.unit().type.canBoost) player.boosting = true;
                new PositionWaypoint(core.x, core.y, itemTransferRange - tilesize * 3, itemTransferRange - tilesize * 3).run();
            }

        } else { // mine
            Tile tile = indexer.findClosestOre(player.x, player.y, item);
            player.unit().mineTile = tile;
            if (tile == null) return;

            player.boosting = player.unit().type.canBoost && !player.within(tile, tilesize * 2); // TODO: Distance based on formation radius rather than just moving super close
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
