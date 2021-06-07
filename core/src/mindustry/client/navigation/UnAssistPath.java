package mindustry.client.navigation;

import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

public class UnAssistPath extends Path {
    public final Player assisting;
    public Seq<BuildPlan> toUndo = new Seq<>();

    public UnAssistPath(Player toAssist) {
        assisting = toAssist;
    }

    @Override
    public void setShow(boolean show) {}

    @Override
    public boolean getShow() {
        return false;
    }

    @Override
    public void follow() {
        if (assisting == null || Vars.player == null) {
            return;
        }
        if (assisting.unit() == null || Vars.player.unit() == null) {
            return;
        }

        try {
            if (assisting.unit().canBuild()) {
                BuildPlan plan = assisting.unit().buildPlan();
                if (plan != null) {
                    if (plan.initialized) {
                        Tile tile = Vars.world.tile(plan.x, plan.y);
                        if (tile.build instanceof ConstructBlock.ConstructBuild) {
                            ConstructBlock.ConstructBuild build = (ConstructBlock.ConstructBuild) tile.build;
                            if (build.current.buildCost > 10) {
                                if (plan.breaking) {
                                    toUndo.add(new BuildPlan(plan.x, plan.y, build.rotation, build.current, build.lastConfig));
                                } else {
                                    toUndo.add(new BuildPlan(plan.x, plan.y));
                                }
                            }
                        }
                    }
                }
            }
        } catch(Exception e){Log.info(e.getMessage());}

        new PositionWaypoint(assisting.x, assisting.y, 0f).run();
        if (Vars.player.unit() instanceof Builderc) {
            ((Builderc) Vars.player.unit()).clearBuilding();
            IntSet contains = new IntSet();
            toUndo = toUndo.filter(plan -> {
                int pos = Point2.pack(plan.x, plan.y);
                if (contains.contains(pos)){
                    return false;
                } else {
                    contains.add(pos);
                    return true;
                }
            });
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
    public float progress() {
        return assisting == null? 1f : 0f;
    }

    @Override
    public void reset() {
        toUndo.clear();
    }

    @Override
    public Position next() {
        return null;
    }
}
