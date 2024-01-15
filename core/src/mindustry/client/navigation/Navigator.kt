package mindustry.client.navigation

import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.navigation.waypoints.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.world.blocks.defense.*

/** An abstract class for a navigation algorithm, i.e. A*.  */
abstract class Navigator {
    @JvmField
    var lastTp = 0L
    private val realObstacles = Seq<Circle>() // Avoids creating new lists every time navigate is called

    /** Called once upon client loading.  */
    abstract fun init()

    /**
     * Finds a path between the start and end points provided an array of circular obstacles.
     * May return null if no path is found.
     */
    protected abstract fun findPath(
        start: Vec2,
        end: Vec2,
        obstacles: Seq<Circle>,
        width: Float,
        height: Float,
        blocked: Int2P
    ): Array<PositionWaypoint>

    fun navigate(start: Vec2, end: Vec2, obstacles: Iterable<TurretPathfindingEntity>): Array<PositionWaypoint> {
        start.clamp(0f, 0f, world.unitWidth().toFloat(), world.unitHeight().toFloat())
        end.clamp(0f, 0f, world.unitWidth().toFloat(), world.unitHeight().toFloat())
        val additionalRadius = player.unit().hitSize / 2 + tilesize

        // Turrets and units FINISHME: Turrets should probably not use this system
        if (player.unit().type.targetable(player.unit(), player.team()) && player.unit().type.hittable(player.unit())) {
            for (turret in obstacles) {
                if (turret.canHitPlayer() && turret.canShoot()) {
                    realObstacles.add(
                        Pools.obtain(Circle::class.java) { Circle() }.set(
                            turret.x(),
                            turret.y(),
                            turret.range + additionalRadius
                        )
                    )
                }
            }
        }

        // Spawns
        if (state.hasSpawns()) {
            for (spawn in spawner.spawns) {
                realObstacles.add(
                    Pools.obtain(Circle::class.java) { Circle() }.set(
                        spawn.worldx(),
                        spawn.worldy(),
                        state.rules.dropZoneRadius + additionalRadius
                    )
                )
            }
        }

        // Shield projectors
        for (team in state.teams.active) {
            if (team === player.team()) continue
            arrayOf(team.getBuildings(Blocks.shieldProjector), team.getBuildings(Blocks.largeShieldProjector)).forEach { shields ->
                val radius = ((shields.firstOpt()?.block ?: return@forEach) as BaseShield).radius + additionalRadius
                for (shield in shields) {
                    realObstacles.add(
                        Pools.obtain(Circle::class.java) { Circle() }.set(
                            shield.x,
                            shield.y,
                            radius
                        )
                    )
                }
            }
        }

        //Consider respawning at a core
        if (Time.timeSinceMillis(lastTp) > 3000 && player.team().cores().any()) {
            if (
                player.unit().spawnedByCore &&
                player.unit().stack.amount == 0 &&
                (if(player.unit() is Payloadc) !(player.unit() as Payloadc).hasPayload() else true)
            ) {
                val bestCore = player.team().cores().min(Structs.comps(Structs.comparingInt { -it.block.size }, Structs.comparingFloat { it.dst2(end) }))
                if (player.dst2(bestCore) > buildingRange * buildingRange && player.dst2(end) > bestCore.dst2(end) && player.dst2(bestCore) > player.unit().speed() * player.unit().speed() * 24 * 24) { // don't try to move if we're already close to that core
                    lastTp = Time.millis() // Try again in 3s
                    if (ClientVars.ratelimitRemaining > 1) Call.buildingControlSelect(player, bestCore)
                }
            }
            if (Time.timeSinceMillis(lastTp) > 3000) lastTp = Time.millis() - 2900 // Didn't tp, try again in .1s
        }

        val avoidFlood = CustomMode.flood() && player.unit().type != UnitTypes.horizon
        val canBoost = player.unit().type.canBoost
        val solidity = player.unit().solidity()
        val ret = findPath(
            start, end, realObstacles, world.unitWidth().toFloat(), world.unitHeight().toFloat()
        ) { x, y ->
//            Log.info("pos: $x $y | clamp: ${world.tiles.getn(x, y).x} ${world.tiles.getn(x, y).y}")
            avoidFlood && world.tiles.getn(x, y).team() === Team.blue || // Avoid blue team in flood
            !canBoost && solidity?.solid(x, y) ?: false && // Units that cannot hover will check for solid blocks
                world.tiles.getn(x, y).run { build === null || build.team !== player.team() || !block().teamPassable } // Ignore teamPassable blocks such as erekir blastDoors
        }
        Pools.freeAll(realObstacles)
        realObstacles.clear()
        return ret
    }

    protected fun interface Int2P {
        operator fun invoke(x: Int, y: Int) : Boolean
    }
}
