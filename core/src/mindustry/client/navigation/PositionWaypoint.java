package mindustry.client.navigation;

import arc.math.geom.*;

public class PositionWaypoint implements Waypoint, Position {
    private final float drawX, drawY;

    public PositionWaypoint(float drawX, float drawY) {
        this.drawX = drawX;
        this.drawY = drawY;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public void run() {

    }

    @Override
    public float getX() {
        return drawX;
    }

    @Override
    public float getY() {
        return drawY;
    }
}
