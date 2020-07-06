package mindustry.world.blocks.distribution;

import arc.*;
import arc.graphics.g2d.*;
import mindustry.entities.type.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.OverflowGate.*;
import mindustry.world.meta.*;

import java.io.*;

import static mindustry.Vars.world;

public class Chain extends Block{
    public float speed = 1f;
    public Block[] blocks;

    public Chain(String name, Block[] blocks){
        super(name);
        this.blocks = blocks;
        hasItems = true;
        solid = true;
        update = true;
        group = BlockGroup.transportation;
        unloadable = false;
        entityType = ChainEntity::new;
    }

    @Override
    public void load(){
        region = icon(Cicon.full);

        cacheRegions = new TextureRegion[cacheRegionStrings.size];
        for(int i = 0; i < cacheRegions.length; i++){
            cacheRegions[i] = Core.atlas.find(cacheRegionStrings.get(i));
        }

        if(cracks == null || (cracks[0][0].getTexture() != null && cracks[0][0].getTexture().isDisposed())){
            cracks = new TextureRegion[maxCrackSize][crackRegions];
            for(int size = 1; size <= maxCrackSize; size++){
                for(int i = 0; i < crackRegions; i++){
                    cracks[size - 1][i] = Core.atlas.find("cracks-" + size + "-" + i);
                }
            }
        }
    }

    @Override
    public void draw(Tile tile){
        Draw.rect(icon(Cicon.full), tile.drawx(), tile.drawy(), rotate ? tile.rotation() * 90 : 0);
    }

    @Override
    public TextureRegion icon(Cicon icon){
        String name = this.name;
        if(cicons[icon.ordinal()] == null){
            cicons[icon.ordinal()] = Core.atlas.find(getContentType().name() + "-" + name + "-" + icon.name(),
            Core.atlas.find(getContentType().name() + "-" + name + "-full",
            Core.atlas.find(getContentType().name() + "-" + name,
            Core.atlas.find(name,
            Core.atlas.find(name + "1", Core.atlas.find("chain-generic"))))));
        }
        return cicons[icon.ordinal()];
    }

    @Override
    public boolean equals(Object other){
        return other instanceof Chain && name.equals(((Chain)other).name);
    }

    public class ChainEntity extends TileEntity{
        @Override
        public byte version(){
            return 3;
        }
    }
}
