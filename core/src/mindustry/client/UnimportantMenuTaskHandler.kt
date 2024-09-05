package mindustry.client

import arc.*
import arc.util.*
import mindustry.Vars.*
import mindustry.game.*
import mindustry.gen.*

// This class is just a simple way to keep track of all the simple lazy loading work the client does
// FINISHME: While it is nice to have this all in one place, it would still be better to have a proper system for queuing background texture loading with priorities and such in an abstract way instead of having to implement that behavior for every class
private var lastExpensiveSyncTask: Long = 0

/** Start async background work when the client loads */
fun handleMenuTasksAsync() = Core.app.post {
    Events.run(EventType.Trigger.update, ::handleMenuTasksSync) // Allow work to begin on sync portions of tasks next frame
    mainExecutor.execute {
        { Musics.load(true); Sounds.load(true) }.named("sound & music")
        maps::loadPreviewsAsync.named("map previews")
    }
}

/** Runs every frame, loads sync portions of resources with a time limit. All called from one place so we can easily control the time per frame spent on this work */
fun handleMenuTasksSync() { // Yes, I know the current implementation is very messy.
    val start = Time.millis()
    val limit = Core.settings.getInt("maxsyncbackgroundtaskduration", 15)

    lastExpensiveSyncTask = control.saves.processQueue(start, lastExpensiveSyncTask, limit)
    if(Time.timeSinceMillis(start) > limit) return
    lastExpensiveSyncTask = maps.processQueue(start, lastExpensiveSyncTask) // Low priority: loads maps in the background
    if(Time.timeSinceMillis(start) > limit) return
    mods.loadIcons(start, limit) // Lowest priority: Loads mod icons in the background.
}

/** Convenience method to easily time lazy loading async tasks */
private fun (() -> Any?).named(name: String) {
    val start = Time.nanos()
    this()
    Log.debug("Lazy loaded $name in ${Time.millisSinceNanos(start)}ms (async)")
}