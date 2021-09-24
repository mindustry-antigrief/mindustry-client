package mindustry.client.navigation;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.graphics.*;

/** A {@link Path} composed of {@link Waypoint} instances. */
public class WaypointPath<T extends Waypoint> extends Path {
    private Seq<T> waypoints;
    private @Nullable Seq<T> initial;
    private int initialSize;
    private boolean show;

    public WaypointPath(Seq<T> waypoints) {
        this.waypoints = waypoints;
        this.initial = waypoints.copy();
        this.initialSize = waypoints.size;
    }

    @SafeVarargs
    public WaypointPath(T... waypoints) {
        this.waypoints = Seq.with(waypoints);
        this.initial = Seq.with(waypoints);
        this.initialSize = waypoints.length;
    }

    @SafeVarargs
    public static <T extends Waypoint> WaypointPath<T> with(T... waypoints) {
        return new WaypointPath<>(waypoints);
    }

    public WaypointPath<T> set(Seq<T> waypoints) {
        this.waypoints = waypoints;
        if (repeat) this.initial = waypoints.copy(); // Don't bother if we aren't repeating
        this.initialSize = waypoints.size;
        return this;
    }

    @Override
    public void setShow(boolean show) {
        this.show = show;
    }

    @Override
    public boolean getShow() {
        return show;
    }

    @Override
    public void follow() {
        if (waypoints == null || waypoints.isEmpty()) return;

        while (waypoints.size > 1 && Core.settings.getBool("assumeunstrict")) waypoints.remove(0); // Only the last waypoint is needed when we are just teleporting there anyways.
        Waypoint waypoint = waypoints.first();
        waypoint.run();
        if (waypoint.isDone()) {
            waypoint.onFinish();
            waypoints.remove(0);
        }
    }

    @Override
    public float progress() {
        if (waypoints == null || initialSize == 0) return 1f;

        return waypoints.size / (float)initialSize;
    }

    @Override
    public boolean isDone() {
        if (waypoints == null) return true;

        if (waypoints.isEmpty() && repeat) onFinish();
        return waypoints.isEmpty();
    }

    @Override
    public void reset() {
        waypoints.clear();
        waypoints.addAll(initial);
    }

    @Override
    public void draw() {
        if (show) {
            Waypoint lastWaypoint = null;
            for(Waypoint waypoint : waypoints){
                if(waypoint instanceof Position){
                    if(lastWaypoint != null){
                        Draw.z(Layer.space);
                        Draw.color(Color.blue, 0.4f);
                        Lines.stroke(3f);
                        Lines.line(((Position)lastWaypoint).getX(), ((Position)lastWaypoint).getY(), ((Position)waypoint).getX(), ((Position)waypoint).getY());
                    }
                    lastWaypoint = waypoint;
                }
                waypoint.draw();
                Draw.color();
            }
            Draw.color();
        }
    }

    @Override
    public Position next() {
        return waypoints.first() instanceof Position ? (Position)waypoints.first() : null;
    }
}
