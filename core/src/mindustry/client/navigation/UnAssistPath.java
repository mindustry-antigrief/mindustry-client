package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

public class UnAssistPath extends Path {
    public Player target;
    public Seq<BuildPlan> toUndo = new Seq<>();
    public Seq<ConfigRequest> toConfig = new Seq<>();

    { // FINISHME: Make this static as we cant even remove events and that may be a problem down the road
        // Remove placed blocks, place removed blocks
        Events.on(EventType.BlockBuildBeginEventBefore.class, e -> {
            if (e.unit == null || e.unit != target.unit() || e.tile == null || (e.breaking && !e.tile.block().isVisible())) return;

            if (e.breaking) toUndo.add(new BuildPlan(e.tile.x, e.tile.y, e.tile.build == null ? 0 : e.tile.build.rotation, e.tile.block(), e.tile.build == null ? null : e.tile.build.config()));
            else toUndo.add(new BuildPlan(e.tile.x, e.tile.y));
        });

        // Undo configs
        Events.on(EventType.ConfigEventBefore.class, e -> {
            if (e.player != target || e.tile == null) return;

            toConfig.add(new ConfigRequest(e.tile.tileX(), e.tile.tileY(), e.tile.config()));
        });

        // Undo block rotates
        Events.on(EventType.BlockRotateEvent.class, e -> {
            if (e.player == null || e.player != target || e.build == null) return;

            toConfig.add(new ConfigRequest(e.build.tileX(), e.build.tileY(), !e.direction, true));
        });
    }
    public UnAssistPath(Player target) {
        this.target = target;
    }

    @Override
    public void setShow(boolean show) {}

    @Override
    public boolean getShow() {
        return false;
    }

    @Override
    public void follow() {
        if (target == null || Vars.player == null) return;

        try {
            if (target.unit().canBuild()) { // FINISHME: What even
                BuildPlan plan = target.unit().buildPlan();
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
        } catch(Exception e) { Log.err(e.getMessage()); }

        waypoint.set(target.x, target.y, 0f, 0f).run();

        Vars.player.unit().clearBuilding();
        IntSet contains = new IntSet();
        toUndo = toUndo.filter(plan -> { // FINISHME: ???
            int pos = Point2.pack(plan.x, plan.y);
            if (contains.contains(pos)) {
                return false;
            } else {
                contains.add(pos);
                return true;
            }
        });

        if (toUndo.any()) {
            for (BuildPlan it : toUndo) {
                if (it.isDone()) toUndo.remove(it);
                else Vars.player.unit().addBuild(it);
            }
        }

        if (toConfig.any() && ClientVars.configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
            try {
                toConfig.remove(0).run();
            } catch (Exception e) {
                Log.err(e);
            }
        }
    }

    @Override
    public float progress() {
        return target == null ? 1f : 0f;
    }

    @Override
    public void reset() {
        toUndo.clear();
        toConfig.clear();
    }

    @Override
    public Position next() {
        return null;
    }

    @Override
    public void draw() {
        if (target == null) return;
        target.unit().drawBuildPlans();
    }
}
