package mindustry.client.navigation;

import arc.math.Mathf;
import arc.math.geom.Circle;
import arc.math.geom.Vec2;
import arc.struct.Seq;

import static mindustry.Vars.*;

/** An abstract class for a navigation algorithm, i.e. A*. */
public abstract class Navigator {

    /** Called once upon client loading. */
    abstract public void init();

    /**
     *  Finds a path between the start and end points provided an array of circular obstacles.
     *  May return null if no path is found.
     */
    abstract protected Vec2[] findPath(Vec2 start, Vec2 end, Circle[] obstacles);

    public Vec2[] navigate(Vec2 start, Vec2 end, TurretPathfindingEntity[] obstacles, int resolution) {
        start.scl(1f / resolution);
        end.clamp(0, 0, world.unitHeight(), world.unitWidth()).scl(1f / resolution);
        Seq<Circle> realObstacles = new Seq<>(new Circle[0]);
        for (TurretPathfindingEntity turret : obstacles) {
            if (turret.canHitPlayer && turret.canShoot) {
                realObstacles.add(new Circle(turret.x / resolution, turret.y / resolution, (turret.radius + (player.unit().formation() == null ? 0f : player.unit().formation().pattern.spacing / (float)Math.sin(180f / player.unit().formation.pattern.slots * Mathf.degRad)) + 8) / resolution));
            }
        }
        Vec2[] path = findPath(start, end, realObstacles.toArray());

        if (path == null) {
            return null;
        }

        for (Vec2 point : path) {
            point.scl(resolution);
        }

        return path;
    }
}
