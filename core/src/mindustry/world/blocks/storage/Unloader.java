package mindustry.world.blocks.storage;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import arc.util.pooling.*;
import arc.util.pooling.Pool.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;

public class Unloader extends Block{
    public @Load(value = "@-center", fallback = "unloader-center") TextureRegion centerRegion;

    public float speed = 1f;
    public static boolean drawUnloaderItems = Core.settings != null && Core.settings.getBool("unloaderview");
    public static boolean customNullLoader = Core.settings != null && Core.settings.getBool("customnullunloader");

    /** Cached result of content.items() */
    static Item[] allItems;

    public Unloader(String name){
        super(name);
        update = true;
        solid = true;
        health = 70;
        hasItems = true;
        configurable = true;
        saveConfig = true;
        itemCapacity = 0;
        noUpdateDisabled = true;
        clearOnDoubleTap = true;
        unloadable = false;

        config(Item.class, (UnloaderBuild tile, Item item) -> tile.sortItem = item);
        configClear((UnloaderBuild tile) -> tile.sortItem = null);
    }

    @Override
    public void init(){
        super.init();

        allItems = content.items().toArray(Item.class);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.speed, 60f / speed, StatUnit.itemsSecond);
    }

    @Override
    public void drawPlanConfig(BuildPlan plan, Eachable<BuildPlan> list){
        drawPlanConfigCenter(plan, plan.config, "unloader-center");
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("items");
    }

    public static class ContainerStat implements Poolable{
        Building building;
        float loadFactor;
        boolean canLoad;
        boolean canUnload;
        /** Cached !(building instanceof StorageBuild) */
        boolean notStorage;
        int lastUsed;

        @Override
        public void reset(){
            building = null;
        }
    }

    public class UnloaderBuild extends Building{
        public float unloadTimer = 0f;
        public int rotations = 0;
        public Item sortItem = null;
        public ContainerStat dumpingFrom, dumpingTo;
        public final Seq<ContainerStat> possibleBlocks = new Seq<>(ContainerStat.class);
        private Item lastItem = null;

        protected final Comparator<ContainerStat> comparator = (x, y) -> {
            //sort so it gives priority for blocks that can only either receive or give (not both), and then by load, and then by last use
            //highest = unload from, lowest = unload to
            int unloadPriority = Boolean.compare(x.canUnload && !x.canLoad, y.canUnload && !y.canLoad); //priority to receive if it cannot give
            if(unloadPriority != 0) return unloadPriority;
            int loadPriority = Boolean.compare(x.canUnload || !x.canLoad, y.canUnload || !y.canLoad); //priority to give if it cannot receive
            if(loadPriority != 0) return loadPriority;
            int loadFactor = Float.compare(x.loadFactor, y.loadFactor);
            if(loadFactor != 0) return loadFactor;
            return Integer.compare(y.lastUsed, x.lastUsed); //inverted
        };

        private boolean isPossibleItem(Item item){
            boolean hasProvider = false,
            hasReceiver = false,
            isDistinct = false;

            var pbi = possibleBlocks.items;
            for(int i = 0, l = possibleBlocks.size; i < l; i++){
                var pb = pbi[i];
                var other = pb.building;

                //set the stats of buildings in possibleBlocks while we are at it
                pb.canLoad = pb.notStorage && other.acceptItem(this, item);
                pb.canUnload = other.canUnload() && other.items != null && other.items.has(item);

                //thats also handling framerate issues and slow conveyor belts, to avoid skipping items if nulloader
                isDistinct |= (hasProvider && pb.canLoad) || (hasReceiver && pb.canUnload);
                hasProvider |= pb.canUnload;
                hasReceiver |= pb.canLoad;
            }
            return isDistinct;
        }

        @Override
        public void onProximityUpdate(){
            //filter all blocks in the proximity that will never be able to trade items

            super.onProximityUpdate();
            Pools.freeAll(possibleBlocks, true);
            possibleBlocks.clear();

            for(int i = 0; i < proximity.size; i++){
                var other = proximity.get(i);
                if(!other.interactable(team)) continue; //avoid blocks of the wrong team

                //partial check
                boolean canLoad = !(other.block instanceof StorageBlock);
                boolean canUnload = other.canUnload() && other.items != null;

                if(canLoad || canUnload){ //avoid blocks that can neither give nor receive items
                    var pb = Pools.obtain(ContainerStat.class, ContainerStat::new);
                    pb.building = other;
                    pb.notStorage = canLoad;
                    //TODO store the partial canLoad/canUnload?
                    possibleBlocks.add(pb);
                }
            }
        }

        @Override
        public void updateTile(){
            if(((unloadTimer += delta()) < speed) || (possibleBlocks.size < 2)) return;
            Item item = null;
            boolean any = false;

            if(sortItem != null){
                if(isPossibleItem(sortItem)) item = sortItem;
            }else{
                //selects the next item for nulloaders
                //inspired of nextIndex() but for all "proximity" (possibleBlocks) at once, and also way more powerful
                for(int i = 0, l = allItems.length; i < l; i++){
                    int id = (rotations + i + 1) % l;
                    var possibleItem = allItems[id];

                    if(isPossibleItem(possibleItem)){
                        item = possibleItem;
                        break;
                    }
                }
            }

            dumpingTo = null;
            dumpingFrom = null;

            if(item != null){
                rotations = item.id; //next rotation for nulloaders //TODO maybe if(sortItem == null)
                var pbi = possibleBlocks.items;
                int pbs = possibleBlocks.size;

                for(int i = 0; i < pbs; i++){
                    var pb = pbi[i];
                    var other = pb.building;
                    int maxAccepted = other.getMaximumAccepted(item);
                    pb.loadFactor = maxAccepted == 0 || other.items == null ? 0 : other.items.get(item) / (float)maxAccepted;
                    pb.lastUsed = (pb.lastUsed + 1) % Integer.MAX_VALUE; //increment the priority if not used
                }

                possibleBlocks.sort(comparator);

                //choose the building to accept the item
                for(int i = 0; i < pbs; i++){
                    if(pbi[i].canLoad){
                        dumpingTo = pbi[i];
                        break;
                    }
                }

                //choose the building to take the item from
                for(int i = pbs - 1; i >= 0; i--){
                    if(pbi[i].canUnload){
                        dumpingFrom = pbi[i];
                        break;
                    }
                }

                //trade the items
                if(dumpingFrom != null && dumpingTo != null && (dumpingFrom.loadFactor != dumpingTo.loadFactor || !dumpingFrom.canLoad)){
                    dumpingTo.building.handleItem(this, item);
                    dumpingFrom.building.removeStack(item, 1);
                    dumpingTo.lastUsed = 0;
                    dumpingFrom.lastUsed = 0;
                    lastItem = item;
                    any = true;
                }
            }

            if(any){
                unloadTimer %= speed;
            }else{
                unloadTimer = Math.min(unloadTimer, speed);
            }
        }

        private static final float halfTilesizeF = tilesizeF / 2f, nodeSize = halfTilesizeF, halfNodeSize = nodeSize / 2f;
        @Override
        public void draw(){
            super.draw();

            Draw.color(sortItem == null ? customNullLoader ? Pal.lightishGray : Color.clear : sortItem.color);
            Draw.rect(centerRegion, x, y);
            if(drawUnloaderItems && possibleBlocks.size >= 2 && lastItem != null && dumpingFrom != null && dumpingTo != null && enabled){
                Draw.color(lastItem.color, 0.67f);
                Draw.rect("unloader-center", x, y);
                Draw.alpha(1f);
                var v1 = Tmp.v1;
                float thick = Lines.getStroke();

                Lines.stroke(tilesizeF / 8f);
                Lines.beginLine();
                getDirection(dumpingFrom.building);
                Lines.linePoint(v1.scl(halfTilesizeF - halfNodeSize / 2f).add(this));
                Lines.linePoint(this);
                getDirection(dumpingTo.building);
                Lines.linePoint(v1.scl(halfTilesizeF - halfNodeSize).add(this));
                Lines.endLine();
                Lines.stroke(thick);

                Tex.logicNode.draw(v1.x - halfNodeSize, v1.y - halfNodeSize, halfNodeSize, halfNodeSize, nodeSize, nodeSize, 1f, 1f, v1.sub(this).angle());
            }
            Draw.color();
        }

        private void getDirection(Building other){
            float dx = other.x - x, dy = other.y - y;
            if(Math.abs(dy) > Math.abs(dx)){
                int sign = Mathf.sign(dy);
                Tmp.v1.set(0, sign); // direction of the rect
                //Tmp.r1.set(x - lineWidth2, y - sign * lineWidth2, lineWidth, sign * (tilesizeF / 2f + lineWidth2));
            } else {
                int sign = Mathf.sign(dx);
                Tmp.v1.set(sign, 0);
                //Tmp.r1.set(x - sign * lineWidth2, y - lineWidth2, sign * (tilesizeF / 2f + lineWidth2), lineWidth);
            }
        }
        @Override
        public void buildConfiguration(Table table){
            ItemSelection.buildTable(Unloader.this, table, content.items(), () -> sortItem, this::configure, selectionRows, selectionColumns);
        }

        @Override
        public void pickedUp() {
            super.pickedUp();

            lastItem = null;
        }

        @Override
        public Item config(){
            return sortItem;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(sortItem == null ? -1 : sortItem.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            int id = revision == 1 ? read.s() : read.b();
            sortItem = id == -1 ? null : content.item(id);
        }
    }
}
