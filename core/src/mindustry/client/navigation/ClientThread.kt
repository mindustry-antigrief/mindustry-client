package mindustry.client.navigation

import arc.*
import arc.util.*
import mindustry.game.EventType.*
import java.util.concurrent.*
import java.util.function.*

object clientThread {
    private var thread: ThreadPoolExecutor? = null

    @JvmStatic
    fun queue() = thread?.queue

    init {
        Events.on(WorldLoadEvent::class.java) { start() }
//        Events.on(ResetEvent::class.java) { stop() } FINISHME: Breaks navigation obstacles
        start()
    }

    /** Starts or restarts the thread. */
    private fun start() {
        stop()
        thread = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) { r ->
            Thread(r, "Client Thread").apply { isDaemon = true }
        }
    }

    /** Stops the thread. */
    private fun stop() {
        val start = Time.millis()
        Threads.await(thread ?: return) // FINISHME: We can't just shutdownNow as we need to ensure that turret ents are properly added and removed but we also don't want to wait on this for ages for things like chat schem sharing
        Log.debug("Waited @ms for client thread", Time.timeSinceMillis(start))
//        thread?.shutdownNow()
        thread = null
    }

    @JvmStatic
    fun post(task: Runnable): CompletableFuture<Void> = if (thread != null) CompletableFuture.runAsync(task, thread).exceptionHandler() else {
        Log.warn("Post to null clientThread")
        CompletableFuture()
    }

    @JvmStatic
    fun <T> submit(task: Supplier<T>): CompletableFuture<T> = if (thread != null) CompletableFuture.supplyAsync(task, thread).exceptionHandler() else {
        Log.warn("Submit to null clientThread")
        CompletableFuture()
    }

    @JvmStatic
    fun get() = thread

    private fun <T> CompletableFuture<T>.exceptionHandler() = apply { exceptionally {
        Core.app.post {
            if (it is CancellationException) return@post
            Log.err("Exception on client thread", it)
        }
        throw it
    } }
}