package mindustry.client.navigation;

import arc.math.geom.*;
import arc.struct.*;
import mindustry.client.navigation.waypoints.*;

/** A way of representing a path */
public abstract class Path {
    private final Seq<Runnable> listeners = new Seq<>();
    public boolean repeat = false;
    static final PositionWaypoint waypoint = new PositionWaypoint(); // Use this for paths that require one point, dont allocate more than we need to
    static final Vec2 v1 = new Vec2(), v2 = new Vec2(); // Temporary vectors
    static final WaypointPath<PositionWaypoint> waypoints = new WaypointPath<>(); // FINISHME: Use this in all paths

    public void init() {
        waypoints.set(new Seq<>()); // Clear waypoints
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
}
