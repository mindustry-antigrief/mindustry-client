package mindustry.world.blocks.power;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Nullable;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.client.ClientVars;
import mindustry.client.antigrief.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;
import mindustry.ui.fragments.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static mindustry.Vars.*;

public class PowerNode extends PowerBlock{
    protected static BuildPlan otherReq;
    protected static int returnInt = 0;
    protected final static ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    /** The maximum range of all power nodes on the map */
    public static float maxRange;

    public @Load("laser") TextureRegion laser;
    public @Load("laser-end") TextureRegion laserEnd;
    public float laserRange = 6;
    public int maxNodes = 3;
    public boolean autolink = true, drawRange = true;
    public float laserScale = 0.25f;
    public Color laserColor1 = Color.white;
    public Color laserColor2 = Pal.powerLight;

    public PowerNode(String name){
        super(name);
        configurable = true;
        consumesPower = false;
        outputsPower = false;
        canOverdrive = false;
        swapDiagonalPlacement = true;
        schematicPriority = -10;
        drawDisabled = false;
        envEnabled |= Env.space;
        destructible = true;

        //nodes do not even need to update
        update = false;

        config(Integer.class, (entity, value) -> {
            PowerModule power = entity.power;
            Building other = world.build(value);
            boolean contains = power.links.contains(value), valid = other != null && other.power != null;

            if(contains){
                //unlink
                power.links.removeValue(value);
                if(valid) other.power.links.removeValue(entity.pos());

                PowerGraph newgraph = new PowerGraph();

                //reflow from this point, covering all tiles on this side
                newgraph.reflow(entity);

                if(valid && other.power.graph != newgraph){
                    //create new graph for other end
                    PowerGraph og = new PowerGraph();
                    //reflow from other end
                    og.reflow(other);
                }
            }else if(linkValid(entity, other) && valid && power.links.size < maxNodes){

                power.links.addUnique(other.pos());

                if(other.team == entity.team){
                    other.power.links.addUnique(entity.pos());
                }

                power.graph.addGraph(other.power.graph);
            }
        });

        config(Point2[].class, (tile, value) -> {
            IntSeq old = new IntSeq(tile.power.links);

            //clear old
            for(int i = 0; i < old.size; i++){
                configurations.get(Integer.class).get(tile, old.get(i));
            }

            //set new
            for(Point2 p : value){
                configurations.get(Integer.class).get(tile, Point2.pack(p.x + tile.tileX(), p.y + tile.tileY()));
            }
        });
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("power", makePowerBalance());
        addBar("batteries", makeBatteryBalance());

        addBar("connections", entity -> new Bar(() ->
        Core.bundle.format("bar.powerlines", entity.power.links.size, maxNodes),
            () -> Pal.items,
            () -> (float)entity.power.links.size / (float)maxNodes
        ));
    }

    public static Func<Building, Bar> makePowerBalance(){
        return entity -> new Bar(() ->
        Core.bundle.format("bar.powerbalance",
            ((entity.power.graph.getPowerBalance() >= 0 ? "+" : "") + UI.formatAmount((long)(entity.power.graph.getPowerBalance() * 60)))),
            () -> Pal.powerBar,
            () -> Mathf.clamp(entity.power.graph.getLastPowerProduced() / entity.power.graph.getLastPowerNeeded())
        );
    }

