package mindustry.client.navigation;

import arc.math.geom.Point2;
import arc.math.geom.Position;
import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Builderc;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;

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

        if (assisting.unit().canBuild()) {
            BuildPlan plan = assisting.unit().buildPlan();
            if (plan != null) {
                if (plan.initialized) {
                    Tile tile = Vars.world.tile(plan.x, plan.y);
                    if (tile.build instanceof ConstructBlock.ConstructBuild) {
                        ConstructBlock.ConstructBuild build = (ConstructBlock.ConstructBuild) tile.build;
                        if (build.cblock.buildCost > 10) {
                            if (plan.breaking) {
                                toUndo.add(new BuildPlan(plan.x, plan.y, build.rotation, build.cblock, build.lastConfig));
                            } else {
                                toUndo.add(new BuildPlan(plan.x, plan.y));
                            }
                        }
                    }
                }
            }
        }

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
    float progress() {
        return assisting == null? 1f : 0f;
    }

    @Override
    Position next() {
        return null;
    }
}
