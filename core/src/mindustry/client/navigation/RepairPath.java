package mindustry.client.navigation;

import arc.Core;
import arc.math.geom.Geometry;
import arc.math.geom.Position;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.gen.Building;

import static mindustry.Vars.*;

public class RepairPath extends Path {
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
        Building build = Geometry.findClosest(player.x, player.y, indexer.getDamaged(player.team()));
        if (build == null || player.unit() == null) return;
        player.shooting(player.unit().inRange(build));
        player.unit().aimLook(build);
        new PositionWaypoint(build.x, build.y, 16, 16).run();
    }

    @Override
    public float progress() {
        return indexer.getDamaged(player.team()).isEmpty() ? 1 : 0;
    }

    @Override
    public void reset() {

    }

    @Override
    public Position next() {
        return null;
    }
}
