package mindustry.client.navigation

import arc.*
import arc.util.*
import mindustry.game.EventType.*
import java.util.concurrent.*
import java.util.function.*

object clientThread {
    private var thread: ExecutorService? = null

    init {
        Events.on(WorldLoadEvent::class.java) { start() }
        Events.on(ResetEvent::class.java) { stop() }
        start()
    }

    /** Starts or restarts the thread. */
    private fun start() {
        stop()
        thread = Executors.newSingleThreadExecutor { r ->
            Thread(r, "Client Thread").apply { isDaemon = true }
        }
    }

    /** Stops the thread. */
    private fun stop() {
        thread?.shutdownNow()
        thread = null
    }

    @JvmStatic
    fun post(task: Runnable): CompletableFuture<Void> = if (thread != null) CompletableFuture.runAsync(task, thread) else CompletableFuture()

    @JvmStatic
    fun <T> submit(task: Supplier<T>): CompletableFuture<T> = if (thread != null) CompletableFuture.supplyAsync(task, thread) else CompletableFuture()

    @JvmStatic @Nullable
    fun get() = thread
}