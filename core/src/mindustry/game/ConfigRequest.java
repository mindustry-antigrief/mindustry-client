package mindustry.game;

import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.player;

public class ConfigRequest{
    public Tile tile;
    public Player player;
    public int value;

    public ConfigRequest(Tile tile, Player player, int value){
        this.tile = tile;
        this.player = player;
        this.value = value;
    }

    public void runRequest(){
        tile.configure(value);
    }
}
