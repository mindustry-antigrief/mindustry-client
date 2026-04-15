package mindustry.client

import arc.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.game.*
import mindustry.gen.*

// This class is just a simple way to keep track of all the simple lazy loading work the client does
// FINISHME: While it is nice to have this all in one place, it would still be better to have a proper system for queuing background texture loading with priorities and such in an abstract way instead of having to implement that behavior for every class

/** Start async background work when the client loads */
fun handleMenuTasksAsync() = Core.app.post {
    Events.run(EventType.Trigger.update) { BackgroundTask.update() } // Allow work to begin on sync portions of tasks next frame
    mainExecutor.execute {
        { Musics.load(true); Sounds.load(true) }.named("sound & music")
        maps::loadPreviewsAsync.named("map previews")
    }
}

/** Convenience method to easily time lazy loading async tasks */
private fun (() -> Any?).named(name: String) {
    val start = Time.nanos()
    this()
    Log.debug("Lazy loaded $name in ${Time.millisSinceNanos(start)}ms (async)")
}

/** [longTaskDuration] > 0 are tasks that will take more than a handful of millis. Only one is performed at a time with a gap of [longTaskDuration] before the next */
abstract class BackgroundTask<T>(val longTaskDuration: Int = 0, @JvmField val units: Queue<T> = Queue()) {
    private var queued = false

    /** @return whether the task is complete */
    abstract fun processStep(): Boolean

    /** @return whether the tak should be processed */
    open fun shouldProcess(): Boolean = true

    /** Add extra units to this task, automatically queues the task if needed. */
    @Synchronized
    open fun addUnit(unit: T) {
        units.add(unit)
        if (!queued) submit() // Only submit if not queued
    }

    @Synchronized
    open fun submit() {
        if (!queued) tasks.add(this)
        queued = true
    }

    @Synchronized
    open fun done() { // Instead of relying on the state of `queued` to determine whether this finished or was cancelled, we should probably just add a param.
        if (queued) tasks.remove(this, true)
        queued = false
    }

    /** Runs all remaining units immediately */
    @Synchronized
    fun block() { // Dang. I almost wrote this whole class without any jank
        if (!queued) return // Do not attempt to process if the queue is empty, this will cause crashes on removeFirst and such.
        do {
            lastLongRunningTask = 0
            start = Time.millis()
        } while (!processStep())
        done()
    }

    companion object {
        private val tasks = Queue<BackgroundTask<*>>()
        private var lastLongRunningTask = 0L
        private var start: Long = 0

        fun update(budgetMillis: Int = Core.settings.getInt("maxsyncbackgroundtaskduration", 15)) {
            start = Time.millis()
            val end = start + budgetMillis
            var longTask: BackgroundTask<*>? = null
            with(tasks.iterator()) {
                while (hasNext() && end > Time.millis()) {
                    val task = next()
                    if (!task.shouldProcess()) continue

                    if (task.longTaskDuration > 0) { // Skip over long tasks as well as any other disabled ones
                        if (longTask == null) longTask = task
                        continue
                    }

                    synchronized(task) {
                        while (end > Time.millis()) {
                            if (task.processStep()) {
                                task.queued = false
                                task.done()
                                remove()
                                break
                            }
                        }
                    }
                }
            }

            if (longTask == null || !allowExpensiveStep(longTask.longTaskDuration)) return
            synchronized(longTask) {
                if (longTask.processStep()) {
                    longTask.queued = false
                    longTask.done()
                    tasks.remove(longTask, true)
                }
            }
            expensiveStep()
        }

        /** mark the current step as expensive */
        @JvmStatic fun expensiveStep() {
            lastLongRunningTask = Time.millis()
        }

        /** whether performing an expensive step now is ok */
        @JvmStatic fun allowExpensiveStep(millis: Int = 50) = start > lastLongRunningTask + millis && Time.timeSinceMillis(start) <= 1

        /** perform an expensive step if possible */
        @JvmStatic fun doExpensiveStep(millis: Int = 50, step: Runnable) {
            if (!allowExpensiveStep(millis))  return
            step.run()
            expensiveStep()
        }
    }
}