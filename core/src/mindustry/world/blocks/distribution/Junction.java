package mindustry.world.blocks.distribution;

import arc.*;
import arc.graphics.g2d.*;
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

    static Vec2 displacement = new Vec2(), direction = new Vec2(tilesize, 0), baseOffset = setBaseOffset(Core.settings == null ? 0 : Core.settings.getInt("junctionview", 0));
    public static boolean drawItems = false;

    public Junction(String name){
        super(name);
        update = true;
        solid = true;
        group = BlockGroup.transportation;
        unloadable = false;
        noUpdateDisabled = true;
    }

    public static Vec2 setBaseOffset(int mode){ // -1 left, 0 disable, 1 right
        drawItems = mode != 0;
        float y = -tilesize / 3.1f * mode;
        return baseOffset = new Vec2(-tilesize/2f, y);
        // for display on left, (0, tilesize / 3.1f) (given rot = 0);
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
            float firstProgress = 0;
            for(int i = 0; i < 4; i++){ // Code from zxtej
                for(int j = 0; j < buffer.indexes[i]; j++){ // from DirectionalItemBuffer.poll()
                    long l = buffer.buffers[i][j];
                    Item item = content.item(BufferItem.item(l));
                    float time = Time.time - BufferItem.time(l); // to exit, Time.time > time + speed. Then currFrame (ie speed) = Time.time - time
                    if(time < 0) time = Float.MAX_VALUE; // if joining a game later than when item was placed
                    float progress = time / speed * timeScale;
                    if (j == 0) firstProgress = progress;

                    progress = Math.min(progress+.15f, firstProgress >= 1 ? 1f - (float)j / capacity : 1); // (cap - j) * 1/cap
                    displacement.set(direction).scl(-0.5f/capacity + progress).add(baseOffset); // -0.5/capacity: 1/capacity times half that distance
                    Draw.rect(item.fullIcon, tile.x * tilesize + displacement.x, tile.y * tilesize + displacement.y, itemSize / 4f, itemSize / 4f);
                }
                direction.rotate90(1);
                baseOffset.rotate90(1);
            }
        }
    }
}
