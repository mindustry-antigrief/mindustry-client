package mindustry.client.navigation

import arc.*
import arc.func.*
import arc.math.geom.*
import arc.struct.*
import arc.util.pooling.*
import mindustry.*
import mindustry.client.navigation.waypoints.*
import mindustry.game.*
import java.util.concurrent.*

/** A way of representing a path */
abstract class Path {
    companion object {
        @JvmField val waypoint = PositionWaypoint() // Use this for paths that require one point, dont allocate more than we need to
        @JvmField val v1 = Vec2() // Temporary vectors
        @JvmField val v2 = Vec2()
        @JvmField val waypoints = WaypointPath<PositionWaypoint>() // FINISHME: Use this in all paths
        private val filter = Seq<PositionWaypoint>()
        private var job: CompletableFuture<Array<PositionWaypoint>> = CompletableFuture.completedFuture(emptyArray())
        private val targetPos = Vec2(-1F, -1F)
        val listeners = Seq<Runnable>()
        @JvmField var repeat = false

        init {
            Events.on(EventType.WorldLoadEvent::class.java) { job = CompletableFuture.completedFuture(emptyArray()) }
        }

        @JvmOverloads @JvmStatic
        fun goTo(dest: Position?, dist: Float = 0F, aStarDist: Float = 0F): WaypointPath<PositionWaypoint> {
            if (dest != null) goTo(dest.x, dest.y, dist, aStarDist)
            return waypoints
        }

        @JvmOverloads @JvmStatic @Synchronized
        fun goTo(destX: Float, destY: Float, dist: Float = 0F, aStarDist: Float = 0F, cons: Cons<WaypointPath<PositionWaypoint>>? = null): WaypointPath<PositionWaypoint> {
            if (Core.settings.getBool("pathnav") && !Core.settings.getBool("assumeunstrict") && (aStarDist == 0F || Vars.player.dst(destX, destY) > aStarDist)) {
                if (!targetPos.within(destX, destY, 1f)) job.cancel(true)
                targetPos.set(destX, destY)
                if (job.isDone) {
                    job = clientThread.submit {
                        val path = Navigation.navigator.navigate(v1.set(Vars.player), v2.set(destX, destY), Navigation.obstacles)
                        Pools.freeAll(filter)
                        filter.clear()
                        if (targetPos.within(destX, destY, 1F) || (Navigation.currentlyFollowing != null && Navigation.currentlyFollowing !is WaypointPath<*>)) { // Same destination
                            filter.addAll(*path).removeAll { (it.dst(destX, destY) < dist).apply { if (this) Pools.free(it) } }

                            while (filter.size > 1 && filter.min(Vars.player::dst) != filter.first()) Pools.free(filter.remove(0))
                            if (filter.size > 1 || (filter.any() && filter.first().dst(Vars.player) < Vars.tilesize)) Pools.free(filter.remove(0))
                            if (filter.size > 1 && Vars.player.unit().isFlying) Pools.free(filter.remove(0)) // Ground units can't properly turn corners if we remove 2 waypoints.
                            waypoints.set(filter)
                        } else { // Different destination, this is needed to prevent issues when starting a path at the end of the last one
                            waypoints.clear().add(waypoint.set(-1F, -1F))
                        }
                        cons?.get(waypoints)
                        path
                    }
                }
            } else { // Not navigating
                waypoints.set(waypoint.set(destX, destY, 16F, dist).run())
                cons?.get(waypoints)
            }

            waypoints.follow()
            return waypoints
        }
    }

    open fun init() {
        waypoints.setShow(true)
    }

    abstract fun setShow(show: Boolean)

    abstract fun getShow(): Boolean

    fun addListener(listener: Runnable) = listeners.add(listener)

     abstract fun follow()

     abstract fun progress(): Float

     open fun isDone(): Boolean {
        val done = progress() >= 0.999f
        if (done && repeat) onFinish()
        return done && !repeat
    }

    fun onFinish() {
        listeners.forEach(Runnable::run)
        if (repeat) reset()
    }

    abstract fun reset()

    @Synchronized
    open fun draw() = Unit

    abstract fun next(): Position?
}
