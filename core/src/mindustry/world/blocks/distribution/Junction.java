package mindustry.world.blocks.distribution;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.Bits;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import mindustry.world.modules.ItemModule;

import static mindustry.Vars.*;

public class Junction extends Block{
    public float speed = 26; //frames taken to go through this junction
    public int capacity = 6;

    // FINISHME: Rework to work with junctions with size >1
    static final Vec2 direction = new Vec2(tilesize, 0), baseOffset = new Vec2();
    public static boolean drawItems = false;

    public static boolean flowRateByDirection = Core.settings != null && Core.settings.getBool("junctionflowratedirection", false);
    public final static TextureRegionDrawable[] directionIcons = {Icon.rightSmall, Icon.upSmall, Icon.leftSmall, Icon.downSmall};

    public Junction(String name){
        super(name);
        update = true;
        solid = false;
        underBullets = true;
        group = BlockGroup.transportation;
        unloadable = false;
        floating = true;
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
        public ItemModule items2 = new ItemModule();

        @Override
        public void update(){
            super.update();
            if (ui == null) return;
            boolean shouldFlow = ui.hudfrag.blockfrag.nextFlowBuild == this;
            if (shouldFlow) items2.updateFlow();
            else items2.stopFlow();
        }

        @Override
        public void display(Table table) {
            boolean tempDisplayFlow = block.displayFlow;
            block.displayFlow = false;
            super.display(table);
            block.displayFlow = tempDisplayFlow;
            if(net.active() && lastAccessed != null){
                table.getChildren().remove(table.getRows() - 1);
            }
            if (displayFlow) {
                String ps = " " + StatUnit.perSecond.localized();
                if (items2 != null) {
                    table.row();
                    table.left();
                    table.table((l)->{
                        Bits current = new Bits();
                        Runnable rebuild = ()->{
                            l.clearChildren();
                            l.left();
                            int i_limit = flowRateByDirection ? 4 : content.items().size;
                            for (int i=0; i < i_limit; i++) {
                                Item item = content.items().get(i);
                                if (items2.hasFlowItem(item)) {
                                    if(flowRateByDirection) l.image(directionIcons[i]).padRight(3.0F);
                                    else l.image(item.uiIcon).padRight(3.0F);
                                    l.label(()->items2.getFlowRate(item) < 0 ? "..." : Strings.fixed(items2.getFlowRate(item), 1) + ps).color(Color.lightGray);
                                    l.row();
                                }
                            }
                        };
                        rebuild.run();
                        l.update(()->{
                            for (Item item : content.items()) {
                                if (items2.hasFlowItem(item) && !current.get(item.id)) {
                                    current.set(item.id);
                                    rebuild.run();
                                }
                            }
                        });
                    }).left();
                }
            }
            if (net.active() && lastAccessed != null) {
                table.getChildren().get(table.getRows() - 2).remove();
                table.row();
                table.add(Core.bundle.format("lastaccessed", lastAccessed)).growX().wrap().left();
            }
            table.marginBottom(-5);
        }

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
                        items2.remove(flowRateByDirection ? content.item(i) : item, 1);
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
            items2.add(flowRateByDirection ? content.item(relative) : item, 1);
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
            // correct the time value since they all somehow get mapped to a high number
            float now = Time.time;
            for(int i = 0; i < 4; i++){
                for(int j = Math.min(buffer.indexes[i], capacity) - 1; j >= 0 && j < buffer.buffers[i].length; j--){
                    var l = buffer.buffers[i][j];
                    if (now <= BufferItem.time(l)){
                        buffer.buffers[i][j] = BufferItem.get(BufferItem.item(l),now - speed * timeScale);
                    }
                }
            }
        }

        @Override
        public void draw(){
            super.draw();
            if(!drawItems) return;
            Draw.z(Layer.blockOver);
            float now = Time.time;
            for(int i = 0; i < 4; i++){ // Code from zxtej
                for(int j = buffer.indexes[i]; j > 0;){
                    var l = buffer.buffers[i][--j];
                    var progress = Mathf.clamp((now - BufferItem.time(l)) / speed * timeScale, 0, (capacity - j) / (float)capacity);

                    Draw.rect(content.item(BufferItem.item(l)).fullIcon,
                        x + baseOffset.x + direction.x * progress,
                        y + baseOffset.y + direction.y * progress,
                        itemSize / 4f, itemSize / 4f);
                }
                direction.rotate90(1);
                baseOffset.rotate90(1);
            }
        }
    }
}
