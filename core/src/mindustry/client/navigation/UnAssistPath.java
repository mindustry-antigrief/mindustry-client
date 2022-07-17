package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

public class UnAssistPath extends Path {
    public Player target;
    public boolean follow;
    public Seq<BuildPlan> toUndo = new Seq<>();

    static {
        // Remove placed blocks, place removed blocks
        Events.on(EventType.BlockBuildBeginEventBefore.class, e -> {
            if (e.tile == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.unit != p.target.unit() || (e.breaking && !e.tile.block().isVisible())) return;

            if (e.breaking) p.toUndo.add(new BuildPlan(e.tile.x, e.tile.y, e.tile.build == null ? 0 : e.tile.build.rotation, e.tile.block(), e.tile.build == null ? null : e.tile.build.config()));
            else p.toUndo.add(new BuildPlan(e.tile.x, e.tile.y));
        });

        // Undo configs
        Events.on(EventType.ConfigEventBefore.class, e -> {
            if (e.tile == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.player != p.target) return;

            ClientVars.configs.add(new ConfigRequest(e.tile, e.tile.config()));
        });

        // Undo block rotates
        Events.on(EventType.BlockRotateEvent.class, e -> {
            if (e.build == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.player != p.target) return;

            ClientVars.configs.add(new ConfigRequest(e.build, !e.direction, true));
        });
    }

    public UnAssistPath(Player target, boolean follow) {
        this.target = target;
        this.follow = follow;
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

        if(follow) waypoint.set(target.x, target.y, 0f, 0f).run(); // FINISHME: Navigation
        else { // FINISHME: This is horrendous, it should really just enable the default movement instead
            Unit u = Vars.player.unit();
            boolean aimCursor = u.type.omniMovement && Vars.player.shooting && u.type.hasWeapons() && u.type.faceTarget && !(u instanceof Mechc && u.isFlying()) && u.type.rotateShooting;
            if (aimCursor) u.lookAt(Angles.mouseAngle(u.x, u.y));
            else u.lookAt(u.prefRotation());
            u.moveAt(Vars.control.input instanceof DesktopInput in ? in.movement : ((MobileInput)Vars.control.input).movement);
            u.aim(u.type.faceTarget ? Core.input.mouseWorld() : Tmp.v1.trns(u.rotation, Core.input.mouseWorld().dst(u)).add(u.x, u.y));
        }

        IntSet contains = new IntSet();
        toUndo.filter(plan -> { // FINISHME: ???
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
                if (it.isDone()) {
                    toUndo.remove(it);
                    Vars.player.unit().plans.remove(it);
                }
                else Vars.player.unit().addBuild(it);
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
