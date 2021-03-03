package mindustry.client.navigation.waypoints;

import arc.math.*;
import arc.math.geom.*;
import mindustry.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class PayloadPickupWaypoint extends Waypoint implements Position {
    public final int tileX, tileY;
    private boolean done = false;

    public PayloadPickupWaypoint(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    @Override
    public float getX() {
        return tileX * tilesize;
    }

    @Override
    public float getY() {
        return tileY * tilesize;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (Vars.player.within(getX(), getY(), 1f)) {
            Call.requestBuildPayload(Vars.player, world.tile(tileX, tileY).build);
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
