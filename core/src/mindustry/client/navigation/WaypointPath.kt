package mindustry.client.navigation

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.math.geom.*
import arc.struct.*
import mindustry.client.navigation.waypoints.*
import mindustry.graphics.*

/** A [Path] composed of [Waypoint] instances.  */
class WaypointPath<T : Waypoint> : Path {
    var waypoints: Seq<T> = Seq()
    private var initial: Seq<T>
    private var initialSize: Int
    private var show = false

    constructor(waypoints: Seq<T>) {
        this.waypoints = waypoints
        initial = waypoints.copy()
        initialSize = waypoints.size
    }

    constructor(vararg waypoints: T) {
        this.waypoints.set(waypoints)
        initial = Seq.with(*waypoints)
        initialSize = waypoints.size
    }

    @Synchronized
    fun set(waypoints: Seq<T>): WaypointPath<T> {
        this.waypoints.set(waypoints)
        if (repeat) initial.set(waypoints) // Don't bother if we aren't repeating
        initialSize = waypoints.size
        return this
    }

    @Synchronized
    fun set(vararg waypoints: T): WaypointPath<T> {
        this.waypoints.set(waypoints)
        if (repeat) initial.set(waypoints) // Don't bother if we aren't repeating
        initialSize = waypoints.size
        return this
    }

    @Synchronized
    fun add(waypoint: T): WaypointPath<T> {
        waypoints.add(waypoint)
        initial.add(waypoint)
        initialSize++
        return this
    }

    @Synchronized
    fun clear(): WaypointPath<T> {
        waypoints.clear()
        initial.clear()
        initialSize = 0
        return this
    }

    override fun setShow(show: Boolean) {
        this.show = show
    }

    override fun getShow(): Boolean {
        return show
    }

    @Synchronized
    override fun follow() {
        if (waypoints.isEmpty) return
        while (waypoints.size > 1 && Core.settings.getBool("assumeunstrict")) waypoints.remove(0) // Only the last waypoint is needed when we are just teleporting there anyways.
        while (waypoints.any() && waypoints.first().isDone) {
            waypoints.first().onFinish()
            waypoints.remove(0)
        }
        if (waypoints.any()) waypoints.first().run()
    }

    override fun progress(): Float {
        return if (initialSize == 0) 1f else waypoints.size / initialSize.toFloat()
    }

    @Synchronized
    override fun isDone(): Boolean {
        if (waypoints.isEmpty && repeat) onFinish()
        return waypoints.isEmpty
    }

    override fun reset() {
        waypoints.clear()
        waypoints.addAll(initial)
    }

    @Synchronized
    override fun draw() {
        if (!show) return

        var lastWaypoint: Position? = null
        Draw.z(Layer.space)
        for (waypoint in waypoints) {
            if (waypoint !is Position) continue
                if (lastWaypoint != null) {
                    Draw.color(Color.blue, 0.4f)
                    Lines.stroke(3f)
                    Lines.line(lastWaypoint.x, lastWaypoint.y, waypoint.x, waypoint.y)
                }
                lastWaypoint = waypoint
            waypoint.draw()
            Draw.color()
        }
        Draw.color()
    }

    override operator fun next(): Position? {
        return waypoints.first() as Position?
    }
}