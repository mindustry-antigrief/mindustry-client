package mindustry.client.navigation;

import mindustry.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

public class ItemPickupWaypoint extends Waypoint {
    public int sourceX, sourceY;
    public ItemStack items;
    private boolean done = false;

    public ItemPickupWaypoint(int sourceX, int sourceY, ItemStack items) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.items = items;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (Vars.player.within(sourceX * Vars.tilesize, sourceY * Vars.tilesize, Vars.itemTransferRange)) {
            Tile tile = Vars.world.tile(sourceX, sourceY);
            if (tile.build == null) {
                return;
            }
            Call.requestItem(Vars.player, tile.build, items.item, items.amount);
            done = true;
        }
    }

    @Override
    public void draw() {}
}
