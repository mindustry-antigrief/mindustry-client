package com.github.blahblahbloopster.navigation

import arc.math.geom.*
import mindustry.Vars.tilesize
import mindustry.client.navigation.Navigator
import mindustry.core.World
import java.util.*
import kotlin.math.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {
    private const val DIAGONAL_COST = 14
    private const val V_H_COST = 10

    //Blocked cells are just null Cell values in grid
    private var grid: Array<Array<Cell>> = emptyArray()
    private var open = PriorityQueue<Cell>(500)
    private var closed: Array<BooleanArray> = emptyArray()
    private var addedCosts: Array<IntArray> = emptyArray()
    private var startX = 0
    private var startY = 0
    private var endX = 0
    private var endY = 0
    private var tileWidth = 0
    private var tileHeight = 0

    private inline fun d8(cons: (x: Int, y: Int) -> Unit) {
        cons(-1, -1)
        cons(-1, 0)
        cons(-1, 1)
        cons(0, -1)
        cons(0, 0)
        cons(0, 1)
        cons(1, -1)
        cons(1, 0)
        cons(1, 1)
    }

    private fun Int.clamp(min: Int, max: Int) = coerceIn(min, max)

    override fun init() {}

    private fun setStartCell(x: Int, y: Int) {
        startX = x
        startY = y
    }

    private fun setEndCell(x: Int, y: Int) {
        endX = x
        endY = y
    }

    private fun checkAndUpdateCost(current: Cell, t: Cell, cost: Int) {
        if (closed[t.x][t.y]) return
        val tFinalCost = t.heuristicCost + cost

        val inOpen = open.contains(t)  // O(N)
        if (!inOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost
            t.parent = current
            if (!inOpen) open.add(t) // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        startX = startX.clamp(0, grid.size - 1)
        startY = startY.clamp(0, grid[0].size - 1)
        open.add(grid[startX][startY])

        endX = endX.clamp(0, grid.size - 1)
        endY = endY.clamp(0, grid[0].size - 1)

        var current: Cell?
        while (true) {
            current = open.poll() ?: break  // Get a tile to explore
            closed[current.x][current.y] = true  // Don't go through it again
            if (current == grid[endX][endY]) {  // Made it to the finish
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
                    (current.finalCost + if (abs(x1) + abs(y1) == 1) V_H_COST else DIAGONAL_COST) + addedCosts[x][y]
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
        val px = World.toTile(start.x)
        val py = World.toTile(start.y)
        val ex = World.toTile(end.x)
        val ey = World.toTile(end.y)

        if (grid.size != tileWidth || grid.getOrNull(0)?.size != tileHeight) {
            grid = Array(tileWidth) { x -> Array(tileHeight) { y -> Cell(x, y) } }
        }

        if (closed.size == tileWidth && closed.getOrNull(0)?.size == tileHeight) {
            closed.forEach { it.fill(false) }
        } else {
            closed = Array(tileWidth) { BooleanArray(tileHeight) }
        }

        if (addedCosts.size == tileWidth && addedCosts.getOrNull(0)?.size == tileHeight) {
            addedCosts.forEach { it.fill(0) }
        } else {
            addedCosts = Array(tileWidth) { IntArray(tileHeight) }
        }

        open.clear()

        // Set start position
        setStartCell(px, py)

        // Set End Location
        setEndCell(ex, ey)
        // Reset all cells
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                grid[x][y].finalCost = 0
                grid[x][y].parent = null
                grid[x][y].heuristicCost = max(abs(x - endX), abs(y - endY))
            }
        }
        grid[px][py].finalCost = 0

        for (turret in obstacles) {
            val lowerXBound = ((turret.x - turret.radius) / tilesize).toInt()
            val upperXBound = ((turret.x + turret.radius) / tilesize).toInt()
            val lowerYBound = ((turret.y - turret.radius) / tilesize).toInt()
            val upperYBound = ((turret.y + turret.radius) / tilesize).toInt()
            for (x in lowerXBound..upperXBound) {
                for (y in lowerYBound..upperYBound) {

                    if (x >= tileWidth || x < 0 || y >= tileHeight || y < 0) continue

                    if (turret.contains(x * tilesize.toFloat(), y * tilesize.toFloat())) {
                        addedCosts[x][y] += ceil(((2 * turret.radius * tilesize) - (abs(x) + abs(y))) / 5f).toInt() + 5
                    }
                }
            }
        }

        grid[px][py] = Cell(px, py)

        aStarSearch()

        return if (closed[endX][endY]) {
            val points = mutableListOf<Vec2>()
            //Trace back the path
            var current: Cell? = grid[endX][endY]
            while (current?.parent != null) {
                points.add(Vec2(World.unconv(current.parent!!.x.toFloat()), World.unconv(current.parent!!.y.toFloat())))
                current = current.parent
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
        var heuristicCost = 0 //Heuristic cost
        var finalCost = 0 //G+H
        var parent: Cell? = null

        override fun toString(): String {
            return "[$x, $y]"
        }

        override fun compareTo(other: Cell): Int {
            return finalCost.compareTo(other.finalCost)
        }
    }
}
