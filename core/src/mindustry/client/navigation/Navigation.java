package mindustry.client.navigation;

import arc.math.geom.Vec2;
import arc.struct.*;
import arc.util.Interval;
import mindustry.Vars;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.client.navigation.waypoints.Waypoint;

import static mindustry.Vars.*;

public class Navigation {
    public static Path currentlyFollowing = null;
    public static boolean isPaused = false;
    public static NavigationState state = NavigationState.NONE;
    public static Path recordedPath = null;
    public static Seq<Waypoint> recording = null;
    public static Seq<TurretPathfindingEntity> obstacles = new Seq<>();
    private static Vec2 targetPos = null;
    private static Seq<TurretPathfindingEntity> obstaclesNotEmpty = new Seq<>();
    private static final Interval timer = new Interval();

    public static void follow(Path path, boolean repeat) {
        stopFollowing();
        currentlyFollowing = path;
        if (path == null){
            state = NavigationState.NONE;
        } else {
            state = NavigationState.FOLLOWING;
            currentlyFollowing.repeat = repeat;
        }
    }

    public static void follow(Path path) {
        follow(path, false);
    }

    public static void update() {
        if (targetPos != null) { // must be navigating, TODO: dejank
            if (timer.get(10)) {
                navigateTo(targetPos.x, targetPos.y);
            }
        }

        if (currentlyFollowing != null && !isPaused) {
            currentlyFollowing.follow();
            if (currentlyFollowing.isDone()) {
                stopFollowing();
            }
        }
        obstaclesNotEmpty = obstacles;
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
    }

    public static void navigateTo(float drawX, float drawY) {
        if (obstaclesNotEmpty.isEmpty()) {
            targetPos = new Vec2(drawX, drawY);
            follow(new WaypointPath(Seq.with(new PositionWaypoint(drawX, drawY))));
            return;
        }
        playerNavigator.taskQueue.post(() -> {
            Seq<int[]> points = AStar.findPathWithObstacles(player.x, player.y, drawX, drawY, world.width(), world.height(), player.team(), obstaclesNotEmpty);
            if (points != null) {
                Seq<Waypoint> waypoints = new Seq<>();
                points.reverse();
                for (int[] point : points) {
                    waypoints.add(new PositionWaypoint(point[0] * tilesize, point[1] * tilesize));
                }
                if (waypoints.any()) {
                    if (waypoints.size > 1) {
                        waypoints.remove(0);
                    }
                    follow(new WaypointPath(waypoints));
                    currentlyFollowing.setShow(true);
                    targetPos = new Vec2(drawX, drawY);
                }
            }
        });
    }

    public static void startRecording() {
        state = NavigationState.RECORDING;
        recording = new Seq<>();
    }

    public static void stopRecording() {
        state = NavigationState.NONE;
        recordedPath = new WaypointPath(recording);
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
        recordedPath = new WaypointPath(recording);
        recordedPath.setShow(true);
    }
}
