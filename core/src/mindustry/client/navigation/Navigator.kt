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
import mindustry.entities.*
import mindustry.game.*
import mindustry.gen.*
import mindustry.gen.Unit
import mindustry.world.blocks.defense.*
import mindustry.world.blocks.storage.*

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

        val unit: Unit? = player.unit()
        val type = unit?.type ?: UnitTypes.gamma // FINISHME: Instead of defaulting to gamma, maybe pick the largest core unit on the map?

        val additionalRadius = (unit?.hitSize() ?: 16F) / 2 + tilesize // Default to about the size of a mega
        val pool = Pools.get(Circle::class.java, ::Circle)

        // Turrets and units FINISHME: Turrets should probably not use this system
        if (type.targetable(unit, player.team()) && type.hittable(unit)) {
            for (turret in obstacles) {
                if (turret.isObstacle()) {
                    realObstacles.add(
                        pool.obtain().set(
                            turret.x(),
                            turret.y(),
                            turret.range() + additionalRadius
                        )
                    )
                }
            }
        }

        // Spawns
        if (state.hasSpawns()) {
            spawner.spawns.each { spawn -> // Use .each() as this is on the client thread and the iterator isn't thread safe. Each works fine as this Seq should never be edited at runtime. This might actually be a bad assumption to make? Idk
                realObstacles.add(
                    pool.obtain().set(
                        spawn.worldx(),
                        spawn.worldy(),
                        state.rules.dropZoneRadius + additionalRadius
                    )
                )
            }
        }

        // Shield projectors
        state.teams.active.each { team ->
            if (player.team() !== null && state.teams.getOrNull(player.team()) === team) return@each
            for (block in BaseShield.baseShields) {
                team.getBuildings(block).each { shield ->
                    realObstacles.add(
                        pool.obtain().set(
                            shield.x,
                            shield.y,
                            (shield as BaseShield.BaseShieldBuild).radius() + additionalRadius - tilesize / 2,
                        )
                    )
                }
            }
        }

        //Consider respawning at a core
        if (Time.timeSinceMillis(lastTp) > 3000 && player.team().cores().any() && start.dst2(end) > 400) { //min 1.6 tiles
            if (
                unit?.spawnedByCore == true &&
                unit.stack.amount == 0 &&
                (unit as? Payloadc)?.hasPayload()?.not() ?: true // no payloads
            ) {
                var best: Position = player.team().cores().min(Structs.comps(Structs.comparingInt { -it.block.size }, Structs.comparingFloat { it.dst2(end) }))
                if (unit.type.coreUnitDock && ClientVars.ratelimitRemaining > 2) { // Try to use a unit if it's closer FINISHME: If the player is a different unit, they can still teleport to a closer one of the same type.
                    val u = Units.closest(player.team(), end.x, end.y, unit.type.speed * 60F * 5) { u -> u.playerControllable() && !u.isPlayer } // Anything within a few seconds of the target
                    if (u != null && u.dst2(end) < best.dst2(end)) best = u
                }
                if (ClientVars.ratelimitRemaining > 1 && player.dst2(best) > buildingRange * buildingRange && player.dst2(end) > best.dst2(end) && player.dst2(best) > unit.speed() * unit.speed() * 24 * 24) { // don't try to move if we're already close to that core
                    lastTp = Time.millis() // Try again in 3s
                    when (best) {
                        is CoreBlock.CoreBuild -> Call.buildingControlSelect(player, best)
                        is Unit -> { // Control the closest unit then immediately respawn
                            Call.unitControl(player, best)
                            Call.unitClear(player)
                            control.input.recentRespawnTimer = 1f // Need to set these so that the game doesn't attempt to control another unit as if we died
                            control.input.controlledType = null
                        }
                    }
                }
            }
            if (Time.timeSinceMillis(lastTp) > 3000) lastTp = Time.millis() - 2900 // Didn't tp, try again in .1s
        }

        val avoidFlood = CustomMode.flood() && type != UnitTypes.horizon && player.team() !== Team.blue
        val canBoost = type.canBoost
        val solidity = unit?.solidity()
        val ret = findPath(
            start, end, realObstacles, world.unitWidth().toFloat(), world.unitHeight().toFloat()
        ) { x, y ->
//            Log.info("pos: $x $y | clamp: ${world.tiles.getn(x, y).x} ${world.tiles.getn(x, y).y}")
            avoidFlood && world.tiles.getn(x, y).team() === Team.blue || // Avoid blue team in flood
            !canBoost && solidity?.solid(x, y) ?: false && // Units that cannot hover will check for solid blocks
                world.tiles.getn(x, y).run { build === null || build.team !== player.team() || !block().teamPassable } // Ignore teamPassable blocks such as erekir blastDoors
        }
        Pools.freeAll(realObstacles, true)
        realObstacles.clear()
        return ret
    }

    protected fun interface Int2P {
        operator fun invoke(x: Int, y: Int) : Boolean
    }
}
