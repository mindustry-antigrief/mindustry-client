package mindustry.client.navigation;

import arc.math.geom.Position;
import arc.struct.*;

import static mindustry.Vars.player;

/** A way of representing a path */
public abstract class Path {
    private final Seq<Runnable> listeners = new Seq<>();
    public boolean repeat = false;

    public void init() {
        addListener(() -> player.shooting(false));
    }

    public abstract void setShow(boolean show);

    abstract boolean isShown();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    abstract void follow();

    abstract float progress();

    public boolean isDone() {
        boolean done = progress() >= 0.99;
        if (done && repeat) {
            onFinish();
        }
        return done && !repeat;
    }

    public void onFinish() {
        listeners.forEach(Runnable::run);
        if (repeat) {
            reset();
        }
    }

    public abstract void reset();

    public void draw() {}

    abstract Position next();
}
