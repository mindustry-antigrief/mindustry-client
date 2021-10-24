package mindustry.client.navigation.waypoints;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class ItemDropoffWaypoint extends Waypoint implements Position {
    public Posc pos;
    private boolean done = false;
    private static Interval dropTimer = new Interval();

    public ItemDropoffWaypoint(Posc pos) {
        this.pos = pos;
    }

    @Override
    public float getX() {
        return pos.x();
    }

    @Override
    public float getY(){
        return pos.y();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (Vars.player.within(getX(), getY(), Vars.itemTransferRange - tilesize * 3) && dropTimer.get(30)) {
            if (pos.tileOn().build == null) return;

            Call.transferInventory(Vars.player, pos.tileOn().build);
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
