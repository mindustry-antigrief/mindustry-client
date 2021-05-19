package mindustry.client.navigation;

import arc.math.geom.*;
import arc.util.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.entities.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class RepairPath extends Path {
    Building current;
    Interval delay = new Interval();
    @Override
    public void init() {
        addListener(() -> player.shooting(false));
    }

    @Override
    public void setShow(boolean show) {}

    @Override
    public boolean getShow() {
        return false;
    }

    @Override
    public void follow() {
        Building build = Units.findDamagedTile(player.team(), player.x, player.y);
        if (build == null || player.unit() == null || (build != current && !delay.check(0, 30))) return;
        current = build;
        delay.reset(0, 0);
        player.shooting(player.unit().inRange(build));
        player.unit().aimLook(build);
        new PositionWaypoint(build.x, build.y, 16, 16).run(); // TODO: Distance based on formation size?
    }

    @Override
    public float progress() {
        return Units.findDamagedTile(player.team(), player.x, player.y) == null ? 1 : 0;
    }

    @Override
    public void reset() {
    }

    @Override
    public Position next() {
        return null;
    }
}
