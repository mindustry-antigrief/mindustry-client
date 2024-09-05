package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import mindustry.*
import mindustry.client.navigation.waypoints.*
import mindustry.game.EventType.*
import mindustry.gen.Unit
import mindustry.type.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.math.*

object Navigation {
    @JvmField var currentlyFollowing: Path? = null
    @JvmField var isPaused = false
    @JvmField var state = NavigationState.NONE

    @JvmField var recordedPath: WaypointPath<Waypoint>? = null

    private val ents = ObjectSet<TurretPathfindingEntity>()
    private var obstacleTree = EntityTree(Rect(0f, 0f, 0f, 0f))
    private var allyTree = EntityTree(Rect(0f, 0f, 0f, 0f))
    private var tmpTree = EntityTree(Rect(0f, 0f, 0f, 0f))
    private var obstacles = Seq<TurretPathfindingEntity>()
    private var allies = Seq<TurretPathfindingEntity>()
    lateinit var navigator: Navigator
    // Used to setup unit pathfinding ents
    private val weaponSet: IntSet = IntSet(4)
    private val numWeapons: ObjectIntMap<UnitType> = ObjectIntMap()

    init {
        Events.on(WorldLoadEvent::class.java) {
            obstacleTree = EntityTree(Vars.world.getQuadBounds(Rect()))
            allyTree = EntityTree(Vars.world.getQuadBounds(Rect()))
            tmpTree = EntityTree(Vars.world.getQuadBounds(Rect()))
            ents.shrink(51)
        }
    }

    @JvmStatic fun addEnt(ent: TurretPathfindingEntity) = clientThread.post { ents.add(ent) }
    @JvmStatic fun removeEnt(ent: TurretPathfindingEntity) = clientThread.post { ents.remove(ent) }
    @JvmStatic fun removeEnts(ent: Array<TurretPathfindingEntity>) = clientThread.post { ents.removeAll(ent) }
    @JvmStatic fun setupEnts(u: Unit): Array<TurretPathfindingEntity?>? {
        var n = numWeapons.get(u.type, -1)
        if (n == 0) return null
        if (n == -1) { // Add missing unit type to the map. This allows us to create arrays that are exactly the right size to reduce garbage
            val s = IntSet()
            u.type.weapons.forEach { s.add(it.bullet.hashCode()) } // Yes, this is duplicated code. No, I do not care
            numWeapons.put(u.type, s.size)
            n = s.size
            if (n == 0) return null // Check again since this could have become 0
        }
        val ret = arrayOfNulls<TurretPathfindingEntity>(n)
        clientThread.post { // The entities will get added by the client thread at a later point
            var i = 0
            u.type.weapons.forEach { w -> // FINISHME: Cache the result of this loop for each UnitType instead of computing hashes every time
                if (weaponSet.add(w.bullet.hashCode())) ret[i++] = TurretPathfindingEntity(u, { max(24f, w.bullet.range) }, w.bullet.collidesGround, w.bullet.collidesAir, { u.canShoot() })
            }
            ents.addAll(ret, 0, ret.size) // Avoid using spread operator as it creates a copy of the array
            weaponSet.clear() // We reuse one set for efficiency
        }
        return ret
    }

    @JvmOverloads @JvmStatic
    fun follow(path: Path?, repeat: Boolean = false) {
        stopFollowing()
        currentlyFollowing = path ?: return
        path.init()
        state = NavigationState.FOLLOWING
        Path.repeat = repeat
    }

    private var job = CompletableFuture.completedFuture<Seq<TurretPathfindingEntity>>(null)
    private var allyJob = CompletableFuture.completedFuture<Seq<TurretPathfindingEntity>>(null)
    private var updatingEnts = AtomicInteger(0)
    private var updatingAllyEnts = AtomicInteger(0)
    private var lastEntFrame = 0L
    private var lastAllyEntFrame = 0L

    /** Client thread only */
    private fun updateEnts(force: Boolean = false): Seq<TurretPathfindingEntity> {
        if ((force || updatingEnts.get() <= 0) && Core.graphics.frameId > lastEntFrame) { // Update once per frame
            lastEntFrame = Core.graphics.frameId
            val tree = tmpTree
            obstacles = Seq()
            tree.use {
                clear()
                for (ent in ents) {
                    if (ent.entity.team() != Vars.player.team()) {
                        insert(ent)
                        obstacles.add(ent)
                    }
                }
            }
            tmpTree = obstacleTree
            obstacleTree = tree
        }
        return obstacles
    }

