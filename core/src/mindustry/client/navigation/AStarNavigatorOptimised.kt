package mindustry.client.navigation

import arc.math.Mathf
import arc.math.geom.*
import arc.struct.*
import arc.util.*
import arc.util.pooling.*
import mindustry.Vars.*
import mindustry.client.ClientVars
import mindustry.client.navigation.waypoints.*
import mindustry.core.*
import java.lang.StringBuilder
import kotlin.math.*

// Taken from http://www.codebytes.in/2015/02/a-shortest-path-finding-algorithm.html
// and modified
// Homework as an exercise for the reader:
// https://svn.sable.mcgill.ca/sable/courses/COMP763/oldpapers/yap-02-grid-based.pdf HEXES LOL for IDA*
// https://arxiv.org/ftp/arxiv/papers/1506/1506.01864.pdf the stuff looks familiar

object AStarNavigatorOptimised : Navigator() {
    private val pool = Pools.get(PositionWaypoint::class.java) { PositionWaypoint() }
    private var grid: Array<Cell> = emptyArray()
    private var gridSize = Point2()
    private var open = BinaryHeap<Cell>(1 shl 16, false)
    private var startX = -1
    private var startY = -1
    private var endX = 0
    private var endY = 0
    private var tileWidth = 0
    private var tileHeight = 0
    private val points = mutableListOf<PositionWaypoint>()

    private val d8x: Array<Int> = arrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val d8y: Array<Int> = arrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private inline fun d8(cons: (x: Int, y: Int, diag: Boolean) -> Unit) {
        cons(1, 0, false)
        cons(1, 1, true)
        cons(0, 1, false)
        cons(-1, 1, true)
        cons(-1, 0, false)
        cons(-1, -1, true)
        cons(0, -1, false)
        cons(1, -1, true)
    }

    private fun cell(x: Int, y: Int) = grid[x + (y * tileWidth)]

    override fun init() {}

    /** Calculates the distance heuristic for this cell */
    private fun h(cell: Cell): Float {
        val dx = abs(cell.x - endX)
        val dy = abs(cell.y - endY)
        return dx + dy - 1.414f * min(dx, dy)
    }

    private fun dist(x1: Int, y1: Int, x2: Int, y2: Int) = sqrt(1f * (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))

    private fun checkAndUpdateCost(current: Cell, t: Cell, cost: Float) {
        if (!t.closed || cost < t.g) {
            t.closed = true
            t.g = cost
            t.cameFrom = current
            addToHeap(t, cost + h(t))
        }
    }

    private fun addToHeap(t: Cell, value: Float) {
        if (t.inHeap) {
            open.setValue(t, value)
        } else {
            open.add(t, value)
            t.inHeap = true
        }
    }

    /**@param checkCorners whether to check corners for diagonal movement **/
    private fun aStarSearch(checkCorners: Boolean = false) {
        //add the start location to open list.
        addToHeap(cell(startX, startY), Float.MAX_VALUE)
        cell(startX, startY).closed = true

        var current: Cell
        while (!open.isEmpty) {
            current = open.pop() // Get a tile to explore
            current.inHeap = false
            if (current === cell(endX, endY) || current.value == Float.POSITIVE_INFINITY) { // Made it to the finish
                return
            }

            // Check surrounding tiles
            d8 { x1, y1, diag ->
                val x = current.x + x1
                val y = current.y + y1

                if (!Structs.inBounds(x, y, tileWidth, tileHeight)) {
                    return@d8
                }

                if (checkCorners && diag) {
                    if (cell(x, current.y).blocked || cell(current.x, y).blocked) return@d8
                }
                // Add to the open list with calculated cost
                checkAndUpdateCost(
                    current,
                    cell(x, y),
                    current.g * (if (diag) 1.00001f else 1f) + cell(x, y).added * if (diag) 1.414f else 1f // Tiebreaker is needed to draw correct path
                )
            }
        }
    }

