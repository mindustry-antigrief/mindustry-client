package mindustry.game;

import arc.struct.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

public class TransferEndpoint{
    //Possible values: "player" "tile" "block_type"
    // What?  I'm too lazy to make an enum class okay?
    private String type = "";

    // if type == "player" then this is not null
    private Player player = null;

    // if type == "tile" then this is not null
    private Integer x = null, y = null;

    // if type == "block_type" then these are not null
    private Array<Integer> blockPositions = new Array<>();
    private Block block = null;

    public TransferEndpoint(Player player){
        type = "player";
        this.player = player;
    }

    public TransferEndpoint(Tile tile){
        type = "tile";
        x = (int)tile.x;
        y = (int)tile.y;
    }

    public TransferEndpoint(Block block){
        type = "block_type";
        this.block = block;
        rebuild();
    }

    public void rebuild(){
        if(type.equals("block_type")){
            blockPositions.clear();
            for(Tile[] tileRow : Vars.world.getTiles()){
                for(Tile tile : tileRow){
                    if(tile.block() != null && tile.getTeam() == Vars.player.getTeam()){
                        if(tile.block().id == block.id){
                            blockPositions.add(tile.pos());
                        }
                    }
                }
            }
        }
    }

    public void transferToPlayer(Item item){
        switch(type){
            case "player":
                break;

            case "tile":
                Tile tile = Vars.world.tile(x, y);
                if(tile != null && tile.block() != null && tile.entity.items.get(item) > 0){
                    Call.transferItemTo(item, 1, x, y, tile);
                }

            case "block_type":
                for(int pos : blockPositions){
                    Tile tile2 = Vars.world.tile(pos);
                    if(tile2 != null && tile2.block() != null && tile2.entity.items.get(item) > 0){
                        Call.transferItemTo(item, 1, x, y, tile2);
                    }
                }
        }
    }


    public void transferFromPlayer(Item item){
        switch(type){
            case "player":
                break;

            case "tile":
                Tile tile = Vars.world.tile(x, y);
                if(tile != null && tile.block() != null && tile.block().acceptStack(item, 1, tile, Vars.player) > 0){
                    Call.transferInventory(Vars.player, tile);
                }

            case "block_type":
                for(int pos : blockPositions){
                    Tile tile2 = Vars.world.tile(pos);
                    if(tile2 != null && tile2.block() != null && tile2.block().acceptStack(item, 1, tile2, Vars.player) > 0){
                        Call.transferInventory(Vars.player, tile2);
                    }
                }
        }
    }

    public String toString(){
        switch(type){
            case "player":
                return "Mech items";

            case "tile":
                return String.format("%d, %d", x, y);

            default:  //must be block_type
                return block.name;
        }
    }
}
