package com.github.blahblahbloopster.navigation

import arc.math.Mathf
import arc.math.geom.*
import mindustry.Vars.*
import mindustry.client.navigation.Navigator
import mindustry.core.World
import java.util.*
import kotlin.math.*
import kotlin.ranges.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified

object AStarNavigator : Navigator() {
    private const val DIAGONAL_COST = 14
    private const val V_H_COST = 10

    //Blocked cells are just null Cell values in grid
    private var grid: Array<Array<Cell?>> = Array(5) { arrayOfNulls(5) }
    private var open = PriorityQueue<Cell?>(500)
    private var closed: Array<BooleanArray> = emptyArray()
    private var costly: Array<BooleanArray> = emptyArray()
    private var startI = 0
    private var startJ = 0
    private var endI = 0
    private var endJ = 0
    private var block = false
    private var width = 0f
    private var height = 0f
    private var tileWidth = 0
    private var tileHeight = 0

    fun Int.clamp(min: Int, max: Int) = coerceIn(min, max)

    private fun setBlocked(i: Int, j: Int) {
        grid[i][j] = null
    }

    override fun init() {}

    private fun setStartCell(i: Int, j: Int) {
        startI = i
        startJ = j
    }

    private fun setEndCell(i: Int, j: Int) {
        endI = i
        endJ = j
    }

    private fun checkAndUpdateCost(current: Cell?, t: Cell?, cost: Int) {
        if (t == null || closed[t.i][t.j]) return
        val tFinalCost = t.heuristicCost + cost
        //        if(closed[t.i][t.j]){
//            t_final_cost *= 100;
//        }
        val inOpen = open.contains(t)  // O(N)
        if (!inOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost
            t.parent = current
            if (!inOpen) open.add(t)  // O(N)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        endI = endI.clamp(0, grid.size - 1)
        endJ = endJ.clamp(0, grid[0].size - 1)
        startI = startI.clamp(0, grid.size - 1)
        startJ = startJ.clamp(0, grid[0].size - 1)
        open.add(grid[startI][startJ])
        var current: Cell?
        while (true) {
            current = open.poll()  // O(log(n))
            if (current == null) break
            closed[current.i][current.j] = true
            if (current == grid[endI][endJ]) {
                return
            }
            var t: Cell?
            val multiplier: Int = if (costly[current.i][current.j]) {
                5
            } else {
                1
            }
            if (current.i - 1 >= 0) {
                t = grid[current.i - 1][current.j]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
                if (current.j - 1 >= 0) {
                    t = grid[current.i - 1][current.j - 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
                if (current.j + 1 < grid[0].size) {
                    t = grid[current.i - 1][current.j + 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
            }
            if (current.j - 1 >= 0) {
                t = grid[current.i][current.j - 1]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
            }
            if (current.j + 1 < grid[0].size) {
                t = grid[current.i][current.j + 1]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
            }
            if (current.i + 1 < grid.size) {
                t = grid[current.i + 1][current.j]
                checkAndUpdateCost(current, t, (current.finalCost + V_H_COST) * multiplier)
                if (current.j - 1 >= 0) {
                    t = grid[current.i + 1][current.j - 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
                if (current.j + 1 < grid[0].size) {
                    t = grid[current.i + 1][current.j + 1]
                    checkAndUpdateCost(current, t, (current.finalCost + DIAGONAL_COST) * multiplier)
                }
            }
        }
    }


    override fun findPath(start: Vec2?, end: Vec2?, obstacles: Array<Circle>?): Array<Vec2>? {
        start ?: return null
        end ?: return null
        obstacles ?: return null
        width = max(obstacles.maxOfOrNull { it.x + it.radius + tilesize * 2 } ?: 0f, max(start.x, end.x))
        height = max(obstacles.maxOfOrNull { it.y + it.radius + tilesize * 2 } ?: 0f, max(start.y, end.y))

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
        grid = Array(tileWidth) { arrayOfNulls(tileHeight) }
        closed = Array(tileWidth) { BooleanArray(tileHeight) }
        costly = Array(tileWidth) { BooleanArray(tileHeight) }
        open.clear()
        open = PriorityQueue { o1: Cell?, o2: Cell? ->
            o1 ?: return@PriorityQueue 0
            o2 ?: return@PriorityQueue 0
            o1.finalCost.compareTo(o2.finalCost)
        }
        //Set start position
        setStartCell(px, py)
        if (costly[px][py]) {
            costly[px][py] = false
        }

        //Set End Location
        setEndCell(ex, ey)
        for (i in 0 until tileWidth) {
            for (j in 0 until tileHeight) {
                grid[i][j] = Cell(i, j).apply { heuristicCost = max(abs(i - endI), abs(j - endJ)) }
                //                  System.out.print(grid[i][j].heuristicCost+" ");
            }
            //              System.out.println();
        }
        grid[px][py]?.finalCost = 0

        for (turret in obstacles) {
            val range = World.toTile(turret.radius)
            val x = World.toTile(turret.x)
            val y = World.toTile(turret.y)
            for (dx in -range..range) {
                for (dy in -range..range) {
                    if (dx + x < 0 || dx + x >= tileWidth || dy + y < 0 || dy + y >= tileHeight) continue
                    if (Mathf.within(dx.toFloat(), dy.toFloat(), range.toFloat())) {
                        if (block) {
                            setBlocked(dx + x, dy + y)
                        } else {
                            costly[dx + x][dy + y] = true
                        }
                    }
                }
            }
        }

        grid[px][py] = Cell(px, py)

        aStarSearch()

        return if (closed[endI][endJ]) {
            val points = mutableListOf<Vec2>()
            //Trace back the path
            var current = grid[endI][endJ]
            while (current?.parent != null) {
                points.add(Vec2(World.unconv(current.parent!!.i.toFloat()), World.unconv(current.parent!!.j.toFloat())))
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

    class Cell(var i: Int, var j: Int) {
        var heuristicCost = 0 //Heuristic cost
        var finalCost = 0 //G+H
        var parent: Cell? = null
        override fun toString(): String {
            return "[$i, $j]"
        }
    }
}