package mindustry.client.navigation

import arc.math.geom.Circle
import arc.math.geom.Point2
import arc.math.geom.Vec2
import arc.struct.Seq
import mindustry.client.navigation.TurretPathfindingEntity
import mindustry.Vars
import mindustry.ai.Pathfinder
import mindustry.client.utils.get
import mindustry.gen.Legsc
import mindustry.gen.PathTile
import mindustry.gen.WaterMovec

/** An abstract class for a navigation algorithm, i.e. A*.  */
abstract class Navigator {
    /** Called once upon client loading.  */
    abstract fun init()

    /**
     * Finds a path between the start and end points provided an array of circular obstacles.
     * May return null if no path is found.
     */
    protected abstract fun findPath(
        start: Vec2,
        end: Vec2,
        obstacles: Array<Circle>,
        width: Float,
        height: Float,
        blocked: (Int, Int) -> Boolean
    ): Array<Vec2>

    fun navigate(start: Vec2, end: Vec2, obstacles: Array<TurretPathfindingEntity>): Array<Vec2> {
        start.clamp(0f, 0f, Vars.world.unitHeight().toFloat(), Vars.world.unitWidth().toFloat())
        end.clamp(0f, 0f, Vars.world.unitHeight().toFloat(), Vars.world.unitWidth().toFloat())
        val realObstacles = mutableListOf<Circle>()
        val additionalRadius =
            if (Vars.player.unit().formation == null) Vars.player.unit().hitSize / 2 else Vars.player.unit()
                .formation().pattern.radius() + Vars.player.unit().formation.pattern.spacing / 2
        for (turret in obstacles) {
            if (turret.canHitPlayer && turret.canShoot) realObstacles.add(
                Circle(
                    turret.x,
                    turret.y,
                    turret.radius + additionalRadius
                )
            )
        }
        if (Vars.state.hasSpawns()) { // TODO: These should really be weighed less than turrets...
            for (spawn in Vars.spawner.spawns) {
                realObstacles.add(
                    Circle(
                        spawn.worldx(),
                        spawn.worldy(),
                        Vars.state.rules.dropZoneRadius + additionalRadius
                    )
                )
            }
        }
        return findPath(
            start, end, realObstacles.toTypedArray(), Vars.world.unitWidth().toFloat(), Vars.world.unitHeight().toFloat()
        ) { x, y -> Vars.player.unit().solidity()?.solid(x, y) ?: false }
    }
}