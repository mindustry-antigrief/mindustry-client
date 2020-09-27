package mindustry.client.navigation;

import arc.struct.*;


public interface Path {
    Seq<Runnable> listeners = new Seq<>();
    boolean show = false;

    default void addListener(Runnable listener) {
        listeners.add(listener);
    }

    void follow();

    float progress();

    default boolean isDone() {
        return progress() >= 0.99;
    }

    default void onFinish() {
        listeners.forEach(Runnable::run);
    }

    default void draw() {}
}