    private fun relaxPath() {
        if (!cell(endX, endY).closed) return
        val start = cell(startX, startY)
        val end = cell(endX, endY)
        var current: Cell? = end
        var p: Cell? = null
        val path = Seq<Cell>()
        while (current != null) {
            path.add(current)
            current.goesTo = p
            p = current
            current = current.cameFrom
        }
        path.reverse()
        val psize = path.size

        //pointSpacing.clear()
        val pathIndices = ObjectIntMap<Cell>(psize)
        for (i in 0 until psize) {
            pathIndices.put(path[i], i)
            //pointSpacing.put(cell, cell.goesTo?.g?.minus(cell.g) ?: continue)
        }
        val spaces = FloatArray(psize - 1) { path[it].goesTo?.g?.minus(path[it].g) ?: Float.POSITIVE_INFINITY}
        if (spaces.any { it == Float.POSITIVE_INFINITY }) {
            Log.debug("oh no we have an infinity (sad)")
        }
        val removed = Bits(psize) // the nodes that have been removed (we skip them)
        val recycle = Bits(psize) // that's kinda funny
        val inQueue = Bits(psize)
        val queue = IntQueue(psize)
        var debug = psize
        var hasRelaxed = true
        while (start.goesTo !== end) {
            if (queue.isEmpty) {
                if (!hasRelaxed) break
                hasRelaxed = false
                //Log.debug("Populating queue (path: @)", debug)
                var curr = start.goesTo
                while (curr != null) {
                    if (!collinear(curr)) queue.addLast(pathIndices[curr])
                    curr = curr.goesTo
                }
                //Log.debug("Queue populated (size: @)", queue.size)
                if (queue.isEmpty) break
            }
            while (!queue.isEmpty) {
                val i = queue.removeFirst()
                //Log.debug("Evaluating @ (@ more in queue)", i, queue.size)
                if (removed.get(i)) continue // do not clear the inQueue status of removed (whats the point)
                if (recycle.getAndClear(i)) {
                    //Log.debug("Recycled @", i)
                    queue.addLast(i)
                    continue
                }
                inQueue.clear(i)
                val curr = path[i]
                val prev = curr.cameFrom?: continue
                val next = curr.goesTo?: continue
                if (collinear(prev, curr, next)) continue
                val cost = spaces[pathIndices[prev]] + spaces[i]
                val newCost = lineOfSight(prev.x, prev.y, next.x, next.y)
                if (newCost <= cost) {
                    hasRelaxed = true
                    //Log.debug("Relaxed @", i)
                    prev.goesTo = next
                    next.cameFrom = prev
                    curr.goesTo = null
                    curr.cameFrom = null
                    removed.set(i)
                    debug--
                    val prevI = pathIndices[prev]
                    val nextI = pathIndices[next]
                    spaces[prevI] = newCost

                    recycle.set(nextI) // move on to the next corner - don't focus on reevaluating the same one
                    if (!inQueue.getAndSet(prevI)) queue.addLast(prevI)
                    if (!inQueue.getAndSet(nextI)) queue.addLast(nextI)
                }
            }
        }
        // FIXME: Optimise this, especially at the straight sections they can be reduced
        // Worst case scenario: O(n^2) (very bad)(very very bad)
        // TODO: Set an upper bound on number of passes
        // TODO: Make relaxation prefer the direction with longer straight, so that relaxation is more optimal
    }

    private fun collinear(c: Cell): Boolean {
        return collinear(c.cameFrom?: return true, c, c.goesTo?: return true)
    }
    private fun collinear(u: Cell, v: Cell, w: Cell): Boolean {
        return collinear(u.x, u.y, v.x, v.y, w.x, w.y)
    }
    private fun collinear(x1: Int, y1: Int, x2: Int, y2: Int, x3: Int, y3: Int): Boolean {
        return (y3 - y2) * (x2 - x1) == (y2 - y1) * (x3 - x2)
    }

    private fun diagonalLineOfSight(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        return if (cell(x1, y2).blocked || cell(x2, y1).blocked) Float.POSITIVE_INFINITY else Mathf.sqrt2
    }

