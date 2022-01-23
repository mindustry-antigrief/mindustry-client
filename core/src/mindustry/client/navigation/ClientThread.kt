package mindustry.client.navigation

import arc.*
import arc.util.async.*
import mindustry.*
import mindustry.game.EventType.*
import java.util.concurrent.*

object clientThread : Runnable {
    private var thread: Thread? = null

    @JvmField
    val taskQueue = LinkedBlockingQueue<Runnable>()

    init {
        Events.on(WorldLoadEvent::class.java) { start() }
        Events.on(ResetEvent::class.java) { stop() }
        start()
    }

    /** Starts or restarts the thread. */
    private fun start() {
        stop()
        taskQueue.clear()
        thread = Threads.daemon("Client Thread", this)
    }

    /** Stops the thread. */
    private fun stop() {
        if (thread != null) {
            thread!!.interrupt()
            thread = null
        }
    }

    override fun run() {
        while (true) {
            try {
                val task = taskQueue.take()
                if (Vars.state?.isPlaying == true) task.run() // Do nothing if not in game
            } catch (e: Exception) {
                if (e !is InterruptedException) e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun post(task: Runnable) = FutureTask(task, Unit).also(taskQueue::add)

    @JvmStatic
    fun <T> submit(task: Callable<T>) = FutureTask(task).also(taskQueue::add)
}