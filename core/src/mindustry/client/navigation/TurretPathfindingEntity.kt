package mindustry.client.navigation

import arc.func.*
import arc.math.geom.*
import arc.math.geom.QuadTree.*
import arc.struct.*
import mindustry.Vars.*
import mindustry.gen.*
import mindustry.logic.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

class TurretPathfindingEntity(@JvmField val entity: Ranged, @JvmField val range: Float, @JvmField val targetGround: Boolean, @JvmField val targetAir: Boolean, private val canShoot: Boolp, @JvmField val mustAvoid: Boolean) : QuadTreeObject {
    var id = 0L

    fun mustAvoid() = mustAvoid
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
    }

    override fun equals(other: Any?): Boolean {
        return (other as? TurretPathfindingEntity)?.id == id
    }

    override fun hashCode(): Int {
        return java.lang.Long.hashCode(id)
    }

    override fun hitbox(out: Rect) {
        out.setCentered(entity.x, entity.y, range * 2)
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
        val range = range + tilesize / 2F
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
class EntityTree private constructor(bounds: Rect, val lock: ReentrantLock) : QuadTree<TurretPathfindingEntity>(bounds) {
    constructor(bounds: Rect) : this(bounds, ReentrantLock(true))

    override fun newChild(rect: Rect) = EntityTree(rect, lock)

    inline fun <T> use(action: EntityTree.() -> T): T = lock.withLock { this.action() } // FINISHME: Use this instead of locking in each function, should improve performance

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
}