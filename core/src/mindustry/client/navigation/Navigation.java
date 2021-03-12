package mindustry.client.navigation;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.navigation.waypoints.*;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static mindustry.Vars.*;

public class Navigation {
    @Nullable public static Path currentlyFollowing = null;
    public static boolean isPaused = false;
    @NotNull public static NavigationState state = NavigationState.NONE;
    @Nullable public static Path recordedPath = null;
    @Nullable public static Seq<Waypoint> recording = null;
    @NotNull public static HashSet<TurretPathfindingEntity> obstacles = new HashSet<>();
    @Nullable private static Vec2 targetPos = null;
    public static Navigator navigator;
    @NotNull private static final Interval timer = new Interval();

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
        if (timer.get(600)) obstacles.clear(); // Refresh all obstacles every 600s since sometimes they don't get removed properly for whatever reason TODO: Check if this happens because it still runs update even when dead, if so just the removal of the obstacle

        if (targetPos != null && playerNavigator.taskQueue.size() == 0) { // must be navigating, TODO: dejank
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
        if (currentlyFollowing != null) {
            currentlyFollowing.onFinish();
        }
        currentlyFollowing = null;
        state = NavigationState.NONE;
        targetPos = null;
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

    public static void navigateTo(Position pos) {
        navigateTo(pos.getX(), pos.getY());
    }

    public static void navigateTo(float drawX, float drawY) {
        state = NavigationState.FOLLOWING;
        if (obstacles.isEmpty()) {
            follow(new WaypointPath<>(Seq.with(new PositionWaypoint(Mathf.clamp(drawX, 0, world.unitWidth()), Mathf.clamp(drawY, 0, world.unitHeight())))));
            currentlyFollowing.setShow(true);
            targetPos = new Vec2(drawX, drawY);
            return;
        }

        targetPos = new Vec2(drawX, drawY);
        playerNavigator.taskQueue.post(() -> {
            Vec2[] points = navigator.navigate(new Vec2(player.x, player.y), new Vec2(drawX, drawY), obstacles.toArray(new TurretPathfindingEntity[0]));
            if (points != null) {
                Seq<PositionWaypoint> waypoints = new Seq<>();
                for (Vec2 point : points) {
                    waypoints.add(new PositionWaypoint(point.x, point.y));
                }
                waypoints.reverse();

                if (waypoints.any()) {
                    while (waypoints.size > 1 && !waypoints.first().within(player, 8)) waypoints.remove(0);
                    if (waypoints.size > 1) waypoints.remove(0);
                    if (waypoints.size > 1) waypoints.remove(0);
                    if (targetPos != null && targetPos.x == drawX && targetPos.y == drawY) { // Don't create new path if stopFollowing has been run
                        follow(new WaypointPath<>(waypoints));
                        targetPos = new Vec2(drawX, drawY);
                        currentlyFollowing.setShow(true);
                    }
                }
            }
        });
    }

    public static void startRecording() {
        state = NavigationState.RECORDING;
        recording = new Seq<>();
    }

    public static void stopRecording() {
        if (recording == null) return;
        state = NavigationState.NONE;
        recordedPath = new WaypointPath<>(recording);
        recording = null;
    }

    public static void addWaypointRecording(Waypoint waypoint) {
        if (state != NavigationState.RECORDING) {
            return;
        }
        if (recording == null) {
            recording = new Seq<>();
        }
        recording.add(waypoint);
        recordedPath = new WaypointPath<>(recording);
        recordedPath.setShow(true);
    }
}
