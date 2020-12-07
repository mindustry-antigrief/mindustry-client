package mindustry.client.navigation;

import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Position;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Nullable;
import mindustry.ai.formations.Formation;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;

import static mindustry.Vars.*;

public class BuildPath extends Path {
    Building core = player.core();
    private boolean show;
    Interval timer = new Interval();
    Queue<BuildPlan> broken = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>();

    @Override
    void setShow(boolean show) { this.show = show; }

    @Override
    boolean isShown() { return show; }

    @Override @SuppressWarnings("unchecked rawtypes") // Java sucks so warnings must be suppressed
    void follow() {
        if (timer.get(15)) {
            if(!broken.isEmpty()){broken.forEach(player.unit().plans::remove); broken.clear();} // Jank code to clear the three extra queues
            if(!assist.isEmpty()){assist.forEach(player.unit().plans::remove); assist.clear();}
            if(!unfinished.isEmpty()){unfinished.forEach(player.unit().plans::remove); unfinished.clear();}

            Units.nearby(player.unit().team, player.unit().x, player.unit().y, Float.MAX_VALUE, u -> {if(u.canBuild() && u != player.unit() && u.isBuilding())u.plans.forEach(assist::add);});
            if(!player.unit().team.data().blocks.isEmpty())player.unit().team.data().blocks.forEach(block -> broken.add(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config)));
            world.tiles.forEach(tile -> {if(tile.team() == player.team() && tile.build instanceof ConstructBlock.ConstructBuild)unfinished.add(tile.<ConstructBlock.ConstructBuild>bc().previous == tile.<ConstructBlock.ConstructBuild>bc().cblock ? new BuildPlan(tile.x, tile.y) : new BuildPlan(tile.x, tile.y, tile.build.rotation, tile.<ConstructBlock.ConstructBuild>bc().cblock, tile.build.config()));});

            boolean all = false, found = false;
            Queue[] queues = {player.unit().plans, broken, assist, unfinished};
            for (int x = 0; x < 2; x++) {
                for (Queue queue : queues) {
                    Queue<BuildPlan> plans = sortPlans(queue, all, true);
                    if (plans.isEmpty()) continue;
                    /* TODO: This doesnt work lol
                    plans.forEach(plan -> Navigation.obstacles.forEach(obstacle -> {if(Mathf.dstm(obstacle.x, obstacle.y, plan.x, plan.y) <= obstacle.range){plans.remove(plan);player.unit().plans.remove(plan);}}));
                    if (plans.isEmpty()) continue; */
                    plans.forEach(player.unit().plans::remove);
                    plans.forEach(player.unit().plans::addFirst);
                    found = true;
                    break;
                }
                if (found) break;
                all = true;
            }
        }

        if (player.unit().isBuilding()) {
            BuildPlan req = player.unit().buildPlan(); //approach request if building

            boolean valid =
                    (req.tile().build instanceof ConstructBlock.ConstructBuild && req.tile().<ConstructBlock.ConstructBuild>bc().cblock == req.block) ||
                            (req.breaking ?
                                    Build.validBreak(player.unit().team(), req.x, req.y) :
                                    Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));

            if(valid){
                //move toward the request
                Formation formation = player.unit().formation;
                float range = buildingRange - 10;
                if (formation != null) range -= formation.pattern.spacing / (float)Math.sin(180f / formation.pattern.slots * Mathf.degRad);
                new PositionWaypoint(req.getX(), req.getY(), 0, range).run();
            }else{
                //discard invalid request
                player.unit().plans.removeFirst();
            }
        }
    }

    @Override
    float progress() {
        return 0;
    }

    @Override
    Position next() {
        return null;
    }

    /** @param includeAll whether to include unaffordable plans (appended to end of affordable ones)
     @param largeFirst reverses the order of outputs, returning the furthest plans first
     @return {@code Queue<BuildPlan>} sorted by distance */
    @Nullable
    public Queue<BuildPlan> sortPlans(Queue<BuildPlan> plans, boolean includeAll, boolean largeFirst) {
        if (plans == null) return null;
        Queue<BuildPlan> out = new Queue<>();
        Seq<BuildPlan> sorted = new Seq<>();
        sorted.addAll(plans);
        sorted.sort(p -> Mathf.dstm(player.tileX(), player.tileY(), p.x, p.y));
        if(!largeFirst)sorted.reverse();
        for (BuildPlan p : sorted) { // The largest distance is at the start of the sequence by this point
            if (player.unit().shouldSkip(p, core)) {
                if (includeAll) out.addLast(p);
            } else {
                out.addFirst(p);
            }
        }
        return out;
    }
}