    public static Func<Building, Bar> makeBatteryBalance(){
        return entity -> new Bar(() ->
        Core.bundle.format("bar.powerstored",
            (UI.formatAmount((long)entity.power.graph.getLastPowerStored())), UI.formatAmount((long)entity.power.graph.getLastCapacity())),
            () -> Pal.powerBar,
            () -> Mathf.clamp(entity.power.graph.getLastPowerStored() / entity.power.graph.getLastCapacity())
        );
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.powerRange, laserRange, StatUnit.blocks);
        stats.add(Stat.powerConnections, maxNodes, StatUnit.none);
    }

    @Override
    public void init(){
        super.init();

        clipSize = Math.max(clipSize, laserRange * tilesize);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Tile tile = world.tile(x, y);

        if(tile == null || !autolink) return;

        Lines.stroke(1f);
        Draw.color(Pal.placing);
        Drawf.circles(x * tilesize + offset, y * tilesize + offset, laserRange * tilesize);

        getPotentialLinks(tile, player.team(), other -> {
            Draw.color(laserColor1, Renderer.laserOpacity * 0.5f);
            drawLaser(x * tilesize + offset, y * tilesize + offset, other.x, other.y, size, other.block.size);

            Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 2f, Pal.place);
        });

        Draw.reset();
    }

    @Override
    public void changePlacementPath(Seq<Point2> points, int rotation){
        Placement.calculateNodes(points, this, rotation, (point, other) -> overlaps(world.tile(point.x, point.y), world.tile(other.x, other.y)));
    }

    protected void setupColor(float satisfaction){
        setupColor(satisfaction, false);
    }

    protected void setupColor(float satisfaction, boolean purple){
        if(purple){
            Draw.color(Pal.place, Renderer.laserOpacity + .2f);
        }else{
            Draw.color(laserColor1, laserColor2, (1f - satisfaction) * 0.86f + Mathf.absin(3f, 0.1f));
            Draw.alpha(Renderer.laserOpacity);
        }
    }

    public void drawLaser(float x1, float y1, float x2, float y2, int size1, int size2){
        float angle1 = Angles.angle(x1, y1, x2, y2),
            vx = Mathf.cosDeg(angle1), vy = Mathf.sinDeg(angle1),
            len1 = size1 * tilesize / 2f - 1.5f, len2 = size2 * tilesize / 2f - 1.5f;

        Drawf.laser(laser, laserEnd, x1 + vx*len1, y1 + vy*len1, x2 - vx*len2, y2 - vy*len2, laserScale);
    }

    protected boolean overlaps(float srcx, float srcy, Tile other, Block otherBlock, float range){
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range), Tmp.r1.setCentered(other.worldx() + otherBlock.offset, other.worldy() + otherBlock.offset,
            otherBlock.size * tilesize, otherBlock.size * tilesize));
    }

    protected boolean overlaps(float srcx, float srcy, Tile other, float range){
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range), other.getHitbox(Tmp.r1));
    }

    protected boolean overlaps(Building src, Building other, float range){
        return overlaps(src.x, src.y, other.tile(), range);
    }

    protected boolean overlaps(Tile src, Tile other, float range){
        return overlaps(src.drawx(), src.drawy(), other, range);
    }

    public boolean overlaps(@Nullable Tile src, @Nullable Tile other){
        if(src == null || other == null) return true;
        return Intersector.overlaps(Tmp.cr1.set(src.worldx() + offset, src.worldy() + offset, laserRange * tilesize), Tmp.r1.setSize(size * tilesize).setCenter(other.worldx() + offset, other.worldy() + offset));
    }

    public void getPotentialLinks(Tile tile, Team team, Cons<Building> others){
        getPotentialLinks(tile, team, others, true);
    }

    public void getPotentialLinks(Tile tile, Team team, Cons<Building> others, boolean skipExisting){
        if(!autolink) return;

        Boolf<Building> valid = other -> other != null && other.tile() != tile && other.power != null &&
            (other.block.outputsPower || other.block.consumesPower || other.block instanceof PowerNode) &&
            overlaps(tile.x * tilesize + offset, tile.y * tilesize + offset, other.tile(), laserRange * tilesize) && other.team == team &&
            !(skipExisting && graphs.contains(other.power.graph)) &&
            !PowerNode.insulated(tile, other.tile) &&
            !(other instanceof PowerNodeBuild obuild && obuild.power.links.size >= ((PowerNode)obuild.block).maxNodes) &&
            !Structs.contains(Edges.getEdges(size), p -> { //do not link to adjacent buildings
                var t = world.tile(tile.x + p.x, tile.y + p.y);
                return t != null && t.build == other;
            });

        tempBuilds.clear();
        graphs.clear();

        //add conducting graphs to prevent double link
        for(var p : Edges.getEdges(size)){
            Tile other = tile.nearby(p);
            if(other != null && other.team() == team && other.build != null && other.build.power != null){
                graphs.add(other.build.power.graph);
            }
        }

        if(tile.build != null && tile.build.power != null){
            graphs.add(tile.build.power.graph);
        }

        var worldRange = laserRange * tilesize;
        var tree = team.data().buildingTree;
        if(tree != null){
            tree.intersect(tile.worldx() - worldRange, tile.worldy() - worldRange, worldRange * 2, worldRange * 2, build -> {
                if(valid.get(build) && !tempBuilds.contains(build)){
                    tempBuilds.add(build);
                }
            });
        }

        tempBuilds.sort((a, b) -> {
            int type = -Boolean.compare(a.block instanceof PowerNode, b.block instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(a.dst2(tile), b.dst2(tile));
        });

        returnInt = 0;

        tempBuilds.each(valid, t -> {
            if(returnInt ++ < maxNodes){
                graphs.add(t.power.graph);
                others.get(t);
            }
        });
    }

    //TODO code duplication w/ method above?
    /** Iterates through linked nodes of a block at a tile. All returned buildings are power nodes. */
    public static void getNodeLinks(Tile tile, Block block, Team team, Cons<Building> others){
        Boolf<Building> valid = other -> other != null && other.tile() != tile && other.block instanceof PowerNode node &&
        node.autolink &&
        other.power.links.size < node.maxNodes &&
        node.overlaps(other.x, other.y, tile, block, node.laserRange * tilesize) && other.team == team
        && !graphs.contains(other.power.graph) &&
        !PowerNode.insulated(tile, other.tile) &&
        !Structs.contains(Edges.getEdges(block.size), p -> { //do not link to adjacent buildings
            var t = world.tile(tile.x + p.x, tile.y + p.y);
            return t != null && t.build == other;
        });

        tempBuilds.clear();
        graphs.clear();

        //add conducting graphs to prevent double link
        for(var p : Edges.getEdges(block.size)){
            Tile other = tile.nearby(p);
            if(other != null && other.team() == team && other.build != null && other.build.power != null
                && !(block.consumesPower && other.block().consumesPower && !block.outputsPower && !other.block().outputsPower)){
                graphs.add(other.build.power.graph);
            }
        }

        if(tile.build != null && tile.build.power != null){
            graphs.add(tile.build.power.graph);
        }

        var rangeWorld = maxRange * tilesize;
        var tree = team.data().buildingTree;
        if(tree != null){
            tree.intersect(tile.worldx() - rangeWorld, tile.worldy() - rangeWorld, rangeWorld * 2, rangeWorld * 2, build -> {
                if(valid.get(build) && !tempBuilds.contains(build)){
                    tempBuilds.add(build);
                }
            });
        }

        tempBuilds.sort((a, b) -> {
            int type = -Boolean.compare(a.block instanceof PowerNode, b.block instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(a.dst2(tile), b.dst2(tile));
        });

        tempBuilds.each(valid, t -> {
            graphs.add(t.power.graph);
            others.get(t);
        });
    }

    @Override
    public void drawPlanConfigTop(BuildPlan plan, Eachable<BuildPlan> list){
        if(plan.config instanceof Point2[] ps){
            setupColor(1f);
            for(Point2 point : ps){
                int px = plan.x + point.x, py = plan.y + point.y;
                otherReq = null;
                list.each(other -> {
                    if(other.block != null
                        && (px >= other.x - ((other.block.size-1)/2) && py >= other.y - ((other.block.size-1)/2) && px <= other.x + other.block.size/2 && py <= other.y + other.block.size/2)
                        && other != plan && other.block.hasPower){
                        otherReq = other;
                    }
                });

                if(otherReq == null || otherReq.block == null) continue;

                drawLaser(plan.drawx(), plan.drawy(), otherReq.drawx(), otherReq.drawy(), size, otherReq.block.size);
            }
            Draw.color();
        }
    }

    public boolean linkValid(Building tile, Building link){
        return linkValid(tile, link, true);
    }

    public boolean linkValid(Building tile, Building link, boolean checkMaxNodes){
        if(tile == link || link == null || !link.block.hasPower || tile.team != link.team) return false;

        if(overlaps(tile, link, laserRange * tilesize) || (link.block instanceof PowerNode node && overlaps(link, tile, node.laserRange * tilesize))){
            if(checkMaxNodes && link.block instanceof PowerNode node){
                return link.power.links.size < node.maxNodes || link.power.links.contains(tile.pos());
            }
            return true;
        }
        return false;
    }

    public static boolean insulated(Tile tile, Tile other){
        return insulated(tile.x, tile.y, other.x, other.y);
    }

    public static boolean insulated(Building tile, Building other){
        return insulated(tile.tileX(), tile.tileY(), other.tileX(), other.tileY());
    }

    public static boolean insulated(int x, int y, int x2, int y2){
        return World.raycast(x, y, x2, y2, (wx, wy) -> {
            Building tile = world.build(wx, wy);
            return tile != null && tile.isInsulated();
        });
    }

    public static class PowerNodeConfigReq extends ConfigRequest {

        private final boolean connect;
        private final int value;

        public PowerNodeConfigReq(@NotNull PowerNodeBuild build, int value, boolean connect) {
            super(build, null, false);
            this.value = value;
            this.connect = connect;
        }

        @Override
        public void run() {
            Tile tile = Vars.world.tile(x, y);
            if(tile == null || !(tile.build instanceof PowerNodeBuild pb)) return;

            boolean isConnected = pb.power.links.contains(value);
            if(isConnected == connect){
                pb.removeFromQueue(value, connect);
                return; //already connected if want to connect, and vice versa
            }

            Call.tileConfig(Vars.player, tile.build, value);
            pb.removeFromQueue(value, connect);
        }
    }

    public class PowerNodeBuild extends Building{

        /** This is used for power split notifications. */
        public @Nullable ChatFragment.ChatMessage message;
        public int disconnections = 0;
        public @Nullable IntSet correctLinks, queuedConfigs; // supposed links when placed in a schematic
        public int queuedConnectionSize = 0; // true number of connections if queued connections are successful
        public long timeout = Time.millis();

        public static int fixNode = Core.settings.getInt("nodeconf");

        @Override
        public void created(){ // Called when one is placed/loaded in the world
            if(autolink && laserRange > maxRange) maxRange = laserRange;

            super.created();
        }


        @Override
        public void placed(){
            if(net.client() || power.links.size > 0) return;

            getPotentialLinks(tile, team, other -> {
                if(!power.links.contains(other.pos())){
                    configureAny(other.pos());
                }
            });

            super.placed();
        }

//        @Override
//        public void playerPlaced(Object config) { FINISHME: Make this work, maybe an IntObjectMap with Entry<pos, Seq<linkPos>>
//            super.playerPlaced(config);
//
//            if(net.client() && config instanceof Point2[] t){ // Fix incorrect power node linking in schems
//                var current = new Seq<Point2>();
//                getPotentialLinks(tile, team, other -> { // The server hasn't sent us the links yet, emulate how it would look if it had
//                    if(!power.links.contains(other.pos())) current.add(new Point2(other.tile.x, other.tile.y).sub(tile.x, tile.y));
//                });
//
//                Log.info(Arrays.toString(t) + " | " + current);
//                current.each(l -> !Structs.contains(t, l), l -> { // Remove extra links.
//                    ClientVars.configs.add(new ConfigRequest(this, l.add(tile.x, tile.y).pack()));
//                    Log.info(tileX() + ", " + tileY() + " | " + l.x + tile.x + ", " + l.y + tile.y);
//                });
//            }
//        }
        private void removeFromQueue(int value, boolean connect){
            if(queuedConfigs == null) return;
            queuedConfigs.remove(value);
            queuedConnectionSize -= connect ? 1 : -1;
            if (queuedConfigs.isEmpty() && correctLinks == null) queuedConfigs = null;
        }

        private void addToQueue(int value, boolean connect){
            if(queuedConfigs == null || queuedConfigs.contains(value)) return;
            queuedConfigs.add(value);
            ClientVars.configs.add(new PowerNodeConfigReq(this, value, connect));
            queuedConnectionSize += connect ? 1 : -1;
            timeout = Time.millis();
        }

        public void findDisconnect(){
            power.links.each(v -> {
                if(!correctLinks.contains(v) && !queuedConfigs.contains(v)){
                    addToQueue(v, false);
                }
            });
        }

        public void findConnect(){
            boolean[] pending = {false};
            correctLinks.each(v -> {
               if(power.links.contains(v) || queuedConfigs.contains(v)) return;
               if(!linkValid(this, world.build(v), false)) {
                   pending[0] = true;
                   return;
               };
               world.tile(v).getLinkedTiles(tile -> {
                   int pos = tile.pos();
                   if(power.links.contains(pos)){
                       correctLinks.remove(v);
                       correctLinks.add(pos);
                   }
               });
               if(!correctLinks.contains(v)) return;
               pending[0] = true;
               if(power.links.size + queuedConnectionSize < maxNodes){
                   addToQueue(v, true);
               }
            });
            if(!pending[0] || Time.timeSinceMillis(timeout) > 300000L /*300 seconds*/ || correctLinks.size > maxNodes || power.links.size + queuedConnectionSize > maxNodes){
                correctLinks = null;
            }
        }

        public void fixNode(Object config){
            if(!net.client()) configure(config);
            else if(config instanceof Point2[] t){ // Fix incorrect power node linking in schems
                var current = new Seq<Point2>();
                getPotentialLinks(tile, team, other -> { // The server hasn't sent us the links yet, emulate how it would look if it had
                    if(!power.links.contains(other.pos())) current.add(new Point2(other.tile.x, other.tile.y).sub(tile.x, tile.y));
                });

                queuedConfigs = new IntSet();
                boolean[] hasQueued = {false};
                current.each(l -> !Structs.contains(t, l), l -> { // Remove extra links.
                    hasQueued[0] = true;
                    int v = l.add(tile.x, tile.y).pack();
                    Core.app.post(() -> addToQueue(v, false));
                });

                if(t.length > 0){
                    correctLinks = new IntSet();
                    for(Point2 p: t){
                        correctLinks.add(p.add(tile.x, tile.y).pack());
                    }
                } else if(!hasQueued[0]) queuedConfigs = null;
            }
        }

        @Override
        public void dropped(){
            power.links.clear();
            queuedConfigs = null;
            correctLinks = null;
            updatePowerGraph();
        }

        @Override
        public void updateTile(){
            if(correctLinks != null){
                findDisconnect();
                findConnect(); // shame (lots of overhead)
            }
            power.graph.update();
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            correctLinks = null; // do not try to auto config further
            queuedConfigs = null;
            if(linkValid(this, other)){
                configure(other.pos());
                return false;
            }

            if(this == other){ // Double tap
                if(other.power.links.size == 0 || Core.input.shift()){ // Find and add possible links (shift to toggle modes)
                    int[] total = {0};
                    Point2[] links = new Point2[maxNodes];
                    getPotentialLinks(tile, team, link -> {
                        if(!insulated(this, link) && total[0] < maxNodes){
                            links[total[0]++] = new Point2(link.tileX() - tile.x, link.tileY() - tile.y);
                        }
                    });
                    configure(Arrays.copyOfRange(links, 0, total[0]));
                }else{ // Clear all links
                    configure(new Point2[0]);
                }
                // deselect();      // Dont deselect
                return false;
            }

            return true;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();

            if(!drawRange) return;

            Lines.stroke(1f);

            Draw.color(Pal.accent);
            Drawf.circles(x, y, laserRange * tilesize);
            Draw.reset();
        }

        @Override
        public void drawConfigure(){

            Drawf.circles(x, y, tile.block().size * tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));

            if(drawRange){
                Drawf.circles(x, y, laserRange * tilesize);

                for(int x = (int)(tile.x - laserRange - 2); x <= tile.x + laserRange + 2; x++){
                    for(int y = (int)(tile.y - laserRange - 2); y <= tile.y + laserRange + 2; y++){
                        Building link = world.build(x, y);

                        if(link != this && linkValid(this, link, false)){
                            boolean linked = linked(link);

                            if(linked){
                                Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                            }
                        }
                    }
                }

                Draw.reset();
            }else{
                power.links.each(i -> {
                    var link = world.build(i);
                    if(link != null && linkValid(this, link, false)){
                        Drawf.square(link.x, link.y, link.block.size * tilesize / 2f + 1f, Pal.place);
                    }
                });
            }
        }

        @Override
        public void draw(){
            super.draw();

            if(Mathf.zero(Renderer.laserOpacity) || isPayload()) return;

            Draw.z(Layer.power);
            setupColor(power.graph.getSatisfaction(), power.graph == PowerInfo.hovered);

            for(int i = 0; i < power.links.size; i++){
                Building link = world.build(power.links.get(i));

                if(!linkValid(this, link)) continue;

                if(link.block instanceof PowerNode && link.id >= id) continue;

                drawLaser(x, y, link.x, link.y, size, link.block.size);
            }

            Draw.reset();
        }

        protected boolean linked(Building other){
            return power.links.contains(other.pos());
        }

        @Override
        public Point2[] config(){
            Point2[] out = new Point2[power.links.size];
            for(int i = 0; i < out.length; i++){
                out[i] = Point2.unpack(power.links.get(i)).sub(tile.x, tile.y);
            }
            return out;
        }
    }

    public enum PowerNodeFixSettings {
        disabled("Disabled", false, false),
        schematicOnlyExcludeSource("Only in schematics but not power sources", false, false),
        schematicOnly("Only in schematics", false, true),
        enabledExcludeSource("Everywhere but not power sources", true, false),
        enabled("Everywhere", true, true);

        public String desc; // for use in settings
        public boolean normal, source;

        PowerNodeFixSettings(String desc, boolean normal, boolean source){
            this.desc = desc;
            this.normal = normal;
            this.source = source;
        }

        public static PowerNodeFixSettings get(int i){
            return values()[i];
        }

        public static PowerNodeFixSettings get(boolean normal, boolean source){
            return get((normal ? nonSchematicReq : enableReq) + (source ? 1 : 0));
        }

        public static final int enableReq = schematicOnlyExcludeSource.ordinal();
        public static final int nonSchematicReq = enabledExcludeSource.ordinal();

        @Override
        public String toString(){
            return desc.toLowerCase(Locale.ROOT) + " (on non-loaded schematics: " + normal + ", on sources: " + source + ")";
        }
    }
}
