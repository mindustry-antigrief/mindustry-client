package com.github.blahblahbloopster.navigation

import arc.math.Mathf
import arc.math.geom.Circle
import arc.math.geom.Vec2
import arc.util.Log
import mindustry.Vars.tilesize
import mindustry.Vars.world
import mindustry.client.navigation.Navigator
import mindustry.core.World
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {
    private const val DIAGONAL_COST = 14
    private const val V_H_COST = 10

    //Blocked cells are just null Cell values in grid
    private var grid: Array<Array<Cell?>> = Array(5) { arrayOfNulls(5) }
    private var open = PriorityQueue<Cell>(500)
    private var closed: Array<BooleanArray> = emptyArray()
    private var costly: Array<BooleanArray> = emptyArray()
    private var startX = 0
    private var startY = 0
    private var endX = 0
    private var endY = 0
    private var block = false
    private var width = 0f
    private var height = 0f
    private var tileWidth = 0
    private var tileHeight = 0

    private fun Int.clamp(min: Int, max: Int) = coerceIn(min, max)

    private fun setBlocked(x: Int, y: Int) {
        grid[x][y] = null
    }

    override fun init() {}

    private fun setStartCell(x: Int, y: Int) {
        startX = x
        startY = y
    }

    private fun setEndCell(x: Int, y: Int) {
        endX = x
        endY = y
    }

    private fun checkAndUpdateCost(current: Cell?, t: Cell?, cost: Int) {
        if (t == null || closed[t.x][t.y]) return
        val tFinalCost = t.heuristicCost + cost
        //        if(closed[t.i][t.j]){
//            t_final_cost *= 100;
//        }
        val inOpen = open.contains(t)  // O(N)
        if (!inOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost
            t.parent = current
            if (!inOpen) open.add(t) // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        endX = endX.clamp(0, grid.size - 1)
        endY = endY.clamp(0, grid[0].size - 1)
        startX = startX.clamp(0, grid.size - 1)
        startY = startY.clamp(0, grid[0].size - 1)
        open.add(grid[startX][startY])
        var current: Cell?
        while (true) {
            current = open.poll()  // O(log(n))
            if (current == null) break
            closed[current.x][current.y] = true
            if (current == grid[endX][endY]) {
                return
            }
            var t: Cell?
            val multiplier: Int = if (costly[current.x][current.y]) {
                5
            } else {
                1
            }
            if (current.x - 1 >= 0) {
                t = grid[current.x - 1][current.y]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
                if (current.y - 1 >= 0) {
                    t = grid[current.x - 1][current.y - 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
                if (current.y + 1 < grid[0].size) {
                    t = grid[current.x - 1][current.y + 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
            }
            if (current.y - 1 >= 0) {
                t = grid[current.x][current.y - 1]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
            }
            if (current.y + 1 < grid[0].size) {
                t = grid[current.x][current.y + 1]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
            }
            if (current.x + 1 < grid.size) {
                t = grid[current.x + 1][current.y]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
                if (current.y - 1 >= 0) {
                    t = grid[current.x + 1][current.y - 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
                if (current.y + 1 < grid[0].size) {
                    t = grid[current.x + 1][current.y + 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
            }
        }
    }


    override fun findPath(start: Vec2?, end: Vec2?, obstacles: Array<Circle>?): Array<Vec2>? {
        start ?: return null
        end ?: return null
        obstacles ?: return null
        width = max(obstacles.maxOfOrNull { it.x + it.radius + tilesize * 2 } ?: 0f, max(start.x, end.x)).coerceAtMost(world.unitWidth().toFloat())
        height = max(obstacles.maxOfOrNull { it.y + it.radius + tilesize * 2 } ?: 0f, max(start.y, end.y)).coerceAtMost(world.unitHeight().toFloat())

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
            grid = Array(tileWidth) { arrayOfNulls(tileHeight) }
        }

        if (closed.size == tileWidth && closed.getOrNull(0)?.size == tileHeight) {
            closed.forEach { it.fill(false) }
        } else {
            closed = Array(tileWidth) { BooleanArray(tileHeight) }
        }

        if (costly.size == tileWidth && costly.getOrNull(0)?.size == tileHeight) {
            costly.forEach { it.fill(false) }
        } else {
            costly = Array(tileWidth) { BooleanArray(tileHeight) }
        }

        open.clear()

        //Set start position
        setStartCell(px, py)
        if (costly[px][py]) {
            costly[px][py] = false
        }

        //Set End Location
        setEndCell(ex, ey)
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                grid[x][y] ?: run { grid[x][y] = Cell(x, y) }
                grid[x][y]?.finalCost = 0
                grid[x][y]?.parent = null
                grid[x][y]?.heuristicCost = max(abs(x - endX), abs(y - endY))
            }
        }
        grid[px][py]?.finalCost = 0

        for (turret in obstacles) {
            for (dx in ((turret.x - turret.radius)/tilesize).toInt()..((turret.x + turret.radius)/tilesize).toInt()) {
                for (dy in ((turret.y - turret.radius)/tilesize).toInt()..((turret.y + turret.radius)/tilesize).toInt()) {
                    if (dx >= tileWidth || dx < 0 || dy >= tileHeight || dy < 0) continue
                    if (turret.contains(dx * tilesize.toFloat(), dy * tilesize.toFloat())) {
                        if (block) {
                            setBlocked(dx, dy)
                        } else {
                            costly[dx][dy] = true
                        }
                    }
                }
            }
        }

        grid[px][py] = Cell(px, py)

        aStarSearch()

        return if (closed[endX][endY]) {
            val points = mutableListOf<Vec2>()
            //Trace back the path
            var current = grid[endX][endY]
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