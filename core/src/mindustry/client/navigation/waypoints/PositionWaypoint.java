package mindustry.client.navigation.waypoints;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import mindustry.client.navigation.*;

import static mindustry.Vars.*;

public class PositionWaypoint extends Waypoint implements Position {
    private float drawX, drawY;
    /** Waypoint is done when the player is this close to the waypoint */
    public float tolerance = 16f;
    /** Stay this distance away from the waypoint */
    public float distance = 0f;
    public boolean stopOnFinish = false;
    public Position mustPassThrough = null; // the tile the unit should "over-walk" away from
    Vec2 vec = new Vec2();

    public PositionWaypoint() {
    }

    public PositionWaypoint(float drawX, float drawY) {
        this.drawX = drawX;
        this.drawY = drawY;
    }

    /** @param tolerance Waypoint is done when the player is this close to the waypoint */
    public PositionWaypoint(float drawX, float drawY, float tolerance) {
        this(drawX, drawY);
        this.tolerance = tolerance;
    }

    /** @param tolerance Waypoint is done when the player is this close to the waypoint
        @param distance Stay this distance away from the waypoint */
    public PositionWaypoint(float drawX, float drawY, float tolerance, float distance) {
        this(drawX, drawY);
        this.tolerance = tolerance;
        this.distance = distance;
    }

    public PositionWaypoint set(float drawX, float drawY){
        return set(drawX, drawY, 16, 0);
    }

    /** @param tolerance Waypoint is done when the player is this close to the waypoint
        @param distance Stay this distance away from the waypoint */
    public PositionWaypoint set(float drawX, float drawY, float tolerance, float distance){
        this.drawX = drawX;
        this.drawY = drawY;
        this.tolerance = tolerance;
        this.distance = distance;
        this.stopOnFinish = false;
        this.mustPassThrough = null;
        return this;
    }

    @Override
    public boolean isDone() {
        return player.within(this, tolerance) &&
                (mustPassThrough == null || !player.within(mustPassThrough, tilesize * 0.99f)); // 0.99 - a number i plucked out of nowhere.
        // most units should be able to surpass the distance requirement to change direction and not hit the corner block
    }

    @Override
    public void onFinish(){
        if (stopOnFinish) {
            float prev = player.unit().rotation();
            if (dst(player) < 2f) {
                player.unit().set(this);
            }
            player.unit().vel.setZero();
            player.unit().lookAt(prev);
            player.snapInterpolation();
            stopOnFinish = false;
        }
        mustPassThrough = null;
    }

    protected void moveTo(Position target, float circleLength, float smooth){
        if(target == null || (target.getX() == -1f && target.getY() == -1f)) return;

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
            vec.setLength(player.unit().speed() * length);
            if (length < -0.5f) vec.rotate(180f);
            else if (length < 0) vec.setZero();
            player.unit().moveAt(vec);
        }
        if (!player.unit().isShooting || !player.unit().type.faceTarget) player.unit().lookAt(vec.angle()); // Look towards waypoint when possible
    }
    @Override
    public PositionWaypoint run() {
        return run(Navigation.currentlyFollowing instanceof WaypointPath ? 0 : 20);
    }

    public PositionWaypoint run(int smooth) {
        moveTo(this, distance, smooth);
        return this;
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
        Fill.circle(getX(), getY(), stopOnFinish? 16f : tolerance);
        Draw.color();
    }
}