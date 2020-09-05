package mindustry.client.pathfinding;

import mindustry.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.type.*;
import static mindustry.Vars.world;


public class Waypoint{
    public float x, y;
    public long time;
    public Integer pickup = null;
    public Integer dump = null;
    public Item item = null;
    public int amount = 0;
    public BuildRequest buildRequest = null;
    public float distance = 32f;

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
        return String.format("mindustry.client.pathfinding.Waypoint(x=%f, \n y=%f, \n time=%d)\n", x, y, time);
    }

    public boolean goTo(){
        Player player = Vars.player;

        if(pickup != null && item != null){
            Call.requestItem(player, world.tile(pickup), item, amount);
            return player.item().amount >= amount;
        }
        if(dump != null){
            Call.transferInventory(player, world.tile(dump));
//            return player.item().amount >= amount;
        }

        if(buildRequest != null){
            return player.buildQueue().indexOf(buildRequest, false) == -1;
        }

        return player.within(x, y, distance);
    }
}
