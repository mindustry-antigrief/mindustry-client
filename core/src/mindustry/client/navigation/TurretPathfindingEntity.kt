package mindustry.client.navigation

import arc.func.*
import arc.math.geom.*
import arc.math.geom.QuadTree.*
import arc.struct.*
import mindustry.*
import mindustry.Vars.*
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

// FINISHME: Awful.
class EntityTree(bounds: Rect) : QuadTree<TurretPathfindingEntity>(bounds) {
    companion object {
        val lock = ReentrantLock(true)
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

    override fun remove(obj: TurretPathfindingEntity?): Boolean {
        lock.withLock {
            return super.remove(obj)
        }
    }

    override fun getObjects(out: Seq<TurretPathfindingEntity>?) {
        lock.withLock {
            super.getObjects(out)
        }
    }

    override fun clear() {
        lock.withLock {
            super.clear()
        }
    }
}