package mindustry.entities.units;

import arc.func.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.math.geom.QuadTree.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.power.*;

import static mindustry.Vars.*;
import static mindustry.client.ClientVars.cameraBounds;

/** Class for storing build plans. Can be either a place or remove plan. */
public class BuildPlan implements Position, Pool.Poolable, QuadTreeObject{
    /** Position and rotation of this plan. */
    public int x, y, rotation;
    /** Block being placed. If null, this is a breaking plan.*/
    public @Nullable Block block;
    /** Whether this is a break plan.*/
    public boolean breaking;
    /** Config int. Not used unless hasConfig is true. */
    public Object config;
    /** Whether the config is to be sent to the server */
    public transient boolean configLocal;
    /** Original position, only used in schematics.*/
    public int originalX, originalY, originalWidth, originalHeight;

    /** Last progress.*/
    public float progress;
    /** Whether construction has started for this plan, and other special variables.*/
    public boolean initialized, worldContext = true, stuck, cachedValid;

    /** Visual scale. Used only for rendering. */
    public float animScale = 0f;

    /** Double freeing plans is a bad idea. */
    public boolean freed;

    /** Whether to always prioritise the plan, regardless of ability to be built.*/
    public boolean priority = false;

    @Override
    public void reset() {
        config = null;
        configLocal = false;
        progress = 0;
        initialized = false;
        stuck = false;
        freed = true;
        priority = false;
    }

    /** This creates a build plan. */
    public BuildPlan(int x, int y, int rotation, Block block){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
        this.breaking = false;
    }

    /** This creates a build plan with a config. */
    public BuildPlan(int x, int y, int rotation, Block block, Object config){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
        this.breaking = false;
        this.config = config;
    }

    /** This creates a remove plan. */
    public BuildPlan(int x, int y){
        this.x = x;
        this.y = y;
        this.rotation = -1;
        this.block = world.tile(x, y).block();
        this.breaking = true;
    }

    public BuildPlan(){

    }

    public boolean placeable(Team team){
        return Build.validPlace(block, team, x, y, rotation);
    }

    public boolean isRotation(Team team){
        if(breaking) return false;
        Tile tile = tile();
        return tile != null && tile.team() == team && tile.block() == block && tile.build != null && tile.build.rotation != rotation;
    }

    public boolean samePos(BuildPlan other){
        return x == other.x && y == other.y;
    }

    /** Transforms the internal position of this config using the specified function, and return the result. */
    public static Object pointConfig(Block block, Object config, Cons<Point2> cons){
        if(config instanceof Point2 point){
            config = point.cpy();
            cons.get((Point2)config);
        }else if(config instanceof Point2[] points){
            Point2[] result = new Point2[points.length];
            int i = 0;
            for(Point2 p : points){
                result[i] = p.cpy();
                cons.get(result[i++]);
            }
            config = result;
        }else if(block != null){
            config = block.pointConfig(config, cons);
        }
        return config;
    }

    /** Transforms the internal position of this config using the specified function. */
    public void pointConfig(Cons<Point2> cons){
        this.config = pointConfig(block, this.config, cons);
    }

    public BuildPlan copy(){
        BuildPlan copy = new BuildPlan();
        copy.x = x;
        copy.y = y;
        copy.rotation = rotation;
        copy.block = block;
        copy.breaking = breaking;
        copy.config = config;
        copy.originalX = originalX;
        copy.originalY = originalY;
        copy.progress = progress;
        copy.initialized = initialized;
        copy.animScale = animScale;
        copy.configLocal = configLocal;
        return copy;
    }

    public BuildPlan original(int x, int y, int originalWidth, int originalHeight){
        originalX = x;
        originalY = y;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        return this;
    }

    public BuildPlan set(int x, int y, int rotation, Block block){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
        this.breaking = false;
        freed = false;
        return this;
    }

    public BuildPlan set(int x, int y, int rotation, Block block, Object config){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
        this.breaking = false;
        this.config = config;
        freed = false;
        return this;
    }

    public BuildPlan set(int x, int y){
        this.x = x;
        this.y = y;
        this.rotation = -1;
        this.block = world.tile(x, y).block();
        this.breaking = true;
        freed = false;
        return this;
    }

    public float drawx(){
        return x*tilesize + (block == null ? 0 : block.offset);
    }

    public float drawy(){
        return y*tilesize + (block == null ? 0 : block.offset);
    }

    public @Nullable Tile tile(){
        return world.tile(x, y);
    }

    public @Nullable Building build(){
        return world.build(x, y);
    }

    public boolean isDone(){ // FINISHME: Surely most of this is redundant for no reason...
        Tile tile = world.tile(x, y);
        if(breaking){
            return tile.block() == null || tile.block() == Blocks.air || tile.block() == tile.floor();  // covering all the bases
        }else{
            return tile.block() == block && (tile.build == null || tile.build.rotation == rotation);
        }
    }

    public boolean isVisible(){
        final Rect r1 = Tmp.r1;
        return !worldContext || cameraBounds.overlaps(block.bounds(x, y, r1)) ||
                (block instanceof ItemBridge b && Tmp.r2.set(cameraBounds).grow(2 * b.range * tilesizeF).overlaps(r1)) ||
                (block instanceof PowerNode p && Tmp.r2.set(cameraBounds).grow(2 * tilesize * p.laserRange).overlaps(r1));
    }

    public static void getVisiblePlans(Eachable<BuildPlan> plans, Seq<BuildPlan> output){
        plans.each(plan -> {
            if(plan.isVisible()) output.add(plan);
        });
    }

    @Override
    public void hitbox(Rect out){
        if(block != null){
            out.setCentered(x * tilesize + block.offset, y * tilesize + block.offset, block.size * tilesize);
        }else{
            out.setCentered(x * tilesize, y * tilesize, tilesize);
        }
    }
    
    public Rect bounds(Rect rect) {
        return bounds(rect, false);
    }
    
    public Rect bounds(Rect rect, boolean allowBreak){
        if(breaking && !allowBreak){
            return rect.set(-100f, -100f, 0f, 0f);
        }else{
            return block.bounds(x, y, rect);
        }
    }

    @Override
    public float getX(){
        return drawx();
    }

    @Override
    public float getY(){
        return drawy();
    }

    @Override
    public String toString(){
        return "BuildPlan{" +
        "x=" + x +
        ", y=" + y +
        ", rotation=" + rotation +
        ", block=" + block +
        ", breaking=" + breaking +
        ", progress=" + progress +
        ", initialized=" + initialized +
        ", config=" + config +
        '}';
    }
}
