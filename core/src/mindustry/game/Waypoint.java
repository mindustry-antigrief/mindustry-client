package mindustry.game;

import arc.math.*;
import arc.util.ArcAnnotate.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
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

        player.velocity().set(x - player.x, y - player.y);

        player.velocity().limit(speed / 4);
        player.updateVelocityStatus();
        player.updateVelocity();
        return player.within(x, y, 4);
    }
}
