package mindustry.client.navigation;

/**
 * A way of representing a waypoint.  You're probably looking for {@link PositionWaypoint}
 */
public abstract class Waypoint {

    abstract boolean isDone();

    abstract void run();

    abstract void draw();

    public void onFinish() {}
}
