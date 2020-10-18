package mindustry.client.navigation;

import arc.math.geom.Position;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Builderc;
import mindustry.gen.Player;

public class UnAssistPath extends Path {
    public final Player assisting;

    public UnAssistPath(Player toAssist) {
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
                BuildPlan plan = ((Builderc) assisting.unit()).buildPlan().copy();
                plan.breaking = !plan.breaking;
                ((Builderc) Vars.player.unit()).addBuild(plan);
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
