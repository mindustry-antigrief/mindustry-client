package mindustry.world.blocks.distribution;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Junction extends Block{
    public float speed = 26; //frames taken to go through this junction
    public int capacity = 6;

    // FINISHME: Rework to work with junctions with size >1
    static final Vec2 direction = new Vec2(tilesize, 0), baseOffset = new Vec2();
    public static boolean drawItems = false;

    public Junction(String name){
        super(name);
        update = true;
        solid = false;
        underBullets = true;
        group = BlockGroup.transportation;
        unloadable = false;
        noUpdateDisabled = true;
    }

    public static void setBaseOffset(int mode){ // -1 left, 0 disable, 1 right
        drawItems = mode != 0;
        float y = -tilesize / 3.1f * mode;
        baseOffset.set(-tilesize/2f, y);
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    public class JunctionBuild extends Building{
        public DirectionalItemBuffer buffer = new DirectionalItemBuffer(capacity);

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            return 0;
        }

        @Override
        public void updateTile(){
            for(int i = 0; i < 4; i++){
                if(buffer.indexes[i] > 0){
                    if(buffer.indexes[i] > capacity) buffer.indexes[i] = capacity;
                    long l = buffer.buffers[i][0];
                    float time = BufferItem.time(l);

                    if(Time.time >= time + speed / timeScale || Time.time < time){
                        Item item = content.item(BufferItem.item(l));
                        Building dest = nearby(i);

                        //skip blocks that don't want the item, keep waiting until they do
                        if(item == null || dest == null || !dest.acceptItem(this, item) || dest.team != team){
                            continue;
                        }

                        dest.handleItem(this, item);
                        System.arraycopy(buffer.buffers[i], 1, buffer.buffers[i], 0, buffer.indexes[i] - 1);
                        buffer.indexes[i] --;
                    }
                }
            }
        }

        @Override
        public void handleItem(Building source, Item item){
            int relative = source.relativeTo(tile);
            buffer.accept(relative, item);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            int relative = source.relativeTo(tile);

            if(relative == -1 || !buffer.accepts(relative)) return false;
            Building to = nearby(relative);
            return to != null && to.team == team;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            buffer.write(write);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            buffer.read(read);
        }

        @Override
        public void draw(){
            super.draw();

            if(!drawItems) return;
            Draw.z(Layer.blockOver);
            var realSpeed = speed * timeScale;
            var iSize = (tilesizeF * size) / capacity;
            var spacing = 1f / capacity;
            for(int i = 0; i < 4; i++){ // Code from zxtej
                var last = 1f - spacing * .5f;
                for(int j = 0; j < buffer.indexes[i]; j++){ // highest to lowest progress
                    var l = buffer.buffers[i][j];
                    var item = content.item(BufferItem.item(l));
                    var progress = Mathf.clamp((Time.time - BufferItem.time(l)) / realSpeed, spacing * .5f, last);
                    last -= spacing;

                    Draw.rect(item.fullIcon,
                        x + direction.x * progress + baseOffset.x,
                        y + direction.y * progress + baseOffset.y,
                        iSize, iSize
                    );
                }
                direction.rotate90(1);
                baseOffset.rotate90(1);
            }
        }
    }
}