    /** Client thread only */
    private fun updateAllyEnts(force: Boolean = false): Seq<TurretPathfindingEntity> {
        if ((force || updatingAllyEnts.get() <= 0) && Core.graphics.frameId > lastAllyEntFrame) { // Update once per frame
            lastAllyEntFrame = Core.graphics.frameId
            val tree = tmpTree
            allies = Seq()
            tree.use {
                clear()
                for (ent in ents) {
                    if (ent.entity.team() == Vars.player.team()) {
                        insert(ent)
                        allies.add(ent)
                    }
                }
            }
            tmpTree = allyTree
            allyTree = tree
        }
        return allies
    }

    @JvmStatic
    /** Thread safe */
    fun getEnts(): Seq<TurretPathfindingEntity> {
        if (Thread.currentThread().name == "main") {
            if (updatingEnts.get() <= 0) clientThread.submit(::updateEnts).get()
            updatingEnts.set(2)
        } else updateEnts()
        return obstacles
    }

    /** Thread safe */
    @JvmStatic
    fun getTree(): EntityTree {
        if (Thread.currentThread().name == "main") {
            if (updatingEnts.get() <= 0) clientThread.submit(::updateEnts).get()
            updatingEnts.set(2)
        } else updateEnts()
        return obstacleTree
    }

    @JvmStatic
    /** Thread safe */
    fun getAllyEnts(): Seq<TurretPathfindingEntity> {
        if (Thread.currentThread().name == "main") {
            if (updatingAllyEnts.get() <= 0) clientThread.submit(::updateAllyEnts).get()
            updatingAllyEnts.set(2)
        } else updateAllyEnts()
        return allies
    }

    /** Thread safe */
    @JvmStatic
    fun getAllyTree(): EntityTree {
        if (Thread.currentThread().name == "main") {
            if (updatingAllyEnts.get() <= 0) clientThread.submit(::updateAllyEnts).get()
            updatingAllyEnts.set(2)
        } else updateAllyEnts()
        return allyTree
    }

    fun update() {
        if (job.isDone && updatingEnts.get() > 0) {
            if (updatingEnts.get() == 1) obstacles.set(job.get())
            job = clientThread.submit { updateEnts(true) }
            updatingEnts.decrementAndGet()
        }

        if (allyJob.isDone && updatingAllyEnts.get() > 0) {
            if (updatingAllyEnts.get() == 1) allies.set(allyJob.get())
            allyJob = clientThread.submit { updateAllyEnts(true) }
            updatingAllyEnts.decrementAndGet()
        }

        if (!isPaused && !Vars.state.isPaused) {
            currentlyFollowing?.run {
                follow()
                if (isDone()) stopFollowing()
            }
        }
    }

    @JvmStatic
    fun draw() {
        if (isFollowing && Core.settings.getBool("drawpath")) currentlyFollowing?.draw()
        if (state == NavigationState.RECORDING) recordedPath?.draw()
    }

    @JvmStatic
    fun stopFollowing() {
        val lastPath = currentlyFollowing
        currentlyFollowing = null
        state = NavigationState.NONE
        lastPath?.onFinish()
    }

    @JvmStatic val isFollowing get() = currentlyFollowing != null && !isPaused

    @JvmStatic
    fun navigateTo(pos: Position?) = pos?.apply { navigateTo(x, y) }

    @JvmStatic
    fun navigateTo(drawX: Float, drawY: Float) {
        Path.goTo(drawX, drawY, 0f, 0f) {
            Core.app.post {
                if (Core.settings.getBool("assumeunstrict")) return@post
                follow(it)
                navigateToInternal(drawX, drawY)
            }
        }
    }

    private fun navigateToInternal(drawX: Float, drawY: Float) {
        Path.goTo(drawX, drawY, 0f, 0f) {
            if (currentlyFollowing == it && Core.settings.getBool("pathnav")) clientThread.post { navigateToInternal(drawX, drawY) }
        }
    }

    @JvmStatic
    fun startRecording() {
        state = NavigationState.RECORDING
        recordedPath = WaypointPath()
    }

    @JvmStatic
    fun stopRecording() {
        state = NavigationState.NONE
    }

    @JvmStatic
    fun addWaypointRecording(waypoint: Waypoint) {
        if (state != NavigationState.RECORDING) return
        recordedPath?.run {
            add(waypoint)
            setShow(true)
        }
    }
}
