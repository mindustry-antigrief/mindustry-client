package mindustry.client.navigation;

import arc.math.geom.*;
import arc.struct.*;

import static mindustry.Vars.*;

/** An abstract class for a navigation algorithm, i.e. A*. */
public abstract class Navigator {

    /** Called once upon client loading. */
    abstract public void init();

    /**
     *  Finds a path between the start and end points provided an array of circular obstacles.
     *  May return null if no path is found.
     */
    abstract protected Vec2[] findPath(Vec2 start, Vec2 end, Circle[] obstacles, float width, float height);

    public Vec2[] navigate(Vec2 start, Vec2 end, TurretPathfindingEntity[] obstacles, int resolution) {
        start.scl(1f / resolution);
        end.clamp(0, 0, world.unitHeight(), world.unitWidth()).scl(1f / resolution);
        Seq<Circle> realObstacles = new Seq<>(new Circle[0]);
        for (TurretPathfindingEntity turret : obstacles) {
            if (turret.canHitPlayer && turret.canShoot) {
                realObstacles.add(new Circle(turret.x / resolution, turret.y / resolution, (turret.radius + (player.unit().formation == null ? 0f : player.unit().formation.pattern.radius()) + 8) / resolution));
            }
        }
        Vec2[] path = findPath(start, end, realObstacles.toArray(), ((float) world.unitWidth()) / resolution, ((float) world.unitHeight()) / resolution);

        if (path == null) {
            return null;
        }

        for (Vec2 point : path) {
            point.scl(resolution);
        }

        return path;
    }
}
