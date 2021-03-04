package mindustry.client.navigation;

import arc.Core;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Nullable;
import mindustry.ai.formations.Formation;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.environment.Boulder;

import static mindustry.Vars.*;

public class BuildPath extends Path {
    Building core = player.core();
    private boolean show;
    Interval timer = new Interval();
    public Queue<BuildPlan> broken = new Queue<>(), boulders = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>(), cleanup = new Queue<>(), networkAssist = new Queue<>();
    public Seq<Queue<BuildPlan>> queues = new Seq<>(8);

    @SuppressWarnings("unchecked")
    public BuildPath(){
        queues.addAll(player.unit().plans, broken, assist, unfinished, networkAssist); // Every queue except for cleanup and boulders is included by default
    }

    @SuppressWarnings("unchecked")
    public BuildPath(String args){
        for (String arg : args.split("\\s")) {
            switch (arg) {
                case "all" -> queues.addAll(player.unit().plans, broken, assist, unfinished, networkAssist);
                case "self" -> queues.add(player.unit().plans);
                case "broken" -> queues.add(broken);
                case "boulders" -> queues.add(boulders);
                case "assist" -> queues.add(assist);
                case "unfinished" -> queues.add(unfinished);
                case "cleanup" -> queues.add(cleanup);
                case "networkassist" -> queues.add(networkAssist);
                default -> ui.chatfrag.addMessage("[scarlet]Invalid option: " + arg, null);
            }
        }
        if (queues.isEmpty()) {
            ui.chatfrag.addMessage("[scarlet]No valid options specified, defaulting to self.\nValid options: All, self, broken, boulders, assist, unfinished, cleanup, networkassist", null);
            queues.add(player.unit().plans);
        }
    }

    @Override
    public void setShow(boolean show) { this.show = show; }

    @Override
    public boolean getShow() { return show; }

    private void clearQueue(Queue<BuildPlan> queue) {
        if (!queue.isEmpty() && queues.contains(queue)) {
            for (BuildPlan item : queue) {
                player.unit().plans.remove(item);
            }
            queue.clear();
        }
    }

    @Override
    public void init() {
        for (Tile tile : world.tiles) {
            if (tile.team() == Team.derelict && tile.breakable() && tile.isCenter() && !(tile.block() instanceof Boulder)) {
                cleanup.add(new BuildPlan(tile.x, tile.y));
            }
        }
    }

    @Override @SuppressWarnings("unchecked rawtypes") // Java sucks so warnings must be suppressed
    public void follow() {
        // TODO: Make sure that unfinished and cleanup don't conflict
        if (timer.get(15)) {
            clearQueue(broken);
            clearQueue(boulders);
            clearQueue(assist);
            clearQueue(unfinished);
            // Don't clear network assist queue, instead remove finished plans
            for (BuildPlan plan : networkAssist) {
                if (plan.isDone()) {
                    networkAssist.remove(plan);
                    player.unit().plans.remove(plan);
                }
            }

            if(queues.contains(broken) && !player.unit().team.data().blocks.isEmpty()) {
                for (Teams.BlockPlan block : player.unit().team.data().blocks) {
                    broken.add(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config));
                }
            }

            if(queues.contains(assist)) {
                Units.nearby(player.unit().team, player.unit().x, player.unit().y, Float.MAX_VALUE, unit -> {
                    if(unit.canBuild() && player.unit() != null && unit != player.unit() && unit.isBuilding()) {
                        Queue<BuildPlan> buildPlans = assist;
                        for (BuildPlan plan : unit.plans) {
                            buildPlans.add(plan);
                        }
                    }
                });
            }
            if(queues.contains(unfinished) || queues.contains(boulders)) {
                for (Tile tile : world.tiles) {
                    if (tile.breakable() && tile.block() instanceof Boulder ||
                            tile.build instanceof ConstructBlock.ConstructBuild d && d.previous instanceof Boulder) {
                        boulders.add(new BuildPlan(tile.x, tile.y));
                    }
                    else if (tile.team() == player.team() &&
                            tile.build instanceof ConstructBlock.ConstructBuild entity && tile.isCenter()) {
                        unfinished.add(entity.wasConstructing ?
                                new BuildPlan(tile.x, tile.y, tile.build.rotation, entity.cblock, tile.build.config()) :
                                new BuildPlan(tile.x, tile.y));
                    }
                }
            }

            boolean all = false;
            dosort:
            for (int x = 0; x < 2; x++) {
                for (Queue queue : queues) {
                    Queue<BuildPlan> plans = sortPlans(queue, all, true);
                    if (plans.isEmpty()) continue;
                    /* TODO: This doesn't work lol
                    plans.forEach(plan -> Navigation.obstacles.forEach(obstacle -> {if(Mathf.dstm(obstacle.x, obstacle.y, plan.x, plan.y) <= obstacle.range){plans.remove(plan);player.unit().plans.remove(plan);}}));
                    if (plans.isEmpty()) continue; */
                    plans.forEach(player.unit().plans::remove);
                    plans.forEach(player.unit().plans::addFirst);
                    break dosort;
                }
                all = true;
            }
        }

        if (player.unit().isBuilding()) {
            BuildPlan req = player.unit().buildPlan(); //approach request if building

            boolean valid =
                    (req.tile().build instanceof ConstructBlock.ConstructBuild entity && entity.cblock == req.block) ||
                            (req.breaking ?
                                    Build.validBreak(player.unit().team(), req.x, req.y) :
                                    Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));

            if(valid){
                //move toward the request
                Formation formation = player.unit().formation;
                float range = buildingRange - player.unit().hitSize()/2 - 10;
                if (formation != null) range -= formation.pattern.spacing / (float)Math.sin(180f / formation.pattern.slots * Mathf.degRad);
                if (Core.settings.getBool("assumeunstrict")) range /= 2; // Teleport closer so its not weird when building stuff like conveyors
                new PositionWaypoint(req.getX(), req.getY(), 0, range).run();
            }else{
                //discard invalid request
                player.unit().plans.removeFirst();
            }
        }
    }

    @Override
    public float progress() {
        return 0;
    }

    @Override
    public void reset() {
        broken.clear();
        boulders.clear();
        assist.clear();
        unfinished.clear();
        cleanup.clear();
    }

    @Override
    public Position next() {
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
            if (p.block == null || player.unit().shouldSkip(p, core)) {
                if (includeAll)out.addLast(p);
            } else {
                out.addFirst(p);
            }
        }
        return out;
    }
}

