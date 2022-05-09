package mindustry.client.navigation

import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import mindustry.Vars.*
import mindustry.client.navigation.waypoints.*
import mindustry.core.*
import kotlin.math.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {
    private val pool = Pools.get(PositionWaypoint::class.java) { PositionWaypoint() }
    private var grid: Array<Cell> = emptyArray()
    private var gridSize = Point2()
    private var open = PQueue<Cell>()
    private var startX = 0
    private var startY = 0
    private var endX = 0
    private var endY = 0
    private var tileWidth = 0
    private var tileHeight = 0
    private val points = mutableListOf<PositionWaypoint>()

    private inline fun d8(cons: (x: Int, y: Int) -> Unit) {
        cons(1, 0)
        cons(1, 1)
        cons(0, 1)
        cons(-1, 1)
        cons(-1, 0)
        cons(-1, -1)
        cons(0, -1)
        cons(1, -1)
    }

    private fun cell(x: Int, y: Int) = grid[x + (y * tileWidth)]

    override fun init() {}

    /** Calculates the distance heuristic for this cell */
    private fun h(cell: Cell): Double {
        val dx = abs(cell.x - endX)
        val dy = abs(cell.y - endY)
        return dx + dy - 1.414 * min(dx, dy)
    }

    private fun checkAndUpdateCost(current: Cell, t: Cell, cost: Double) {
        if (!t.closed || cost < t.g) {
            t.closed = true
            t.g = cost
            t.f = t.g + h(t)
            t.cameFrom = current
            open.add(t) // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        open.add(cell(startX, startY))
        cell(startX, startY).closed = true

        var current: Cell
        while (!open.empty()) {
            current = open.poll() // Get a tile to explore
            if (current == cell(endX, endY)) { // Made it to the finish
                return
            }

            // Check surrounding tiles
            d8 { x1, y1 ->
                val x = current.x + x1
                val y = current.y + y1

                if (!Structs.inBounds(x, y, tileWidth, tileHeight)) {
                    return@d8
                }

                // Add to the open list with calculated cost
                checkAndUpdateCost(
                    current,
                    cell(x, y),
                    current.g * (if (abs(x1) + abs(y1) == 1) 1.0 else 1.0000001) + cell(x, y).added * if (abs(x1) + abs(y1) == 1) 1.0 else 1.414 // Tiebreaker is needed to draw correct path
                )
            }
        }
    }


    override fun findPath(
        start: Vec2,
        end: Vec2,
        obstacles: Seq<Circle>,
        width: Float,
        height: Float,
        blocked: Int2P
    ): Array<PositionWaypoint> {
        val t0 = Time.nanos()
        tileWidth = ceil(width / tilesize).toInt() + 1
        tileHeight = ceil(height / tilesize).toInt() + 1

        start.clamp(0f, 0f, height, width)
        end.clamp(0f, 0f, height, width)

        //Reset
        startX = World.toTile(start.x).coerceIn(0, tileWidth - 1)
        startY = World.toTile(start.y).coerceIn(0, tileHeight - 1)
        endX = World.toTile(end.x).coerceIn(0, tileWidth - 1)
        endY = World.toTile(end.y).coerceIn(0, tileHeight - 1)

        if (!gridSize.equals(tileWidth, tileHeight)) {
            grid = Array(tileWidth * tileHeight) { Cell(it % tileWidth, it / tileWidth) }
            gridSize.set(tileWidth, tileHeight)
        }

        open.clear()

        // Reset all cells
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                cell(x, y).g = 0.0
                cell(x, y).cameFrom = null
                cell(x, y).closed = blocked(x, y)
                cell(x, y).added = 1
            }
        }

        for (turret in obstacles) {
            Geometry.circle(World.toTile(turret.x), World.toTile(turret.y), World.toTile(turret.radius)) { x, y ->
                if (Structs.inBounds(x, y, tileWidth, tileHeight)) cell(x, y).added += 100
            }
        }

        val t1 = Time.nanos()
        aStarSearch()
        val t2 = Time.nanos()
        if (benchmark) Log.debug("AStarNavigator took @ us (@ init, @ A*)", (t2 - t0) / 1000f, (t1 - t0) / 1000f, (t2 - t1)/1000f)

        points.clear()
        if (cell(endX, endY).closed) {
            //Trace back the path
            var current: Cell? = cell(endX, endY)
            while (current?.cameFrom != null) {
                points.add(pool.obtain().set(World.unconv(current.cameFrom!!.x.toFloat()), World.unconv(current.cameFrom!!.y.toFloat())))
                current = current.cameFrom
            }
            points.reverse()
        }
        return points.toTypedArray()
    }

    class Cell(var x: Int, var y: Int) : Comparable<Cell> {
        var g = 0.0 // cost so far
        var f = Double.POSITIVE_INFINITY // g + h, estimate of total cost
        var cameFrom: Cell? = null
        var closed = false
        var added = 1

        override fun toString(): String {
            return "[$x, $y]"
        }

        override fun compareTo(other: Cell): Int { // lower f is better
            return f.compareTo(other.f)
        }
    }
}
