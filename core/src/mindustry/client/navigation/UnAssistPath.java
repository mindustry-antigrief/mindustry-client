package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.world.blocks.*;

import static mindustry.Vars.*;

public class UnAssistPath extends Path {
    public final Player target;
    public boolean follow;
    public final Seq<BuildPlan> toUndo = new Seq<>();
    private final Pool<BuildPlan> pool = Pools.get(BuildPlan.class, BuildPlan::new, 15_000);
    private boolean done;
    private final Interval timer = new Interval();

    static {
        // Remove placed blocks, place removed blocks
        Events.on(EventType.BlockBuildBeginEventBefore.class, e -> { // FINISHME: If a block is rotated twice by being placed over before the first event is processed, the block wont be reset properly. Is a fix as simple as returning if theres already something queued at (x,y)? I don't care to find out
            if (e.tile == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.unit != p.target.unit() || (e.breaking && !e.tile.block().isPlaceable())) return;

            if (e.tile.block() != Blocks.air) p.toUndo.add(p.pool.obtain().set(e.tile.x, e.tile.y, e.tile.build == null ? 0 : e.tile.build.rotation, e.tile.block(), e.tile.build == null ? null : e.tile.build.config()));
            else p.toUndo.add(p.pool.obtain().set(e.tile.x, e.tile.y));
        });

        // Undo configs
        Events.on(EventType.ConfigEventBefore.class, e -> {
            if (e.tile == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.player != p.target) return;

            ClientVars.configs.addFirst(new ConfigRequest(e.tile, e.tile.config()));
        });

        // Undo block rotates
        Events.on(EventType.BuildRotateEvent.class, e -> {
            if (e.build == null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.unit == null || e.unit.getPlayer() != p.target) return;

            boolean direction = ClientUtils.rotationDirection(e.previous, e.build.rotation);
            ClientVars.configs.addFirst(new ConfigRequest(e.build, !direction, true));
        });

        // Player left, finish processing then stop path
        Events.on(EventType.PlayerLeave.class, e -> {
            if (e.player != null || !(Navigation.currentlyFollowing instanceof UnAssistPath p) || e.player != p.target || !p.follow) return;
            p.follow = false;

            ClientVars.configs.add(() -> { // Mark the path as done once all configs are processed
                p.done = true;
            });
        });
    }

    {
        addListener(pool::clear); // Clear pool to prevent hogging memory
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
            if (target.unit().canBuild()) {
                BuildPlan plan = target.unit().buildPlan();
                if (plan != null) {
                    if (plan.initialized) {
                        if (plan.tile().build instanceof ConstructBlock.ConstructBuild build) {
                            if (build.current.buildCost > 10) {
                                if (plan.breaking) {
                                    toUndo.add(pool.obtain().set(plan.x, plan.y, build.rotation, build.current, build.lastConfig));
                                } else {
                                    toUndo.add(pool.obtain().set(plan.x, plan.y));
                                }
                            }
                        }
                    }
                }
            }
        } catch(Exception e) { Log.err(e.getMessage()); }

        if (follow) waypoint.set(target.x, target.y, 0f, 0f).run(); // FINISHME: Navigation
        else if (done && toUndo.any()){ // Target left, we are finishing up the building plans
            if (timer.get(0, 15)) toUndo.sort(Structs.comparingFloat(pl -> pl.dst2(player))); // Sort the plans by distance to the player every so often so that we're not flying around inefficiently
            waypoint.set(toUndo.first().x, toUndo.first().y);
        }
        else { // FINISHME: This is horrendous, it should really just enable the default movement instead
            Unit u = Vars.player.unit();
            boolean aimCursor = u.type.omniMovement && Vars.player.shooting && u.type.hasWeapons() && u.type.faceTarget && !(u instanceof Mechc && u.isFlying());
            if (aimCursor) u.lookAt(Angles.mouseAngle(u.x, u.y));
            else u.lookAt(u.prefRotation());
            u.moveAt(Vars.control.input instanceof DesktopInput in ? in.movement : ((MobileInput)Vars.control.input).movement);
            u.aim(u.type.faceTarget ? Core.input.mouseWorld() : Tmp.v1.trns(u.rotation, Core.input.mouseWorld().dst(u)).add(u.x, u.y));
        }

        if (toUndo.any()) { // Remove all finished plans and remove duplicates
            IntSet contains = new IntSet();
            var it = toUndo.iterator();
            while (it.hasNext()) {
                var p = it.next();
                if (p.isDone()) { // Remove finished plans
                    it.remove();
                    Vars.player.unit().plans.remove(p);
                    pool.free(p);
                } else if (!contains.add(Point2.pack(p.x, p.y))) { // Keep only one plan for each tile FINISHME: This seems like a bad idea, this is going to cause issues, is it not?
                    it.remove();
                    pool.free(p);
                } else Vars.player.unit().addBuild(p); // Add a plan to the queue (only the first unfinished plan for any given tile)
            }
        }
    }

    @Override
    public float progress() {
        return target == null || done && toUndo.isEmpty() ? 1f : 0f;
    }

    @Override
    public void reset() {
        toUndo.clear().shrink();
    }

    @Override
    public Position next() {
        return null;
    }

    @Override
    public synchronized void draw() {
        if (target == null) return;
        target.unit().drawBuildPlans();
    }
}
