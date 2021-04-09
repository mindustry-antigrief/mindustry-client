package mindustry.client.navigation

import arc.math.geom.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.navigation.*
import mindustry.core.*
import java.util.*
import kotlin.math.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {

    //Blocked cells are just null Cell values in grid
    private var grid: Array<Array<Cell>> = emptyArray()
    private var open = PQueue<Cell>()
    private lateinit var closed: GridBits
    private var addedCosts: Array<IntArray> = emptyArray()
    private var startX = 0
    private var startY = 0
    private var endX = 0
    private var endY = 0
    private var tileWidth = 0
    private var tileHeight = 0

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

    private fun Int.clamp(min: Int, max: Int) = coerceIn(min, max)

    override fun init() {}

    /** Calculates the heuristic for this cell */
    private fun h(cell: Cell): Double {
        val dx = abs(cell.x - endX)
        val dy = abs(cell.y - endY)
        return dx + dy - 1.414 * min(dx, dy)
    }

    private fun checkAndUpdateCost(current: Cell, t: Cell, cost: Double) {

        if (!closed.get(t.x, t.y) || cost < t.g) {
            closed.set(t.x, t.y)
            t.g = cost
            t.f = t.g + h(t)
            t.cameFrom = current
            open.add(t) // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        startX = startX.clamp(0, grid.size - 1)
        startY = startY.clamp(0, grid[0].size - 1)
        open.add(grid[startX][startY])
        closed.set(startX, startY)

        endX = endX.clamp(0, grid.size - 1)
        endY = endY.clamp(0, grid[0].size - 1)

        var current: Cell?
        while (!open.empty()) {
            current = open.poll() // Get a tile to explore
            if (current == grid[endX][endY]) { // Made it to the finish
                return
            }

            // Check surrounding tiles
            d8 { x1, y1 ->
                val x = current.x + x1
                val y = current.y + y1

                if (x < 0 || y < 0 || x >= tileWidth || y >= tileHeight) {
                    return@d8
                }

                // Add to the open list with calculated cost
                checkAndUpdateCost(
                        current,
                        grid[x][y],
                        (current.g + addedCosts[x][y] + if (abs(x1) + abs(y1) == 1) 1.0 else 1.414) * if (abs(x1) + abs(y1) == 1) 1.0 else 1.001
                )
            }
        }
    }


    override fun findPath(start: Vec2?, end: Vec2?, obstacles: Array<Circle>?, width: Float, height: Float): Array<Vec2>? {
        start ?: return null
        end ?: return null
        obstacles ?: return null

        tileWidth = ceil(width / tilesize).toInt() + 1
        tileHeight = ceil(height / tilesize).toInt() + 1

        start.clamp(0f, 0f, height, width)
        end.clamp(0f, 0f, height, width)


        if (obstacles.isEmpty()) {
            return arrayOf(end)
        }

        //Reset
        startX = World.toTile(start.x)
        startY = World.toTile(start.y)
        endX = World.toTile(end.x)
        endY = World.toTile(end.y)

        if (grid.size != tileWidth || grid.getOrNull(0)?.size != tileHeight) {
            grid = Array(tileWidth) { x -> Array(tileHeight) { y -> Cell(x, y) } }
        }

        if (this::closed.isInitialized && closed.width() == tileWidth && closed.height() == tileHeight) {
            closed.clear()
        } else {
            closed = GridBits(tileWidth, tileHeight)
        }

        if (addedCosts.size == tileWidth && addedCosts.getOrNull(0)?.size == tileHeight) {
            addedCosts.forEach { it.fill(0) }
        } else {
            addedCosts = Array(tileWidth) { IntArray(tileHeight) }
        }

        open.clear()

        // Reset all cells
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                grid[x][y].g = 0.0
                grid[x][y].f = 0.0 // TODO: Is this needed?
                grid[x][y].cameFrom = null
            }
        }

        for (turret in obstacles) {
            val lowerXBound = ((turret.x - turret.radius) / tilesize).toInt()
            val upperXBound = ((turret.x + turret.radius) / tilesize).toInt()
            val lowerYBound = ((turret.y - turret.radius) / tilesize).toInt()
            val upperYBound = ((turret.y + turret.radius) / tilesize).toInt()
            for (x in lowerXBound..upperXBound) {
                for (y in lowerYBound..upperYBound) {
                    if (Structs.inBounds(x, y, tileWidth, tileHeight) && addedCosts[x][y] == 0 && turret.contains(x * tilesize.toFloat(), y * tilesize.toFloat())) {
                        addedCosts[x][y] = 1000
//                        closed.set(x, y) TODO: The line above will always succeed, this line will do nothing if there is no path, add a hotkey to toggle
                    }
                }
            }
        }

        aStarSearch()

        return if (closed.get(endX, endY)) {
            val points = mutableListOf<Vec2>()
            //Trace back the path
            var current: Cell? = grid[endX][endY]
            while (current?.cameFrom != null) {
                points.add(Vec2(World.unconv(current.cameFrom!!.x.toFloat()), World.unconv(current.cameFrom!!.y.toFloat())))
                current = current.cameFrom
            }
            //            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms");
            points.toTypedArray()
            //            System.out.println();
        } else {
//            System.out.println("Time taken = " + (System.currentTimeMillis() - startTime) + " ms, no path found");
            null
        }
    }

    class Cell(var x: Int, var y: Int) : Comparable<Cell> {
        var g = 0.0
        var f = Double.POSITIVE_INFINITY // g + h, estimate of best possible path
        var cameFrom: Cell? = null

        override fun toString(): String {
            return "[$x, $y]"
        }

        override fun compareTo(other: Cell): Int { // lower f is better
            return f.compareTo(other.f)
        }
    }
}
