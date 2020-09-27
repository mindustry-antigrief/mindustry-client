package mindustry.client.navigation;

public interface Path {

    void follow();

    float progress();

    default boolean done() {
        return progress() >= 0.99;
    }
}
