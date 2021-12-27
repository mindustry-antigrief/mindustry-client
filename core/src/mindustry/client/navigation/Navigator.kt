package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.navigation.waypoints.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*

/** An abstract class for a navigation algorithm, i.e. A*.  */
abstract class Navigator {
    @JvmField
    val map = HashMap<Int, Vec2>()
    private var lastWp = 0L
    private val realObstacles = mutableListOf<Circle>() // Avoids creating new lists every time navigate is called

    init {
        Events.on(EventType.WorldLoadEvent::class.java) {
            map.clear()
        }
    }
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
    ): Array<PositionWaypoint>

    fun navigate(start: Vec2, end: Vec2, obstacles: Iterable<TurretPathfindingEntity>): Array<PositionWaypoint> {
        start.clamp(0f, 0f, world.unitHeight().toFloat(), world.unitWidth().toFloat())
        end.clamp(0f, 0f, world.unitHeight().toFloat(), world.unitWidth().toFloat())
        realObstacles.clear()
        val additionalRadius =
            if (player.unit().formation == null) player.unit().hitSize / 2
            else player.unit().formation().pattern.radius() + player.unit().formation.pattern.spacing / 2

        if(state.map.name() != "The Maze") {
            synchronized (obstacles) {
                for (turret in obstacles) {
                    if (turret.canHitPlayer && turret.canShoot) realObstacles.add(
                        Circle(
                            turret.x,
                            turret.y,
                            turret.radius + additionalRadius
                        )
                    )
                }
            }
        }

        if (state.hasSpawns()) { // FINISHME: These should really be weighed less than turrets...
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

        if (map.size > 0 && Time.timeSinceMillis(lastWp) > 1000) {
            val closestCore = map.minByOrNull { it.value.dst(end) }!!
            if (player.dst(end) > closestCore.value.dst(end)) {
                lastWp = Time.millis() // Try again in a second
                Call.sendChatMessage("/wp ${closestCore.key}")
            } else lastWp = Time.millis() - 900 // Try again in .1s
        }

        val flood = flood() && player.unit().type != UnitTypes.horizon
        return findPath(
            start, end, realObstacles.toTypedArray(), world.unitWidth().toFloat(), world.unitHeight().toFloat()
        ) { x, y ->
            flood && world.tiles.getc(x, y).team() == Team.blue || player.unit().type != null && !player.unit().type.canBoost && player.unit().solidity()?.solid(x, y) ?: false
        }
    }
}