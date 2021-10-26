package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.client.utils.*;
import mindustry.core.*;

import java.util.*;

import static mindustry.Vars.*;

public class Navigation {
    @Nullable public static Path currentlyFollowing = null;
    public static boolean isPaused = false;
    public static NavigationState state = NavigationState.NONE;
    @Nullable public static WaypointPath<Waypoint> recordedPath = null;
    public static final HashSet<TurretPathfindingEntity> obstacles = new HashSet<>();
    @Nullable private static Vec2 targetPos = null;
    public static Navigator navigator;
    private static final Interval timer = new Interval();
    private static Seq<PositionWaypoint> waypoints;

    public static void follow(Path path, boolean repeat) {
        stopFollowing();
        if (path == null) return;
        currentlyFollowing = path;
        currentlyFollowing.init();
        state = NavigationState.FOLLOWING;
        currentlyFollowing.repeat = repeat;
    }

    public static void follow(Path path) {
        follow(path, false);
    }

    public static void update() {
        if (timer.get(600)) obstacles.clear(); // Refresh all obstacles every 600s since sometimes they don't get removed properly for whatever reason FINISHME: Check if this happens because it still runs update even when dead, if so just the removal of the obstacle

        if (targetPos != null && clientThread.taskQueue.size() == 0) { // Must be navigating FINISHME: dejank
            navigateTo(targetPos);
        }

        if (currentlyFollowing != null && !isPaused && !Vars.state.isPaused()) {
            currentlyFollowing.follow();
            if (currentlyFollowing != null && currentlyFollowing.isDone()) {
                stopFollowing();
            }
        }
    }

    public static void stopFollowing() {
        var lastPath = currentlyFollowing;

        currentlyFollowing = null;
        state = NavigationState.NONE;
        targetPos = null;
        if (lastPath != null) lastPath.onFinish();
    }

    public static boolean isFollowing() {
        return currentlyFollowing != null && !isPaused;
    }

    public static void draw() {
        if (currentlyFollowing != null) {
            currentlyFollowing.draw();
        }

        if (state == NavigationState.RECORDING && recordedPath != null) {
            recordedPath.draw();
        }

//        Draw.color(Color.green, 0.2f);
//        for (TurretPathfindingEntity turret : obstacles) {
//            Fill.circle(turret.x, turret.y, turret.radius);
//        }
//        Draw.color();
    }

    public static Path navigateTo(Position pos) {
        if (pos == null) return null; // Apparently this can happen somehow?
        return navigateTo(pos.getX(), pos.getY());
    }

    public static Path navigateTo(float drawX, float drawY) {
        if (Core.settings.getBool("assumeunstrict")) {
            NetClient.setPosition(Mathf.clamp(drawX, 0, world.unitWidth()), Mathf.clamp(drawY, 0, world.unitHeight())); // FINISHME: Detect whether or not the player is at their new destination, if not run with assumeunstrict off
            player.snapInterpolation();
            return null;
        }

        state = NavigationState.FOLLOWING;
        if (obstacles.isEmpty() && !Vars.state.hasSpawns() && !UtilitiesKt.flood() && player.unit().isFlying()) {
            follow(new WaypointPath<>(new PositionWaypoint(
                Mathf.clamp(drawX, 0, world.unitWidth()),
                Mathf.clamp(drawY, 0, world.unitHeight()))));
            currentlyFollowing.setShow(true);
            targetPos = new Vec2(drawX, drawY);
            return null;
        }

        targetPos = new Vec2(drawX, drawY);
        clientThread.taskQueue.post(() -> {
            waypoints = Seq.with(navigator.navigate(new Vec2(player.x, player.y), new Vec2(drawX, drawY), Navigation.obstacles));

            if (waypoints.any()) {
                while (waypoints.size > 1 && waypoints.min(wp -> wp.dst(player)) != waypoints.first()) Pools.free(waypoints.remove(0));
                if (waypoints.size > 1) Pools.free(waypoints.remove(0));
                if (waypoints.size > 1 && player.unit().isFlying()) Pools.free(waypoints.remove(0)); // Ground units cant properly turn corners if we remove 2 waypoints.
                if (targetPos != null && targetPos.x == drawX && targetPos.y == drawY) { // Don't create new path if stopFollowing has been run
                    if (currentlyFollowing instanceof WaypointPath p) p.set(waypoints);
                    else follow(new WaypointPath<>(waypoints));
                    targetPos = new Vec2(drawX, drawY);
                    currentlyFollowing.setShow(true);
                }
                Pools.freeAll(waypoints);
            }
        });
        return currentlyFollowing;
    }

    public static void startRecording() {
        state = NavigationState.RECORDING;
        recordedPath = new WaypointPath<>();
    }

    public static void stopRecording() {
        state = NavigationState.NONE;
    }

    public static void addWaypointRecording(Waypoint waypoint) {
        if (state != NavigationState.RECORDING) return;
        recordedPath.add(waypoint);
        recordedPath.setShow(true);
    }
}