    private const val step_count = 200 // TODO: Use fixed step size or just get overlapped pixels and do math
    // TODO: yeah we will need overlapped pixels since the ray might land on the corner
    private fun lineOfSight(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val absdx = abs(x1 - x2)
        if (absdx == 1 && absdx == abs(y1 - y2)) return diagonalLineOfSight(x1, y1, x2, y2)
        var cost = 0f
        var x = x1.toFloat()
        var y = y1.toFloat()
        val step = dist(x1, y1, x2, y2) / step_count // 200 steps per ray
        val dx = (x2.toFloat() - x) / step_count
        val dy = (y2.toFloat() - y) / step_count
        for (i in 0 until step_count) {
            x += dx
            y += dy
            if (cell(x.roundToInt(), y.roundToInt()).blocked) return Float.POSITIVE_INFINITY
            cost += (cell(x.roundToInt(), y.roundToInt()).added - 1) * step
        }
        return cost + dist(x1, y1, x2, y2)
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

        // TODO: VERY long init time when ground (cache friendliness?)
        var hasBlocked = false
        // Reset all cells
        for (x in 0 until tileWidth) {
            for (y in 0 until tileHeight) {
                val cell = cell(x, y)
                cell.g = 0f
                cell.cameFrom = null
                cell.goesTo = null
                cell.blocked = blocked(x, y)
                hasBlocked = hasBlocked || cell.blocked
                cell.closed = cell.blocked
                cell.added = if (cell.blocked) Float.POSITIVE_INFINITY else 1f
                cell.inHeap = false
            }
        }

        for (turret in obstacles) {
            Geometry.circle(World.toTile(turret.x), World.toTile(turret.y), World.toTile(turret.radius)) { x, y ->
                if (Structs.inBounds(x, y, tileWidth, tileHeight)) cell(x, y).added += 100f
            }
        }

        if (!cell(endX, endY).blocked) { // don't bother searching if it's blocked off
            val t1 = Time.nanos()
            aStarSearch(hasBlocked)
            val t2 = Time.nanos()
            relaxPath()
            val t3 = Time.nanos()
            points.clear()
            if (cell(endX, endY).closed) {
                //Trace back the path
                var current: Cell? = cell(endX, endY)
                while (current != null) {
                    points.add(pool.obtain().set(World.unconv(current.x.toFloat()), World.unconv(current.y.toFloat())))
                    current = current.cameFrom
                }
                points.reverse()
                if (hasBlocked) {
                    val tileSize = tilesize.toFloat()
                    for (c in points) { // adjust tolerance values
                        val cx = World.toTile(c.x)
                        val cy = World.toTile(c.y)
                        var blockedNeighbour = false
                        for (j in d8x.indices) {
                            val x = cx + d8x[j]
                            val y = cy + d8y[j]
                            if (Structs.inBounds(x, y, tileWidth, tileHeight) && cell(x, y).blocked) {
                                blockedNeighbour = true
                                break
                            }
                        }
                        if (blockedNeighbour) {
                            c.tolerance = tileSize / 2f // set tolerance to only that tile
                        }
                    }
                    // corner identification
                    val v1 = Vec2()
                    val v2 = Vec2() //TODO: move this out
                    for (i in 1 until points.size - 1) {
                        val c = points[i]
                        v1.set(points[i + 1].x - c.x, points[i + 1].y - c.y)
                        v2.set(points[i - 1].x - c.x, points[i - 1].y - c.y)
                        val angle = v1.angle(v2)
                        if (abs(90f - abs(angle)) < 15f) { // if it is a corner
                            v1.add(v2).setLength(tileSize) // get midpoint of the angle
                            val ccx = World.conv(c.x + v1.x).roundToInt()
                            val ccy = World.conv(c.y + v1.y).roundToInt()
                            if (!Structs.inBounds(ccx, ccy, tileWidth, tileHeight)) continue
                            if (cell(ccx, ccy).blocked) {
                                //c.set(c.x - v1.x * 0.25f, c.y - v1.y * 0.25f, c.tolerance / 2, c.distance)
                                c.mustPassThrough = points[i - 1]
                            }
                        }
                    }
                }
            }
            val t4 = Time.nanos()
            if (ClientVars.benchmarkNav) Log.debug(
                "AStarNavigatorOptimised took @ us (@ init, @ A*, @ relax, @ tol [blocked: @])",
                ff(t4 - t0), ff(t1 - t0), ff(t2 - t1), ff(t3 - t2), ff(t4 - t3), hasBlocked
            )
        } else {
            val t3 = Time.nanos()
            if (ClientVars.benchmarkNav) Log.debug(
                "AStarNavigatorOptimised took @ us (@ init, not pathed)", ff(t3 - t0), ff(t3 - t0))
            points.clear()
        }
        return points.toTypedArray()
    }

    class Cell(var x: Int, var y: Int) : BinaryHeap.Node(Float.POSITIVE_INFINITY) {
        var g = 0f // cost so far
        // f has been moved to value
        var added = 1f
        var cameFrom: Cell? = null
        var goesTo: Cell? = null
        var closed = false
        var blocked = false
        var inHeap = false

        override fun toString(): String {
            return "[$x, $y]"
        }
    }

    private val tmp1 = StringBuilder()
    private val tmp2 = StringBuilder()
    //private fun ff(num: Long) = Strings.fixed(num / 1000f, 1) //not thread safe
    private fun ff(num: Long): String {
        val d = num / 1000f
        val decimalPlaces = 1
        val dec = tmp2
        dec.setLength(0)
        dec.append((d * 10.0.pow(decimalPlaces.toDouble()) + 0.000001f).toInt())

        val len: Int = dec.length
        var decimalPosition: Int = len - decimalPlaces
        val result = tmp1
        result.setLength(0)
        if (decimalPosition > 0) {
            // Insert a dot in the right place
            result.append(dec, 0, decimalPosition)
            result.append(".")
            result.append(dec, decimalPosition, dec.length)
        } else {
            result.append("0.")
            // Insert leading zeroes into the decimal part
            while (decimalPosition++ < 0) {
                result.append("0")
            }
            result.append(dec)
        }
        return result.toString()
    }
}
