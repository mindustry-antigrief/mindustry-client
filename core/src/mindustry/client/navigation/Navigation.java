package mindustry.client.navigation;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.*;
import mindustry.*;
import mindustry.client.navigation.dstar.DStarLite;
import mindustry.client.navigation.dstar.DStarState;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import java.util.ArrayList;
import java.util.List;

import static mindustry.Vars.*;

public class Navigation {
    public static Path currentlyFollowing = null;
    public static boolean isPaused = false;
    public static NavigationState state = NavigationState.NONE;
    public static Path recordedPath = null;
    public static Seq<Waypoint> recording = null;
    private static DStarLite dstar = null;
    private static final int resolutionModifier = 4;  // Everything is divided by this number to convert from world space to navigation space
    public static Seq<TurretPathfindingEntity> obstacles = new Seq<>();
    private static Vec2 targetPos = null;

    public static void follow(Path path) {
        currentlyFollowing = path;
        if (path == null){
            state = NavigationState.NONE;
        } else {
            state = NavigationState.FOLLOWING;
        }
    }

    public static void update() {
//        System.out.println(obstacles);
//        Draw.color(Color.green);
//        Draw.alpha(0.25f);
//        for (TurretPathfindingEntity obstacle : obstacles) {
//            Fill.circle(obstacle.x * tilesize, obstacle.y * tilesize, obstacle.range);
//        }
//        Draw.color();

        if (currentlyFollowing != null && !isPaused) {
            currentlyFollowing.follow();
            if (dstar != null) {
                if (Core.graphics.getFrameId() % 30 == 0) {
                    dstar = new DStarLite();
                    dstar.init(player.tileX() / Navigation.resolutionModifier, player.tileY() / Navigation.resolutionModifier,
                            Mathf.floor(targetPos.x / resolutionModifier) / tilesize, Mathf.floor(targetPos.y / resolutionModifier) / tilesize);

//                    dstar.updateStart(player.tileX() / resolutionModifier, player.tileY() / resolutionModifier);
                    for (TurretPathfindingEntity turret : obstacles) {
                        Geometry.circle(turret.x / resolutionModifier, turret.y / resolutionModifier,
                                Mathf.floor(turret.range / tilesize) / resolutionModifier,
                                (a, b) -> dstar.updateCell(a, b, 500));
                    }

                    if (dstar.replan()) {
                        Seq<Waypoint> points = new Seq<>();
                        List<DStarState> path = dstar.getPath();
                        path.forEach(point -> points.add(new PositionWaypoint(point.x * tilesize * resolutionModifier,
                                point.y * tilesize * resolutionModifier)));
                        points.remove(0);
                        follow(new WaypointPath(points));
                        currentlyFollowing.setShow(true);
                    }
                }
            }
            if (currentlyFollowing.isDone()) {
                currentlyFollowing.onFinish();
                currentlyFollowing = null;
                state = NavigationState.NONE;
                dstar = null;
                targetPos = null;
            }
        }
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
//        Seq<TurretPathfindingEntity> circularObstacles = new Seq<>();
//        for (Tile tile : world.tiles) {
//            if (tile != null) {
//                if (tile.block() instanceof Turret) {
//                    if (tile.team() != player.team()) {
//                        circularObstacles.add(new TurretPathfindingEntity(tile.x, tile.y, ((Turret) tile.block()).range));
//                    }
//                } else if (tile.block() == Blocks.spawn) {
//                    circularObstacles.add(new TurretPathfindingEntity(tile.x, tile.y, Vars.state.rules.dropZoneRadius));
//                }
//            }
//        }
//        System.out.println(Navigation.obstacles);

        dstar = new DStarLite();
        dstar.init(player.tileX() / Navigation.resolutionModifier, player.tileY() / Navigation.resolutionModifier,
                Mathf.floor(drawX / tilesize) / Navigation.resolutionModifier, Mathf.floor(drawY / tilesize) / Navigation.resolutionModifier);
        for (TurretPathfindingEntity turret : obstacles) {
            Geometry.circle(turret.x / resolutionModifier, turret.y / resolutionModifier,
                    Mathf.floor(turret.range / tilesize) / resolutionModifier,
                    (a, b) -> dstar.updateCell(a, b, 500));
        }

        if (dstar.replan()) {
            targetPos = new Vec2(drawX, drawY);
            Seq<Waypoint> points = new Seq<>();
            List<DStarState> path = dstar.getPath();
            path.forEach(point -> points.add(new PositionWaypoint(point.x * tilesize * resolutionModifier,
                    point.y * tilesize * resolutionModifier)));
            follow(new WaypointPath(points));
            currentlyFollowing.setShow(true);
        }

//        Seq<int[]> points = AStar.findPathTurretsDropZone(turrets, player.x, player.y, drawX, drawY, world.width(), world.height(), player.team(), dropZones);
//        if (points != null) {
//            Seq<Waypoint> waypoints = new Seq<>();
//            points.reverse();
//            for (int[] point : points) {
//                waypoints.add(new PositionWaypoint(point[0] * tilesize, point[1] * tilesize));
//            }
//            follow(new WaypointPath(waypoints));
//        }
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
