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

import javax.swing.*;

import static mindustry.Vars.player;


public class Waypoint{
    public float x, y;
    public long time;
    @Nullable
    public Tile pickup = null;
    @Nullable
    public Item item = null;
    public int amount = 0;
//    public boolean requested = false;

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
//            requested = true;
        }
//
//        float speed = player.isBoosting && !player.mech.flying ? player.mech.boostSpeed : player.mech.speed;
//
//        if(player.mech.flying){
//            //prevent strafing backwards, have a penalty for doing so
//            float penalty = 0.2f; //when going 180 degrees backwards, reduce speed to 0.2x
//            speed *= Mathf.lerp(1f, penalty, Angles.angleDist(player.rotation, player.velocity().angle()) / 180f);
//        }

//        player.velocity().set((x - player.x) * 5, (y - player.y) * 5);
//        player.velocity().limit(speed);
//        player.rotation = player.velocity().angle();
//        player.updateVelocityStatus();
//        player.updateVelocity();
//        Vec2 movement = new Vec2();
//        movement.setZero();

//        float xa = (x - player.x) / Time.delta();
//        float ya = (y - player.y) / Time.delta();
////        System.out.println(xa);
//        movement = movement.set(xa, ya).limit(speed);
////        System.out.println(Time.delta());
////        movement.limit(speed);
//        player.velocity().add(movement.scl(Time.delta()));
////        player.updateVelocityStatus();
//        player.rotation = player.velocity().angle();
////        System.out.println("movement.x = " + movement.x);

//        movement.set((x - player.x) / Time.delta(), (y - player.y) / Time.delta()).limit(speed);
//        player.velocity().set(movement.scl(Time.delta()));
//        player.rotation = player.velocity().angle();
//        player.updateVelocityStatus();
//        System.out.println(player.velocity());

        return player.within(x, y, 16);
    }
}
