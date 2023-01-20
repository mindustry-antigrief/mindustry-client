package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import mindustry.*
import mindustry.client.navigation.waypoints.*
import mindustry.game.EventType.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

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
    private var lastFrame = 0L

    /** Client thread only */
    private fun updateEnts(force: Boolean = false): Seq<TurretPathfindingEntity> {
        if ((force || updatingEnts.get() <= 0) && Core.graphics.frameId > lastFrame) { // Update once per frame
            lastFrame = Core.graphics.frameId
            val tree = tmpTree
            obstacles = Seq()
            tree.use {
                tree.clear()
                for (ent in ents) {
                    if (ent.entity.team() != Vars.player.team()) {
                        tree.insert(ent)
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
        if ((force || updatingAllyEnts.get() <= 0) && Core.graphics.frameId > lastFrame) { // Update once per frame
            lastFrame = Core.graphics.frameId
            val tree = tmpTree
            allies = Seq()
            tree.use {
                tree.clear()
                for (ent in ents) {
                    if (ent.entity.team() == Vars.player.team()) {
                        tree.insert(ent)
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
