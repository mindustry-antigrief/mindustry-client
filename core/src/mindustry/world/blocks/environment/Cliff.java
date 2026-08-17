package mindustry.world.blocks.environment;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.units.*;
import mindustry.graphics.*;
import mindustry.world.*;

public class Cliff extends Block{
    public float size = 11f;
    public @Load(value = "cliffmask#", length = 256) TextureRegion[] cliffs;

    public Cliff(String name){
        super(name);
        breakable = alwaysReplace = false;
        solid = true;
        cacheLayer = CacheLayer.walls;
        fillsTile = false;
        hasShadow = false;
        ignoreBuildDarkness = true; //Foo's addition
    }

    @Override
    public void flipRotation(BuildPlan req, boolean x){ //Foo's addition, we need to recompute the packed field.
        if(req.config != null && req.config instanceof Integer original){
            int out = 0;
            // 3  2  1
            //
            // 4  c  0
            //
            // 5  6  7
            if(x){
                out |= (original & (1 << 1)) <<  (3 - 1); out |= (original & (1 << 3)) >>  (3 - 1);
                out |= (original & (1 << 0)) <<  (4 - 0); out |= (original & (1 << 4)) >>  (4 - 0);
                out |= (original & (1 << 7)) >> -(5 - 7); out |= (original & (1 << 5)) << -(5 - 7);
                out |= original & (1 << 2 | 1 << 6);
            } else {
                out |= (original & (1 << 3)) <<  (5 - 3); out |= (original & (1 << 5)) >>  (5 - 3);
                out |= (original & (1 << 2)) <<  (6 - 2); out |= (original & (1 << 6)) >>  (6 - 2);
                out |= (original & (1 << 1)) <<  (7 - 1); out |= (original & (1 << 7)) >>  (7 - 1);
                out |= original & (1 << 0 | 1 << 4);
            }
            req.config = out;
        }
    }

    @Override
    public void rotatePlan(BuildPlan req, int direction){
        if(req.config != null && req.config instanceof Integer original){
            int out = 0;
            int offset = direction * 2;
            for(int i = 0; i < 8; i ++){
                if((original & (1 << i)) != 0) out |= (1 << Mathf.mod((i + offset), 8));
            }
            req.config = out;
        }
    }

    @Override
    public void drawBase(Tile tile){
        Draw.color(Tmp.c1.set(tile.floor().mapColor).mul(1.6f));
        Draw.rect(cliffs[tile.data & 0xff], tile.worldx(), tile.worldy());
        Draw.color();
    }

    @Override
    public int minimapColor(Tile tile){
        return Tmp.c1.set(tile.floor().mapColor).mul(1.2f).rgba();
    }
}
