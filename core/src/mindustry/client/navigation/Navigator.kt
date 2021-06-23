package mindustry.client.navigation

import arc.math.geom.*
import mindustry.Vars.*
import mindustry.game.*

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
        start.clamp(0f, 0f, world.unitHeight().toFloat(), world.unitWidth().toFloat())
        end.clamp(0f, 0f, world.unitHeight().toFloat(), world.unitWidth().toFloat())
        val realObstacles = mutableListOf<Circle>()
        val additionalRadius =
            if (player.unit().formation == null) player.unit().hitSize / 2
            else player.unit().formation().pattern.radius() + player.unit().formation.pattern.spacing / 2
        for (turret in obstacles) {
            if (turret.canHitPlayer && turret.canShoot) realObstacles.add(
                Circle(
                    turret.x,
                    turret.y,
                    turret.radius + additionalRadius
                )
            )
        }
        if (state.hasSpawns()) { // TODO: These should really be weighed less than turrets...
            for (spawn in spawner.spawns) {
                realObstacles.add(
                    Circle(
                        spawn.worldx(),
                        spawn.worldy(),
                        state.rules.dropZoneRadius + additionalRadius
                    )
                )
            }
        }
        val flood = ui.join.lastHost != null && (ui.join.lastHost.modeName ?: false) == "Flood"
        return findPath(
            start, end, realObstacles.toTypedArray(), world.unitWidth().toFloat(), world.unitHeight().toFloat()
        ) { x, y ->
            flood && world.tiles.getc(x, y).team() == Team.blue || player.unit().type != null && !player.unit().type.canBoost && player.unit().solidity()?.solid(x, y) ?: false
        }
    }
}