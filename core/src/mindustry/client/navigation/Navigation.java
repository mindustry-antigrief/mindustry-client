package mindustry.client.navigation;

import arc.math.geom.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.navigation.waypoints.*;

import java.util.*;

public class Navigation {
    @Nullable public static Path currentlyFollowing = null;
    public static boolean isPaused = false;
    public static NavigationState state = NavigationState.NONE;
    @Nullable public static WaypointPath<Waypoint> recordedPath = null;
    public static final Set<TurretPathfindingEntity> obstacles = Collections.synchronizedSet(new HashSet<>());
    private static final Vec2 targetPos = new Vec2(-1, -1);
    public static Navigator navigator;
    private static final Interval timer = new Interval();

    public static void follow(Path path, boolean repeat) {
        stopFollowing(!(path instanceof WaypointPath));
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

        if (!targetPos.within(-1, -1, 1)) { // Must be navigating
            var path = Path.goTo(targetPos);
            if (!targetPos.within(-1, -1, 1)) follow(path); // Make sure we still want to navigate
        }

        if (currentlyFollowing != null && !isPaused && !Vars.state.isPaused()) {
            currentlyFollowing.follow();
            if (currentlyFollowing != null && currentlyFollowing.isDone()) {
                stopFollowing();
            }
        }
    }

    public static void stopFollowing(boolean removeTarget) {
        var lastPath = currentlyFollowing;

        currentlyFollowing = null;
        state = NavigationState.NONE;
        if (removeTarget) targetPos.set(-1, -1);
        if (lastPath != null) lastPath.onFinish();
    }

    public static void stopFollowing() {
        stopFollowing(true);
    }

    public static boolean isFollowing() {
        return currentlyFollowing != null && !isPaused;
    }

    public static void draw() {
        if (currentlyFollowing != null && (!(currentlyFollowing instanceof WaypointPath) || !targetPos.within(-1, -1, 1))) {
            currentlyFollowing.draw();
        }

        if (state == NavigationState.RECORDING && recordedPath != null) {
            recordedPath.draw();
        }
    }

    public static Path navigateTo(Position pos) {
        if (pos == null) return Path.waypoints; // Apparently this can happen somehow?
        return navigateTo(pos.getX(), pos.getY());
    }

    public static Path navigateTo(float drawX, float drawY) {
        targetPos.set(drawX, drawY);
        return Path.waypoints;
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
