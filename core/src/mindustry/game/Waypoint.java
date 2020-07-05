package mindustry.game;

import arc.util.ArcAnnotate.*;
import mindustry.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;


public class Waypoint{
    public float x, y;
    public long time;
    @Nullable
    public Tile pickup = null;
    @Nullable
    public Item item = null;
    public int amount = 0;
    @Nullable
    public BuildRequest buildRequest = null;

    public Waypoint(float x, float y){
        this.x = x;
        this.y = y;
    }

    public Waypoint(BuildRequest b){
        x = b.drawx();
        y = b.drawy();
        buildRequest = b;
    }

    @Override
    public String toString(){
        return String.format("mindustry.game.Waypoint(x=%f, \n y=%f, \n time=%d)\n", x, y, time);
    }

    public boolean goTo(){
        Player player = Vars.player;

        if(pickup != null && item != null){
            Call.requestItem(player, pickup, item, amount);
            return player.item().amount >= amount;
        }

        if(buildRequest != null){
            return player.buildQueue().indexOf(buildRequest, false) == -1;
        }

        return player.within(x, y, 32);
    }
}
