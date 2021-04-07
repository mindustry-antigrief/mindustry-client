package mindustry.client.navigation;

import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.formations.*;
import mindustry.client.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.logic.*;

import static mindustry.Vars.*;

public class BuildPath extends Path {
    Building core = player.core();
    private boolean show, activeVirus;
    Interval timer = new Interval(2);
    public Queue<BuildPlan> broken = new Queue<>(), boulders = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>(), cleanup = new Queue<>(), networkAssist = new Queue<>(), virus = new Queue<>();
    public Seq<Queue<BuildPlan>> queues = new Seq<>(9);
    public Seq<BuildPlan> sorted = new Seq<>();

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
                case "virus" -> queues.add(virus); // Intentionally undocumented due to potential false positives
                default -> ui.chatfrag.addMessage("[scarlet]Invalid option: " + arg, null);
            }
        }
        if (queues.isEmpty()) {
            ui.chatfrag.addMessage("[scarlet]No valid options specified, defaulting to self." +
                "\nValid options: All, self, broken, boulders, assist, unfinished, cleanup, networkassist", null);
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

    @Override @SuppressWarnings("unchecked rawtypes") // Java sucks so warnings must be suppressed
    public void follow() {
        if (timer.get(15)) {
            clearQueue(broken);
            clearQueue(boulders);
            clearQueue(assist);
            clearQueue(unfinished);
            clearQueue(cleanup);
            clearQueue(virus);
            for (BuildPlan plan : networkAssist) { // Don't clear network assist queue, instead remove finished plans
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
                        for (BuildPlan plan : unit.plans) {
                            assist.add(plan);
                        }
                    }
                });
            }
            if(queues.contains(unfinished) || queues.contains(boulders) || queues.contains(cleanup) || queues.contains(virus)) {
                for (Tile tile : world.tiles) {
                    if (queues.contains(virus) && tile.team() == player.team() && tile.build instanceof LogicBlock.LogicBuild build) {
                        if (virusBlock(build, false)) {
                            virus.add(new BuildPlan(tile.x, tile.y)); // Partially delete the spammed processors, prioritizes ones that haven't been configured yet in the event that you get ratelimited
                        }

                    } else if (queues.contains(boulders) && tile.breakable() && tile.block() instanceof Boulder || tile.build instanceof ConstructBlock.ConstructBuild build && build.previous instanceof Boulder) {
                        boulders.add(new BuildPlan(tile.x, tile.y));

                    } else if (queues.contains(cleanup) && (tile.build instanceof ConstructBlock.ConstructBuild build && build.activeDeconstruct && build.lastBuilder != null && build.lastBuilder == player.unit()) || (tile.team() == Team.derelict && tile.breakable() && tile.isCenter() && !(tile.block() instanceof Boulder))) {
                        cleanup.add(new BuildPlan(tile.x, tile.y));

                    } else if (queues.contains(unfinished) && tile.team() == player.team() && tile.build instanceof ConstructBlock.ConstructBuild build && tile.isCenter()) {
                        unfinished.add(build.wasConstructing ?
                                new BuildPlan(tile.x, tile.y, tile.build.rotation, build.cblock, tile.build.config()) :
                                new BuildPlan(tile.x, tile.y));
                    }
                }
            }
             if (queues.contains(virus)) {
                 activeVirus = !virus.isEmpty();
                 if (!activeVirus) { // The virus has stopped spreading, start cleaning up
                     for (Tile tile : world.tiles) {
                         if (tile.team() == player.team() && (tile.build instanceof ConstructBlock.ConstructBuild cb && cb.cblock instanceof LogicBlock || tile.build instanceof LogicBlock.LogicBuild build && build.code.contains("print \"Logic grief auto removed by:\"\nprint \""))) {
                             virus.add(new BuildPlan(tile.x, tile.y));
                         }
                     }
                 }
             }

            boolean all = false;
            sort:
            for (int i = 0; i < 2; i++) {
                for (Queue queue : queues) {
                    Queue<BuildPlan> plans = sortPlans(queue, all, all); // TODO: should large first always be false or should it stay as all?
                    if (plans.isEmpty()) continue;
                    /* TODO: This doesn't work lol
                    plans.forEach(plan -> Navigation.obstacles.forEach(obstacle -> {if(Mathf.dstm(obstacle.x, obstacle.y, plan.x, plan.y) <= obstacle.range){plans.remove(plan);player.unit().plans.remove(plan);}}));
                    if (plans.isEmpty()) continue; */
                    plans.forEach(player.unit().plans::remove);
                    plans.forEach(player.unit().plans::addFirst);
                    break sort;
                }
                all = true;
            }
        }


        BuildPlan req;
        while (activeVirus && !virus.isEmpty() && ClientVars.configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) { // Remove config from virus blocks if we arent hitting the config ratelimit
            req = sorted.clear().addAll(virus).max(plan -> plan.dst(player));
            virus.remove(req);
            player.unit().plans.remove(req);
            if (req.build() instanceof LogicBlock.LogicBuild l) {
                Call.tileConfig(player, req.build(), LogicBlock.compress(String.format("print \"Logic grief auto removed by:\"\nprint \"%.34s\"", Strings.stripColors(player.name)), l.relativeConnections()));
            }
        }

        if (player.unit().isBuilding()) { // Approach request if building
            req = player.unit().buildPlan();

            if(validPlan(req)){
                //move toward the request
                Formation formation = player.unit().formation;
                float range = buildingRange - player.unit().hitSize()/2 - 32; // Range - 4 tiles
                if (formation != null) range -= formation.pattern.spacing / (float)Math.sin(180f / formation.pattern.slots * Mathf.degRad);
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
     @return {@link Queue<BuildPlan>} sorted by distance */
    @Nullable
    public Queue<BuildPlan> sortPlans(Queue<BuildPlan> plans, boolean includeAll, boolean largeFirst) {
        if (plans == null) return null;
        Queue<BuildPlan> out = new Queue<>();
        sorted.clear();
        sorted.addAll(plans);
        sorted.sort(Structs.comps(Structs.comparingBool(plan -> !(plan.block == null || player.unit().shouldSkip(plan, core))), Structs.comparingFloat(plan -> plan.dst(player))));
        if (!largeFirst) sorted.reverse();
        for (BuildPlan plan : sorted) { // The largest distance is at the start of the sequence by this point
            if (!validPlan(plan)) continue;
            if (includeAll || plan.block != null && !player.unit().shouldSkip(plan, core)) { // TODO: This is terrible and slow
                out.addLast(plan);
                if (out.size > 300) out.removeFirst();
            }
        }
        return out;
    }

    boolean validPlan (BuildPlan req) {
        return (!activeVirus || virus.indexOf(req, true) == -1 || req.tile().block() instanceof LogicBlock) &&
            (req.breaking ?
            Build.validBreak(player.unit().team(), req.x, req.y) :
            Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));
    }

    public static boolean virusBlock (LogicBlock.LogicBuild block, boolean checkEnd) {
        String code = block.code;
        if (checkEnd && code.startsWith("end")) return false;
        return code.contains("ucontrol build") && code.contains("ubind")
            && (((code.contains("@x") || code.contains("@shootX") || code.contains("@thisx")) && (code.contains("@y") || code.contains("@shootY") ||code.contains("@thisy")) || code.contains("@this") || code.contains("@controller"))); // Doesn't use a regex as those are expensive
    }

    public static boolean virusBlock (LogicBlock.LogicBuild block) {
        return virusBlock(block, true);
    }
}

