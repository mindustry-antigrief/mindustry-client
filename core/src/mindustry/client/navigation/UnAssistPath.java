package mindustry.client.navigation;

import arc.math.geom.Position;
import arc.struct.Queue;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Builderc;
import mindustry.gen.Player;

public class UnAssistPath extends Path {
    public final Player assisting;
    public Seq<BuildPlan> toUndo = new Seq<>();

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
                //slow?
//                BuildPlan plan = toUndo.min(item -> Vars.player.dst2(item));
//                ((Builderc) Vars.player.unit()).addBuild(plan);
                for (BuildPlan it : toUndo) {
                    if (it.isDone()) {
                        toUndo.remove(it);
                    }
                    ((Builderc) Vars.player.unit()).addBuild(it);
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
