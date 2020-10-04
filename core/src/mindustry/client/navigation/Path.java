package mindustry.client.navigation;

import arc.struct.*;

/**
 * A way of representing a path, currently only implemented by {@link WaypointPath}
 */
public abstract class Path {
    private final Seq<Runnable> listeners = new Seq<>();

    abstract void setShow(boolean show);

    abstract boolean isShown();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    abstract void follow();

    abstract float progress();

    public boolean isDone() {
        return progress() >= 0.99;
    }

    public void onFinish() {
        listeners.forEach(Runnable::run);
    }

    public void draw() {}
}
