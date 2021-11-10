package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.pooling.*;
import mindustry.client.navigation.waypoints.*;

import static mindustry.Vars.*;

/** A way of representing a path */
public abstract class Path {
    private final Seq<Runnable> listeners = new Seq<>();
    public boolean repeat = false;
    static final PositionWaypoint waypoint = new PositionWaypoint(); // Use this for paths that require one point, dont allocate more than we need to
    static final Vec2 v1 = new Vec2(), v2 = new Vec2(); // Temporary vectors
    static final WaypointPath<PositionWaypoint> waypoints = new WaypointPath<>(); // FINISHME: Use this in all paths
    private static final Seq<PositionWaypoint> filter = new Seq<>();

    public void init() {
        waypoints.clear();
        waypoints.setShow(true);
    }

    public abstract void setShow(boolean show);

    public abstract boolean getShow();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public abstract void follow();

    public abstract float progress();

    public boolean isDone() {
        boolean done = progress() >= 0.999f;
        if (done && repeat) {
            onFinish();
        }
        return done && !repeat;
    }

    public void onFinish() {
        listeners.forEach(Runnable::run);
        if (repeat) reset();
    }

    public abstract void reset();

    public void draw() {}

    public abstract Position next();

    public static WaypointPath<PositionWaypoint> goTo(Position dest, float dist) {
        if (Core.settings.getBool("pathnav") && !Core.settings.getBool("assumeunstrict")) {
            if (clientThread.taskQueue.size() == 0) {
                clientThread.taskQueue.post(() -> {
                    Pools.freeAll(filter);
                    filter.clear().addAll(Navigation.navigator.navigate(v1.set(player), v2.set(dest), Navigation.obstacles));
                    for (int i = filter.size - 1; i >= 0; i--) {
                        var wp = filter.get(i);
                        if (wp.dst(dest) < dist) {
                            filter.remove(wp);
                            Pools.free(wp);
                        }
                    }

                    if (filter.any()) {
                        while (filter.size > 1 && filter.min(wp -> wp.dst(player)) != filter.first()) Pools.free(filter.remove(0));
                        if (filter.size > 1 || filter.first().dst(player) < tilesize) Pools.free(filter.remove(0));
                        if (filter.size > 1 && player.unit().isFlying()) Pools.free(filter.remove(0)); // Ground units can't properly turn corners if we remove 2 waypoints.
                    }

                    waypoints.set(filter);
                });
            }
        } else waypoints.clear().add(waypoint.set(dest.getX(), dest.getY(), 0, dist)); // Not using navigation

        waypoints.follow();
        return waypoints;
    }
}
