package mindustry.client.navigation;

abstract class Waypoint {

    abstract boolean isDone();

    abstract void run();

    abstract void draw();

    public void onFinish() {}
}
