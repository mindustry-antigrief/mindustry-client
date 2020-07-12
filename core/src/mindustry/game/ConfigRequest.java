package mindustry.game;

import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class ConfigRequest{
    public int x;
    public int y;
    public int value;
    public int initialValue;

    public ConfigRequest(Tile tile, int value){
        this.x = tile.x;
        this.y = tile.y;
        this.value = value;
        this.initialValue = tile.getConfig();
    }

    public void runRequest(){
        if(value != -1){
            world.tile(x, y).configure(value);
        }
    }

    public void undoRequest(){
        world.tile(x, y).configure(initialValue);
    }

    public ConfigRequest getUndoRequest(){
        return new ConfigRequest(world.tile(x, y), initialValue);
    }
}
