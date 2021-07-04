package mindustry.client.navigation.waypoints;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ItemDropoffWaypoint extends Waypoint implements Position {
    public int destinationX, destinationY;
    private boolean done = false;
    private static Interval dropTimer = new Interval();

    public ItemDropoffWaypoint(int destinationX, int destinationY) {
        this.destinationX = destinationX;
        this.destinationY = destinationY;
    }

    @Override
    public float getX() {
        return destinationX * tilesize;
    }

    @Override
    public float getY(){
        return destinationY * tilesize;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (Vars.player.within(destinationX * Vars.tilesize, destinationY * Vars.tilesize, Vars.itemTransferRange) && dropTimer.get(30)) {
            Tile tile = Vars.world.tile(destinationX, destinationY);
            if (tile.build == null) {
                return;
            }
            Call.transferInventory(Vars.player, tile.build);
            done = true;
        } else {
            float direction = player.angleTo(this);
            float x = Mathf.cosDeg(direction) * 2f;
            float y = Mathf.sinDeg(direction) * 2f;
            x = Mathf.clamp(x / 10, -1f, 1f);
            y = Mathf.clamp(y / 10, -1f, 1f);
            control.input.updateMovementCustom(player.unit(), x, y, direction);
        }
    }

    @Override
    public void draw() {}
}
