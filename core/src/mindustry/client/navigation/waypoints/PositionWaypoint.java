package mindustry.client.navigation.waypoints;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.client.navigation.*;

import static mindustry.Vars.*;

public class PositionWaypoint extends Waypoint implements Position {
    private final float drawX, drawY;
    public float tolerance = 16f;
    public float distance = 0f;
    Vec2 vec = new Vec2();

    public PositionWaypoint(float drawX, float drawY) {
        this.drawX = drawX;
        this.drawY = drawY;
    }

    public PositionWaypoint(float drawX, float drawY, float tolerance) {
        this(drawX, drawY);
        this.tolerance = tolerance;
    }

    public PositionWaypoint(float drawX, float drawY, float tolerance, float distance) {
        this(drawX, drawY);
        this.tolerance = tolerance;
        this.distance = distance;
    }

    @Override
    public boolean isDone() {
        return player.within(this, tolerance);
    }

    protected void moveTo(Position target, float circleLength, float smooth){
        if(target == null) return;

        vec.set(target).sub(player.unit());

        if (Core.settings.getBool("assumeunstrict")) {
            float length = player.unit().dst(target) - circleLength;
            vec.setLength(length);
            if (length < 0) vec.setZero();
            player.trns(vec);
            player.unit().trns(vec);
            player.snapInterpolation();
        } else {
            float length = circleLength <= 0.001f ? 1f : Mathf.clamp((player.unit().dst(target) - circleLength) / smooth, -1f, 1f);
            vec.setLength(player.unit().realSpeed() * length);
            if (length < -0.5f) vec.rotate(180f);
            else if (length < 0) vec.setZero();
            player.unit().moveAt(vec);
        }
        if (!player.unit().isShooting || !player.unit().type.rotateShooting) player.unit().lookAt(vec.angle()); // Look towards waypoint when possible
    }
    @Override
    public void run() {
        moveTo(this, distance, Navigation.currentlyFollowing instanceof WaypointPath ? 0 : 20);
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