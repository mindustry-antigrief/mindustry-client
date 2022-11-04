package mindustry.client.navigation

import arc.*
import arc.func.*
import arc.math.geom.*
import arc.struct.*
import arc.util.pooling.*
import mindustry.*
import mindustry.client.navigation.waypoints.*
import mindustry.game.*
import mindustry.world.blocks.storage.*
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
                        v1.set(Vars.player) // starting position
                        if (targetPos.within(destX, destY, 1F) && Navigation.currentlyFollowing != null && waypoints.waypoints.any()) { // Same destination
                            val point = waypoints.waypoints.first()
                            if (v1.dst(point) < point.tolerance * 1.5) {
                                v1.set(point)
                            }
                        }
                        val path = Navigation.navigator.navigate(v1, v2.set(destX, destY), Navigation.getEnts())
                        Pools.freeAll(filter)
                        filter.clear()
                        if (path.isNotEmpty() && (targetPos.within(destX, destY, 1F) || (Navigation.currentlyFollowing != null && Navigation.currentlyFollowing !is WaypointPath<*>))) { // Same destination
                            val relaxed = Navigation.navigator is AStarNavigatorOptimised
                            filter.addAll(*path)
                            if (!relaxed) filter.removeAll { (it.dst(destX, destY) < dist).apply { if (this) Pools.free(it) } }
                            else while(filter.size > 1 && filter[filter.size - 2].dst(destX, destY) < dist) Pools.free(filter.pop())
                            if (filter.size > 1) {
                                val m = filter.min(Vars.player::dst) // from O(n^2) to O(n) (pog) (cool stuff)
                                if (!relaxed || Vars.player.within(m, m.tolerance)) {
                                    val i = filter.indexOf(m)
                                    if (i > 0) { for (j in 0 until i) Pools.free(filter[j]); filter.removeRange(0, i - 1) }
                            }}
                            if (!relaxed) {
                                if (filter.size > 1 || (filter.any() && filter.first().dst(Vars.player) < Vars.tilesize / 2f)) Pools.free(filter.remove(0))
                                if (filter.size > 1 && Vars.player.unit().isFlying) Pools.free(filter.remove(0)) // Ground units can't properly turn corners if we remove 2 waypoints.
                            } else if (filter.size > 1) {
                                var prev: Position = v1 // startX, startY should be guaranteed to be behind the player?
                                var removeTo = -1
                                for (i in 0 until filter.size) {
                                    val curr = filter[i]
                                    if (prev.dst2(curr) >= Vars.player.dst2(prev)) { // find the point where the path crosses through the player
                                        removeTo = i - 1
                                        break
                                    }
                                    prev = curr
                                }
                                if (removeTo >= 0) { for (j in 0 .. removeTo) Pools.free(filter[j]); filter.removeRange(0, removeTo) }
                                // if (filter[0].dst(filter[1]) >= Vars.player.dst(filter[0])) Pools.free(filter.remove(0))
                                // by triangular inequality, we check if filter[i] and filter[i+1] are on opposing sides of the player
                            }
                            if (filter.any()) {
                                filter.peek().tolerance = 4f // greater accuracy when stopping
                                filter.peek().stopOnFinish = true
                                waypoints.set(filter)
                            } else {
                                waypoint.set(destX, destY, 8f, dist)
                                waypoint.stopOnFinish = true
                                waypoints.clear().add(waypoint)
                            }
                        } else if (path.isEmpty()){
                            waypoints.clear()
                        } else { // Different destination, this is needed to prevent issues when starting a path at the end of the last one
                            waypoints.clear().add(waypoint.set(-1F, -1F))
                        }
                        cons?.get(waypoints)
                        path
                    }
                }
            } else { // Not navigating
//                waypoint.set(destX, destY, if (aStarDist == 0f) 4f else aStarDist, dist)
                waypoints.set(waypoint.set(destX, destY, 1F, dist).run())
                waypoint.stopOnFinish = true
                waypoints.set(waypoint.run())
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

    // FINISHME: Unjank minepath core tp on mix tech maps
//    open fun allowCore(core: CoreBlock.CoreBuild) : Boolean {
//        return true
//    }
}
