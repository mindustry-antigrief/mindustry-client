package mindustry.client.navigation;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.struct.*;

public class WaypointPath implements Path {
    private Seq<Waypoint> waypoints;
    private Seq<Waypoint> finished;

    public WaypointPath(Seq<Waypoint> waypoints) {
        this.waypoints = waypoints;
        finished = new Seq<>();
    }

    @Override
    public void follow() {
        Waypoint waypoint = waypoints.peek();
        waypoint.run();
        if (waypoint.isDone()) {
            finished.add(waypoints.pop());
        }
    }

    @Override
    public float progress() {
        //TODO make this work better
        return waypoints.size / (float)(waypoints.size + finished.size);
    }

    @Override
    public boolean isDone() {
        return waypoints.isEmpty();
    }

    @Override
    public void draw() {
        if (show){
            Waypoint lastWaypoint = null;
            for(Waypoint waypoint : waypoints){
                if(waypoint instanceof Position){
                    if(lastWaypoint != null){
                        Draw.color(Color.blue);
                        Draw.alpha(0.4f);
                        Lines.stroke(3f);
                        Lines.line(((Position)lastWaypoint).getX(), ((Position)lastWaypoint).getY(), ((Position)waypoint).getX(), ((Position)waypoint).getY());
                    }
                    lastWaypoint = waypoint;
                }
                waypoint.draw();
            }
        }
    }
}
