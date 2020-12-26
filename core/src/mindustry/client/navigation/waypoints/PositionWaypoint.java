package mindustry.client.navigation.waypoints;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.Timer;

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

        float length = circleLength <= 0.001f ? 1f : Mathf.clamp((player.unit().dst(target) - circleLength) / smooth, -1f, 1f);

        vec.setLength(player.unit().realSpeed() * length);
        if(length < -0.5f){
            vec.rotate(180f);
        }else if(length < 0){
            vec.setZero();
        }

        player.unit().moveAt(vec);
        if (!player.unit().isShooting || !player.unit().type.rotateShooting) player.unit().lookAt(vec.angle()); // Look towards waypoint when possible
    }
    @Override
    public void run() {
        if (Core.settings.getBool("assumeunstrict")) player.unit().moveAt(new Vec2().set(this).sub(player.unit()), player.dst(this));
        else moveTo(this, distance, 8f);
//        if (player.dst(this) > tolerance /* + player.unit().realSpeed() / player.unit().drag * Time.delta */) {
//            //control.input.updateMovementCustom(player.unit(), x, y, direction);
//            moveTo(this, 30, 100);
//        }
//        else if (player.dst(this) < tolerance) {
//            player.unit().vel.scl(1.0f - player.unit().drag * Time.delta);
//        }
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
