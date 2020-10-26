package mindustry.client.navigation;

import arc.math.geom.Position;
import mindustry.Vars;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.gen.Builderc;
import mindustry.gen.Player;

public class AssistPath extends Path {
    public final Player assisting;

    public AssistPath(Player toAssist) {
        assisting = toAssist;
    }

    @Override
    void setShow(boolean show) {}

    @Override
    boolean isShown() {
        return false;
    }

    @Override
    void follow() {
        if (assisting == null || Vars.player == null) {
            return;
        }
        if (assisting.unit() == null || Vars.player.unit() == null) {
            return;
        }

        new PositionWaypoint(assisting.x, assisting.y, assisting.unit().hitSize + Vars.player.unit().hitSize).run();
        if (assisting.unit() instanceof Builderc && Vars.player.unit() instanceof Builderc) {
            ((Builderc) Vars.player.unit()).clearBuilding();
            if (((Builderc) assisting.unit()).activelyBuilding()) {
                ((Builderc) Vars.player.unit()).addBuild(((Builderc) assisting.unit()).buildPlan());
            }
        }
    }

    @Override
    float progress() {
        return assisting == null? 1f : 0f;
    }

    @Override
    Position next() {
        return null;
    }
}
