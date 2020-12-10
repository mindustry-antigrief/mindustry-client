package mindustry.client.navigation;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.struct.*;
import mindustry.client.navigation.waypoints.Waypoint;
import mindustry.graphics.Layer;

/**
 * A {@link Path} composed of {@link Waypoint} instances.
 */
public class WaypointPath extends Path {
    private final Seq<Waypoint> waypoints;
    private final Seq<Waypoint> finished;
    private boolean show;

    public WaypointPath(Seq<Waypoint> waypoints) {
        this.waypoints = waypoints;
        finished = new Seq<>();
    }

    @Override
    public void setShow(boolean show) {
        this.show = show;
    }

    @Override
    public boolean isShown() {
        return show;
    }

    @Override
    public void follow() {
        if (waypoints == null) {
            return;
        }
        if (waypoints.isEmpty()){
            return;
        }
        Waypoint waypoint = waypoints.first();
        waypoint.run();
        if (waypoint.isDone()) {
            waypoint.onFinish();
            finished.add(waypoints.remove(0));
        }
    }

    @Override
    public float progress() {
        //TODO make this work better
        if (waypoints == null) {
            return 1f;
        }
        if (waypoints.size + finished.size == 0) {
            return 1f;
        }
        return waypoints.size / (float)(waypoints.size + finished.size);
    }

    @Override
    public boolean isDone() {
        if (waypoints == null) {
            return true;
        }
        return waypoints.isEmpty();
    }

    @Override
    public void draw() {
        if (show) {
            Waypoint lastWaypoint = null;
            for(Waypoint waypoint : waypoints){
                if(waypoint instanceof Position){
                    if(lastWaypoint != null){
                        Draw.color(Color.blue);
                        Draw.alpha(0.4f);
                        Draw.z(Layer.space);
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
        return waypoints.peek() instanceof Position? (Position)waypoints.peek() : null;
    }
}
