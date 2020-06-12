package mindustry.game;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.util.*;
import arc.util.ArcAnnotate.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.player;


public class Waypoint{
    public float x, y;
    public long time;
    @Nullable
    public Tile pickup = null;
    @Nullable
    public Item item = null;
    public int amount = 0;

    public Waypoint(float x, float y, long millis){
        this.x = x;
        this.y = y;
        this.time = millis;
    }

    @Override
    public String toString(){
        return String.format("mindustry.game.Waypoint(x=%f, \n y=%f, \n time=%d)\n", x, y, time);
    }

    public boolean goTo(){
        Player player = Vars.player;

        if(pickup != null && item != null){
            Call.requestItem(player, pickup, item, amount);
        }

        float speed = player.isBoosting && !player.mech.flying ? player.mech.boostSpeed : player.mech.speed;

        if(player.mech.flying){
            //prevent strafing backwards, have a penalty for doing so
            float penalty = 0.2f; //when going 180 degrees backwards, reduce speed to 0.2x
            speed *= Mathf.lerp(1f, penalty, Angles.angleDist(player.rotation, player.velocity().angle()) / 180f);
        }

//        player.velocity().set((x - player.x) * 5, (y - player.y) * 5);
//        player.velocity().limit(speed);
//        player.rotation = player.velocity().angle();
//        player.updateVelocityStatus();
//        player.updateVelocity();
        Vec2 movement = new Vec2();
        movement.setZero();

        float xa = Mathf.clamp(x - player.x, -1F, 1F);
        float ya = Mathf.clamp(y - player.y, -1F, 1F);
        movement.y += ya * speed;
        movement.x += xa * speed;
        movement.limit(speed).scl(Time.delta());
        player.velocity().add(movement);
        player.updateVelocityStatus();
        player.rotation = player.velocity().angle();
        return player.within(x, y, 4);
    }
}
