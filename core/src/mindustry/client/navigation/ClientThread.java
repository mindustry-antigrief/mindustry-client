package mindustry.client.navigation;

import arc.*;
import arc.util.*;
import arc.util.async.*;
import mindustry.game.*;

import java.util.Arrays;

import static mindustry.Vars.*;

public class ClientThread implements Runnable {
    private Thread thread = null;
    public TaskQueue taskQueue = new TaskQueue();
    private static final int updateFPS = 60;
    private static final int updateInterval = 1000 / updateFPS;

    public ClientThread() {
        Events.on(EventType.WorldLoadEvent.class, event -> start());
        Events.on(EventType.ResetEvent.class, event -> stop());
        start();
    }

    /** Starts or restarts the thread. */
    private void start() {
        if(thread != null && !thread.isInterrupted()) thread.interrupt();
        taskQueue.clear();
        thread = Threads.daemon(this);
    }

    /** Stops the thread. */
    private void stop() {
        if(thread != null) {
            thread.interrupt();
            thread = null;
        }
        if(state != null && state.isPlaying()) start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (state != null && state.isPlaying()) taskQueue.run();
                Thread.sleep(updateInterval);
            } catch (InterruptedException e) {
                Log.log(Log.LogLevel.debug, Arrays.toString(e.getStackTrace()));
                thread = null;
                stop();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
