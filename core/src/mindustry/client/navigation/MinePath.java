package mindustry.client.navigation;

import arc.math.geom.*;
import arc.util.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MinePath extends Path {
    static Interval timer = new Interval();
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
        Item item = player.team().data().mineItems.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));
        if (item == null) return;

        if (player.unit().maxAccepted(item) == 0) { // drop off
            if (player.within(core, itemTransferRange - tilesize) && timer.get(30)) {
                Call.transferInventory(player, core);
            } else {
                if (player.unit().type.canBoost) player.boosting = true;
                new PositionWaypoint(core.x, core.y, itemTransferRange - tilesize * 2, itemTransferRange - tilesize * 2).run();
            }

        } else { // mine
            Tile tile = indexer.findClosestOre(player.unit(), item);
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
