package mindustry.client.navigation;

public class Navigation {
    public static Path currentlyFollowing = null;
    public static boolean isPaused = false;

    public static void follow(Path path) {
        currentlyFollowing = path;
    }

    public static void update() {
        if (currentlyFollowing != null && !isPaused) {
            currentlyFollowing.follow();
            if (currentlyFollowing.isDone()) {
                currentlyFollowing.onFinish();
                currentlyFollowing = null;
            }
        }
    }

    public static boolean isFollowing() {
        return currentlyFollowing != null && !isPaused;
    }

    public static void draw() {
        if (currentlyFollowing != null) {
            currentlyFollowing.draw();
        }
    }
}
