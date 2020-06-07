package mindustry.world.blocks.distribution;

import mindustry.world.*;

public class Chain extends Block{

    public Chain(String name){
        super(name);
    }

    @Override
    public void playerPlaced(Tile tile){
        tile.setBlock(new OverflowGate("overflow-gate"));
    }
}
