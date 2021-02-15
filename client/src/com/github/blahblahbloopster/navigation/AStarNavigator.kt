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
    private var open = PriorityQueue<Cell?>()
    private var closed: Array<BooleanArray> = emptyArray()
    private var costly: Array<BooleanArray> = emptyArray()
    private var startI = 0
    private var startJ = 0
    private var endI = 0
    private var endJ = 0
    private var block = false
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
        val inOpen = open.contains(t)
        if (!inOpen || tFinalCost < t.finalCost) {
            t.finalCost = tFinalCost
            t.parent = current
            if (!inOpen) open.add(t)
        }
    }

    private fun aStarSearch() {
        //add the start location to open list.
        endI = Mathf.clamp(endI, 0, grid.size - 1)
        endJ = Mathf.clamp(endJ, 0, grid[0].size - 1)
        startI = Mathf.clamp(startI, 0, grid.size - 1)
        startJ = Mathf.clamp(startJ, 0, grid[0].size - 1)
        open.add(grid[startI][startJ])
        var current: Cell?
        while (true) {
            current = open.poll()
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

        val width = max(obstacles.maxOfOrNull { it.x } ?: 0f, max(start.x, end.x))
        val height = max(obstacles.maxOfOrNull { it.y } ?: 0f, max(start.y, end.y))

        val tileWidth = ceil(width / tilesize).toInt() + 1
        val tileHeight = ceil(height / tilesize).toInt() + 1

        start.clamp(0f, 0f, height, width)
        end.clamp(0f, 0f, height, width)

        val playerX = start.x
        val playerY = start.y
        val targetX = end.x
        val targetY = end.y


        if (obstacles.isEmpty()) {
            return arrayOf(end)
        }
        //        long startTime = System.currentTimeMillis();
        val blocked2 = ArrayList<IntArray>()
        for (turret in obstacles) {
//            if(turret.getTeam() == player.getTeam()){
//                continue;
//            }
            val range = World.conv(turret.radius).toInt()
            val x = World.conv(turret.x).toInt()
            val y = World.conv(turret.y).toInt()
            Geometry.circle(x, y, range) { tx, ty ->
                if (tx < 0 || tx >= tileWidth || ty < 0 || ty >= tileHeight) {
                    return@circle
                }
                blocked2.add(intArrayOf(tx, ty))
            }
//            var colNum = 0
//            while (colNum <= width - 1) {
//                if (colNum > x + range) {
//                    colNum += 1
//                    continue
//                }
//                if (colNum < x - range) {
//                    colNum += 1
//                    continue
//                }
//                var blockNum = 0
//                while (blockNum <= height - 1) {
//                    if (blockNum > y + range) {
//                        blockNum += 1
//                        continue
//                    }
//                    if (blockNum < y - range) {
//                        blockNum += 1
//                        continue
//                    }
//                    if (Mathf.sqrt(Mathf.pow(x - colNum, 2f) + Mathf.pow(y - blockNum, 2f)) < range) {
//                        blocked2.add(intArrayOf(colNum, blockNum))
//                    }
//                    blockNum += 1
//                }
//                colNum += 1
//            }
        }
        val blocked = blocked2.toTypedArray()
//        var b = 0
//        while (b <= blocked2.size - 1) {
//            blocked[b] = blocked2[b]
//            b += 1
//        }
        //Reset
        val px = World.toTile(playerX)
        val py = World.toTile(playerY)
        val ex = World.toTile(targetX)
        val ey = World.toTile(targetY)
        grid = emptyArray()
        grid = Array(tileWidth) { arrayOfNulls(tileHeight) }
        closed = emptyArray()
        closed = Array(tileWidth) { BooleanArray(tileHeight) }
        costly = emptyArray()
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

        /*
             Set blocked cells. Simply set the cell values to null
             for blocked cells.
           */
        for (ints in blocked) {
            if (block) {
                setBlocked(ints[0], ints[1])
            } else {
                costly[ints[0]][ints[1]] = true
            }
        }
        grid[px][py] = Cell(px, py)

        //Display initial map
//        System.out.println("Grid: ");
//        for(int i = 0; i < width; ++i){
//            for(int j = 0; j < height; ++j){
//                if(i == px && j == py) System.out.print("SO  "); //Source
//                else if(i == ex && j == ey) System.out.print("DE  ");  //Destination
//                else if(grid[i][j] != null) System.out.printf("%-3d ", 0);
//                else System.out.print("BL  ");
//            }
//            System.out.println();
//        }
//        System.out.println();
//        System.out.println("eifwief");
//        System.out.println(grid.length);
//        System.out.println(grid[0].length);
        aStarSearch()
        //        System.out.println("\nScores for cells: ");
//        for(int i = 0; i < width; ++i){
//            for(int j = 0; j < height; ++j){
//                if(grid[i][j] != null) System.out.printf("%-3d ", grid[i][j].finalCost);
//                else System.out.print("BL  ");
//            }
//            System.out.println();
//        }
//        System.out.println();
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