package mindustry.client.navigation

import arc.*
import arc.math.geom.*
import arc.struct.*
import mindustry.*
import mindustry.client.navigation.Path.Companion.goTo
import mindustry.client.navigation.clientThread.post
import mindustry.client.navigation.clientThread.submit
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
    private var tmpTree = EntityTree(Rect(0f, 0f, 0f, 0f))
    private var obstacles = Seq<TurretPathfindingEntity>()
    lateinit var navigator: Navigator

    init {
        Events.on(WorldLoadEvent::class.java) {
            obstacleTree = EntityTree(Vars.world.getQuadBounds(Rect()))
            tmpTree = EntityTree(Vars.world.getQuadBounds(Rect()))
            ents.shrink(51)
        }
    }

    @JvmStatic fun addEnt(ent: TurretPathfindingEntity) = post { ents.add(ent) }
    @JvmStatic fun removeEnt(ent: TurretPathfindingEntity) = post { ents.remove(ent) }

    @JvmOverloads @JvmStatic
    fun follow(path: Path?, repeat: Boolean = false) {
        stopFollowing()
        currentlyFollowing = path ?: return
        path.init()
        state = NavigationState.FOLLOWING
        Path.repeat = repeat
    }

    private var job = CompletableFuture.completedFuture<Seq<TurretPathfindingEntity>>(null)
    private var updatingEnts = AtomicInteger(0)
    private var lastFrame = 0L

    /** Client thread only */
    private fun updateEnts(force: Boolean = false): Seq<TurretPathfindingEntity> {
        if ((force || updatingEnts.get() <= 0) && Core.graphics.frameId > lastFrame) { // Update once per frame
            lastFrame = Core.graphics.frameId
            val tree = tmpTree
            tree.clear()
            obstacles = Seq()
            for (ent in ents) {
                if (ent.entity.team() != Vars.player.team()) {
                    tree.insert(ent)
                    obstacles.add(ent)
                }
            }
            tmpTree = obstacleTree
            obstacleTree = tree
        }
        return obstacles
    }

    @JvmStatic
    /** Thread safe */
    fun getEnts(): Seq<TurretPathfindingEntity> {
        if (Thread.currentThread().name == "main") {
            if (updatingEnts.get() <= 0) submit(::updateEnts).get()
            updatingEnts.set(2)
        } else updateEnts()
        return obstacles
    }

    /** Thread safe */
    @JvmStatic
    fun getTree(): EntityTree {
        if (Thread.currentThread().name == "main") {
            if (updatingEnts.get() <= 0) submit(::updateEnts).get()
            updatingEnts.set(2)
        } else updateEnts()
        return obstacleTree
    }

    fun update() {
        if (job.isDone && updatingEnts.get() > 0) {
            if (updatingEnts.get() == 1) obstacles.set(job.get())
            job = submit { updateEnts(true) }
            updatingEnts.decrementAndGet()
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
    fun navigateTo(pos: Position?) = pos?.let { navigateTo(it.x, it.y) }

    @JvmStatic
    fun navigateTo(drawX: Float, drawY: Float) {
        goTo(drawX, drawY, 0f, 0f) {
            Core.app.post {
                if (Core.settings.getBool("assumeunstrict")) return@post
                follow(it)
                navigateToInternal(drawX, drawY)
            }
        }
    }

    private fun navigateToInternal(drawX: Float, drawY: Float) {
        goTo(drawX, drawY, 0f, 0f) {
            if (currentlyFollowing == it && Core.settings.getBool("pathnav")) post { navigateToInternal(drawX, drawY) }
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