package mindustry.client.navigation;

import arc.math.geom.Position;
import arc.struct.Queue;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Builderc;
import mindustry.gen.Player;

public class UnAssistPath extends Path {
    public final Player assisting;
    public Queue<BuildPlan> toUndo = new Queue<>();

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
        if (Vars.player.unit() instanceof Builderc) {
            ((Builderc) Vars.player.unit()).clearBuilding();
            if (!toUndo.isEmpty()) {
                ((Builderc) Vars.player.unit()).addBuild(toUndo.first());
                if (toUndo.first().isDone()) {
                    toUndo.removeFirst();
                }
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
