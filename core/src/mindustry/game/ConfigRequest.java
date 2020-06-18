package mindustry.game;

import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.world.*;

import static mindustry.Vars.player;

public class ConfigRequest{
    Tile tile;
    Player player;
    int value;

    public ConfigRequest(Tile tile, Player player, int value){
        this.tile = tile;
        this.player = player;
        this.value = value;
    }

    public void runRequest(){
        Call.onTileConfig(player, tile, value);
    }
}
