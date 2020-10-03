package mindustry.client.navigation;

import arc.math.*;
import arc.math.geom.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ItemPickupWaypoint extends Waypoint implements Position {
    public int sourceX, sourceY;
    public ItemStack items;
    private boolean done = false;

    public ItemPickupWaypoint(int sourceX, int sourceY, ItemStack items) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.items = items;
    }

    @Override
    public float getX() {
        return sourceX * tilesize;
    }

    @Override
    public float getY() {
        return sourceY * tilesize;
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
