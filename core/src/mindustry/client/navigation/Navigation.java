package mindustry.client.navigation;

import arc.*;
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
    public static Navigator navigator;
    private static final Interval timer = new Interval();

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
        if (lastPath != null) lastPath.onFinish();
    }

    public static boolean isFollowing() {
        return currentlyFollowing != null && !isPaused;
    }

    public static void draw() {
        if (currentlyFollowing != null && Core.settings.getBool("drawpath")) {
            currentlyFollowing.draw();
        }

        if (state == NavigationState.RECORDING && recordedPath != null) {
            recordedPath.draw();
        }
    }

    public static void navigateTo(Position pos) {
        if (pos != null) navigateTo(pos.getX(), pos.getY());
    }

    public static void navigateTo(float drawX, float drawY) {
        Path.goTo(drawX, drawY, 0, 0, p -> Core.app.post(() -> {
            if (Core.settings.getBool("assumeunstrict")) return;
            follow(p);
            navigateToInternal(drawX, drawY);
        }));
    }

    private static void navigateToInternal(float drawX, float drawY) {
        Path.goTo(drawX, drawY, 0, 0, p -> {
            if (currentlyFollowing != p) return;
            if (Core.settings.getBool("pathnav")) clientThread.post(() -> navigateToInternal(drawX, drawY));
        });
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
