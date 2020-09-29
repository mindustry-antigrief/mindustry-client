package mindustry.client.navigation;

import arc.struct.*;
import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

public class Navigation {
    public static Path currentlyFollowing = null;
    public static boolean isPaused = false;

    public static void follow(Path path) {
        currentlyFollowing = path;
    }

    public static void update() {
        if (currentlyFollowing != null && !isPaused) {
            currentlyFollowing.follow();
            if (currentlyFollowing.isDone()) {
                currentlyFollowing.onFinish();
                currentlyFollowing = null;
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
    }

    public static void navigateTo(float drawX, float drawY) {
        Seq<TurretBuild> turrets = new Seq<>();
        Seq<TurretPathfindingEntity> dropZones = new Seq<>();
        for (Tile tile : world.tiles) {
            if (tile != null) {
                if (tile.block() instanceof Turret) {
                    if (tile.team() != player.team()) {
                        turrets.add((TurretBuild)tile.build);
                    }
                } else if (tile.block() == Blocks.spawn) {
                    dropZones.add(new TurretPathfindingEntity(tile.x, tile.y, state.rules.dropZoneRadius));
                }
            }
        }

        Seq<int[]> points = AStar.findPathTurretsDropZone(turrets, player.x, player.y, drawX, drawY, world.width(), world.height(), player.team(), dropZones);
        if (points != null) {
            Seq<Waypoint> waypoints = new Seq<>();
            for (int[] point : points) {
                waypoints.add(new PositionWaypoint(point[0], point[1]));
            }
            follow(new WaypointPath(waypoints));
        }
    }
}
