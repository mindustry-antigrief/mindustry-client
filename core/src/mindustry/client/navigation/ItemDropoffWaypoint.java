package mindustry.client.navigation;

import mindustry.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

public class ItemDropoffWaypoint extends Waypoint {
    public int destinationX, destinationY;
    private boolean done = false;

    public ItemDropoffWaypoint(int destinationX, int destinationY) {
        this.destinationX = destinationX;
        this.destinationY = destinationY;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (Vars.player.within(destinationX * Vars.tilesize, destinationY * Vars.tilesize, Vars.itemTransferRange)) {
            Tile tile = Vars.world.tile(destinationX, destinationY);
            if (tile.build == null) {
                return;
            }
            Call.transferInventory(Vars.player, tile.build);
            done = true;
        }
    }

    @Override
    public void draw() {}
}
