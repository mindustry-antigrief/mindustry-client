package mindustry.client.navigation;

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
    public boolean done() {
        return waypoints.isEmpty();
    }
}
