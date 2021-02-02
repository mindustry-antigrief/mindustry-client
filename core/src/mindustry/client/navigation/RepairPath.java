package mindustry.client.navigation;

import arc.math.geom.Geometry;
import arc.math.geom.Position;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.gen.Building;

import static mindustry.Vars.*;

public class RepairPath extends Path {
    @Override
    public void setShow(boolean show) {}

    @Override
    boolean isShown() {
        return false;
    }

    @Override
    void follow() {
        Building build = Geometry.findClosest(player.x, player.y, indexer.getDamaged(player.team()));
        if (build == null || (player.unit() != null && player.unit().type != null && !player.unit().type.canHeal)) return;
        player.shooting(player.unit().inRange(build));
        player.unit().aimLook(build);
        new PositionWaypoint(build.x, build.y, 8, 8).run();
    }

    @Override
    float progress() {
        if (player.unit() != null && (!player.unit().type.canHeal || indexer.getDamaged(player.team()).isEmpty())) {
            player.shooting(false);
            return 1;
        }
        return 0;
    }

    @Override
    public void reset() {

    }

    @Override
    Position next() {
        return null;
    }
}
