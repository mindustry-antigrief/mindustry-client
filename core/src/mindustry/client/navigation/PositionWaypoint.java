package mindustry.client.navigation;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import static mindustry.Vars.*;

public class PositionWaypoint implements Waypoint, Position {
    private final float drawX, drawY;
    public float tolerance = 16f;

    public PositionWaypoint(float drawX, float drawY) {
        this.drawX = drawX;
        this.drawY = drawY;
    }

    @Override
    public boolean isDone() {
        return player.within(this, tolerance);
    }

    @Override
    public void run() {
        float direction = player.angleTo(this);
        float x = Mathf.cosDeg(direction) * 2f;
        float y = Mathf.sinDeg(direction) * 2f;
        x = Mathf.clamp(x / 10, -1f, 1f);
        y = Mathf.clamp(y / 10, -1f, 1f);
        control.input.updateMovementCustom(player.unit(), x, y, direction);
    }

    @Override
    public float getX() {
        return drawX;
    }

    @Override
    public float getY() {
        return drawY;
    }

    @Override
    public void draw() {
        Draw.color(Color.green);
        Draw.alpha(0.3f);
        Fill.circle(getX(), getY(), tolerance);
        Draw.color();
    }
}
