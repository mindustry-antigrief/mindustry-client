package mindustry.client.navigation;

import arc.math.geom.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MinePath extends Path {
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
            if (player.within(core, itemTransferRange - tilesize * 2)) {
                Call.transferInventory(player, core);
            } else {
                new PositionWaypoint(core.x, core.y, itemTransferRange - tilesize * 2, itemTransferRange - tilesize * 2).run();
            }
        } else { // mine
            Tile tile = indexer.findClosestOre(player.unit(), item);
            player.unit().mineTile = tile;
            if (tile == null) return;
            new PositionWaypoint(tile.getX(), tile.getY(), 16, 16).run();
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
