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

    public Vec2[] navigate(Vec2 start, Vec2 end, TurretPathfindingEntity[] obstacles) {
        start.clamp(0, 0, world.unitHeight(), world.unitWidth());
        end.clamp(0, 0, world.unitHeight(), world.unitWidth());
        Seq<Circle> realObstacles = new Seq<>(new Circle[0]);
        float additionalRadius =  player.unit().formation == null ? player.unit().hitSize/2 : player.unit().formation().pattern.radius() + player.unit().formation.pattern.spacing/2;
        for (TurretPathfindingEntity turret : obstacles) {
            if (turret.canHitPlayer && turret.canShoot) realObstacles.add(new Circle(turret.x, turret.y, (turret.radius + additionalRadius)));
        }

        return findPath(start, end, realObstacles.toArray(), ((float) world.unitWidth()), ((float) world.unitHeight()));
    }
}
