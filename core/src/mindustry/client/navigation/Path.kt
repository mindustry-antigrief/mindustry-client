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
import kotlin.math.*

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
                        // what is this following code supposed to achieve?
//                        if (targetPos.within(destX, destY, 1F) && Navigation.currentlyFollowing != null && waypoints.waypoints.any()) { // Same destination
//                            val point = waypoints.waypoints.first()
//                            if (v1.dst(point) < point.tolerance * 1.5) {
//                                v1.set(point)
//                            }
//                        }
                        val path = Navigation.navigator.navigate(v1, v2.set(destX, destY), Navigation.getEnts())
                        Pools.freeAll(filter, true)
                        filter.clear()
                        if (!Vars.player.dead()) v1.set(Vars.player.unit())
                        if (path.isNotEmpty() && (targetPos.within(destX, destY, 1F) || (Navigation.currentlyFollowing != null && Navigation.currentlyFollowing !is WaypointPath<*>))) { // Same destination
                            val relaxed = Navigation.navigator is AStarNavigatorOptimised
                            filter.addAll(path, 0, path.size) // Avoid addAll(*path) as that requires a spread operator which does an arrayCopy
                            if (dist > 0F) {
                                if (!relaxed) filter.removeAll { (it.dst2(destX, destY) < dist * dist).apply { if (this) Pools.free(it) } }
                                else while (filter.size > 1 && filter[filter.size - 2].dst2(destX, destY) < dist * dist) Pools.free(filter.pop())
                            }
                            if (!relaxed) {
                                if (filter.size > 1) {
                                    val m = filter.min(v1::dst2)
                                    if (v1.within(m, m.tolerance)) {
                                        val i = filter.indexOf(m)
                                        if (i > 0) { for (j in 0 until i) Pools.free(filter[j]); filter.removeRange(0, i - 1) }
                                    }
                                }
                                if (filter.size > 1 || (filter.any() && filter.first().dst(Vars.player) < Vars.tilesize / 2f)) Pools.free(filter.remove(0))
                                if (filter.size > 1 && Vars.player.unit().isFlying) Pools.free(filter.remove(0)) // Ground units can't properly turn corners if we remove 2 waypoints.
                            } else if (filter.size > 1) {
                                var prev: Position = filter.first()
                                var removeTo = -1
                                for (i in 1 until filter.size) {
                                    val curr = filter[i]
                                    if ((v1.x - prev.x) * (curr.x - v1.x) + (v1.y - prev.y) * (curr.y - v1.y) > 0) { // find the point where the path crosses through the player
                                        // this uses the dot product of the vectors AP and PB (where P is player, A and B are points). If product > 0, P is "between" A and B
                                        removeTo = i - 1
                                        break
                                    }
                                    prev = curr
                                }
                                if (removeTo >= 0) { for (j in 0 .. removeTo) Pools.free(filter[j]); filter.removeRange(0, removeTo) }
                            }
                            if (filter.any()) { // Modify the last waypoint to have greater accuracy when stopping
                                val last = filter.peek()
                                if (abs(destX - last.x) < Vars.tilesizeF / 2f && abs(destY - last.y) < Vars.tilesizeF / 2f)
                                    last.set(destX, destY) // Snap the last waypoint to cursor location
                                last.tolerance = 4f
                                last.stopOnFinish = true
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
                waypoints.set(waypoint.set(destX, destY, 1F, dist).run())
                waypoint.stopOnFinish = aStarDist == 0f
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
