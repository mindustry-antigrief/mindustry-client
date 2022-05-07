package mindustry.client.navigation

import arc.func.*
import arc.math.geom.*
import arc.math.geom.QuadTree.*
import arc.struct.*
import mindustry.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.gen.*
import mindustry.logic.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

class TurretPathfindingEntity(@JvmField val entity: Ranged, @JvmField var range: Float, @JvmField val targetGround: Boolean, @JvmField val targetAir: Boolean, private val canShoot: Boolp) : QuadTreeObject {
    var id = 0L

    fun canShoot() = canShoot.get()
    fun canHitPlayer() = if (player.unit().isFlying) targetAir else targetGround
    fun x() = entity.x
    fun y() = entity.y
    @JvmField val turret = entity is Building

    companion object {
        private var nextId: Long = 0
    }

    init {
        id = nextId++
        range += Vars.tilesize
    }

    override fun equals(other: Any?): Boolean {
        return (other as? TurretPathfindingEntity)?.id == id
    }

    override fun hashCode(): Int {
        return java.lang.Long.hashCode(id)
    }

    override fun hitbox(out: Rect) {
        out.setCentered(entity.x, entity.y, (range - Vars.tilesize) * 2)
    }

    /**
     * Checks whether this circle contains a given point.
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if this circle contains the given point.
     */
    fun contains(x: Float, y: Float): Boolean {
        val dx = entity.x - x
        val dy = entity.y - y
        val range = range - Vars.tilesize
        return dx * dx + dy * dy <= range * range
    }

    /**
     * Checks whether this circle overlaps a given rectangle.
     * @param x Rectangle X origin
     * @param y Rectangle Y origin
     * @param w Rectangle width
     * @param h Rectangle height
     * @return true if this circle overlaps the given rectangle
     */
    fun overlaps(x: Float, y: Float, w: Float, h: Float): Boolean {
        return contains(entity.x.coerceIn(x, x + w), entity.y.coerceIn(y, y + h))
    }
}

class EntityTree(bounds: Rect) : QuadTreeMk2<TurretPathfindingEntity>(bounds) {
    companion object {
        val lock = ReentrantLock(true)
    }

    /**
     * @return whether an object overlaps this rectangle.
     * This will never result in false positives.
     */
    @Suppress("NAME_SHADOWING")
    override fun any(x: Float, y: Float, w: Float, h: Float): Boolean {
        lock.withLock {
            val half = tilesize / 2f
            val x = x + half
            val y = y + half
            val w = w - half
            val h = h - half
            if (stack == null) stack = Seq()
            stack.add(this)

            while (stack.size > 0) {
                val curr = stack.pop()
                val objects: Seq<*> = curr.objects

                for (i in 0 until objects.size) {
                    val item = objects.items[i] as TurretPathfindingEntity
                    hitbox(item)
                    if (tmp.overlaps(x, y, w, h) && item.overlaps(x, y, w, h)) {
                        stack.clear()
                        return true
                    }
                }
                if (curr.leaf) continue
                if (topLeft.bounds.overlaps(x, y, w, h)) stack.add(curr.topLeft)
                if (topRight.bounds.overlaps(x, y, w, h)) stack.add(curr.topRight)
                if (botLeft.bounds.overlaps(x, y, w, h)) stack.add(curr.botLeft)
                if (botRight.bounds.overlaps(x, y, w, h)) stack.add(curr.botRight)
            }
            return false
        }
    }

    override fun insert(obj: TurretPathfindingEntity?) {
        lock.withLock {
            super.insert(obj)
        }
    }

    override fun intersect(x: Float, y: Float, width: Float, height: Float, out: Cons<TurretPathfindingEntity>?) {
        lock.withLock {
            super.intersect(x, y, width, height, out)
        }
    }

    override fun intersect(x: Float, y: Float, width: Float, height: Float, out: Seq<TurretPathfindingEntity>?) {
        lock.withLock {
            super.intersect(x, y, width, height, out)
        }
    }

    override fun remove(obj: TurretPathfindingEntity?) {
        lock.withLock {
            super.remove(obj)
        }
    }

    override fun getObjects(out: Seq<TurretPathfindingEntity>?) {
        lock.withLock {
            super.getObjects(out)
        }
    }

    override fun getTotalObjectCount(): Int {
        lock.withLock {
            return super.getTotalObjectCount()
        }
    }

    override fun clear() {
        lock.withLock {
            super.clear()
        }
    }
}