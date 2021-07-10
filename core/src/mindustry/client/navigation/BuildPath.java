package mindustry.client.navigation;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.formations.*;
import mindustry.client.*;
import mindustry.client.navigation.waypoints.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.logic.*;

import java.util.concurrent.atomic.*;

import static mindustry.Vars.*;

public class BuildPath extends Path {
    Building core = player.core();
    private boolean show, activeVirus;
    Interval timer = new Interval(2);
    public Queue<BuildPlan> broken = new Queue<>(), boulders = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>(), cleanup = new Queue<>(), networkAssist = new Queue<>(), virus = new Queue<>(), drills = new Queue<>(), belts = new Queue<>();
    public Seq<Queue<BuildPlan>> queues = new Seq<>(11);
    public Seq<BuildPlan> sorted = new Seq<>();
    private Seq<Item> mineItems;
    private int cap;
    GridBits blocked = new GridBits(world.width(), world.height());
    int radius = Core.settings.getInt("defaultbuildpathradius");
    Position origin = player;

    @SuppressWarnings("unchecked")
    public BuildPath() {
        queues.addAll(player.unit().plans, broken, assist, unfinished, networkAssist, drills, belts); // Most queues included by default
    }

    public BuildPath(Seq<Item> mineItems, int cap) {
        this();
        this.mineItems = mineItems;
        this.cap = cap;
    }

    @SuppressWarnings("unchecked")
    public BuildPath(String args) {
        for (String arg : args.split("\\s")) {
            switch (arg) {
                case "all", "*" -> queues.addAll(player.unit().plans, broken, assist, unfinished, networkAssist, drills, belts);
                case "self", "me" -> queues.add(player.unit().plans);
                case "broken", "destroyed", "dead", "killed", "rebuild" -> queues.add(broken);
                case "boulders", "rocks" -> queues.add(boulders);
                case "assist", "help" -> queues.add(assist);
                case "unfinished", "finish" -> queues.add(unfinished);
                case "cleanup", "derelict", "clean" -> queues.add(cleanup);
                case "networkassist", "na", "network" -> queues.add(networkAssist);
                case "virus" -> queues.add(virus); // Intentionally undocumented due to potential false positives
                case "drills", "mines", "mine", "drill" -> queues.add(drills);
                case "belts", "conveyors", "conduits", "pipes", "ducts", "tubes" -> queues.add(belts);
                default -> {
                    if (Strings.canParsePositiveInt(arg)) radius = Strings.parsePositiveInt(arg);
                    else ui.chatfrag.addMessage(Core.bundle.format("client.path.builder.invalid", arg), null);
                }
            }
        }
        if (queues.isEmpty()) {
            player.sendMessage(Core.bundle.format("client.path.builder.allinvalid", "All, self, broken, boulders, assist, unfinished, cleanup, networkassist, drills, conveyors"));
            queues.add(player.unit().plans);
        }
    }

    @Override
    public void setShow(boolean show) { this.show = show; }

    @Override
    public boolean getShow() { return show; }

    public void clearQueue(Queue<BuildPlan> queue) {
        if (!queue.isEmpty() && queues.contains(queue) && queue != networkAssist) {
            for (BuildPlan item : queue) {
                player.unit().plans.remove(item);
            }
            queue.clear();
        }
    }

    @Override @SuppressWarnings("unchecked rawtypes") // Java sucks so warnings must be suppressed
    public void follow() {
        if (timer.get(15)) {
            if (mineItems != null) {
                Item item = mineItems.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));
                if (item != null && core.items.get(item) <= cap / 2) Navigation.follow(new MinePath(mineItems, cap));
            }

            if (timer.get(1, 300)) {
                blocked.clear();
                for (var turret : Navigation.obstacles) {
                    int lowerXBound = (int)(turret.x - turret.radius) / tilesize;
                    int upperXBound = (int)(turret.x + turret.radius) / tilesize;
                    int lowerYBound = (int)(turret.y - turret.radius) / tilesize;
                    int upperYBound = (int)(turret.y + turret.radius) / tilesize;
                    for (int x = lowerXBound ; x <= upperXBound; x++) {
                        for (int y = lowerYBound ; y <= upperYBound; y++) {
                            if (Structs.inBounds(x, y, world.width(), world.height()) && turret.contains(x * tilesize, y * tilesize)) {
                                blocked.set(x, y);
                            }
                        }
                    }
                }
            }
            clearQueue(broken);
            clearQueue(boulders);
            clearQueue(assist);
            clearQueue(unfinished);
            clearQueue(cleanup);
            clearQueue(virus);
            clearQueue(drills);
            clearQueue(belts);
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
            if(queues.contains(unfinished) || queues.contains(boulders) || queues.contains(cleanup) || queues.contains(virus) || queues.contains(drills) || queues.contains(belts)) {
                for (Tile tile : world.tiles) {
                    if (queues.contains(virus) && tile.team() == player.team() && tile.build instanceof LogicBlock.LogicBuild build && build.isVirus) { // Dont add configured processors
                        virus.add(new BuildPlan(tile.x, tile.y));

                    } else if (queues.contains(boulders) && tile.breakable() && tile.block() instanceof Prop || tile.build instanceof ConstructBlock.ConstructBuild build && build.previous instanceof Prop) {
                        boulders.add(new BuildPlan(tile.x, tile.y));

                    } else if (queues.contains(cleanup) && tile.isCenter() && (tile.build instanceof ConstructBlock.ConstructBuild build && !build.wasConstructing && build.lastBuilder != null && build.lastBuilder == player.unit() || tile.team() == Team.derelict && tile.breakable() && !(tile.block() instanceof Prop))) {
                        cleanup.add(new BuildPlan(tile.x, tile.y));

                    } else if (queues.contains(unfinished) && tile.team() == player.team() && tile.build instanceof ConstructBlock.ConstructBuild build && tile.isCenter()) {
                        unfinished.add(build.wasConstructing ?
                            new BuildPlan(tile.x, tile.y, tile.build.rotation, build.current, tile.build.config()) :
                            new BuildPlan(tile.x, tile.y));

                    } else if ((queues.contains(belts) || queues.contains(drills)) && tile.team() == player.team() && tile.build != null && tile.isCenter()) {
                        Block block = tile.build instanceof ConstructBlock.ConstructBuild b ? b.previous : tile.block();

                        if (queues.contains(belts) && block == Blocks.conveyor) belts.add(new BuildPlan(tile.x, tile.y, tile.build.rotation, Blocks.titaniumConveyor));
                        else if (queues.contains(belts) && block == Blocks.conduit) belts.add(new BuildPlan(tile.x, tile.y, tile.build.rotation, Blocks.pulseConduit));
                        else if (queues.contains(drills) && block == Blocks.mechanicalDrill) drills.add(new BuildPlan(tile.x, tile.y, 0, Blocks.pneumaticDrill));
                    }
                }
            }
             if (queues.contains(virus)) {
                 activeVirus = !virus.isEmpty();
                 if (!activeVirus) { // All processors broken or configured
                     for (Tile tile : world.tiles) {
                         if (tile.team() == player.team() && (tile.build instanceof ConstructBlock.ConstructBuild cb && cb.current instanceof LogicBlock || tile.build instanceof LogicBlock.LogicBuild build && build.code.startsWith("print \"Logic grief auto removed by:\"\nprint \""))) {
                             virus.add(new BuildPlan(tile.x, tile.y));
                         }
                     }
                 }
             }

            boolean all = false;
            sort:
            for (int i = 0; i < 2; i++) {
                for (Queue queue : queues) {
                    PQueue<BuildPlan> plans = sortPlans(queue, all, false);
                    if (plans.empty()) continue;
                    i = 0;
                    BuildPlan plan;
                    Queue<BuildPlan> scuffed = new Queue<>(player.unit().plans.size);
                    player.unit().plans.each(scuffed::add);
                    while ((plan = plans.poll()) != null && i++ < 300) {
                        player.unit().plans.remove(plan);
                        player.unit().plans.addLast(plan);
                    }
                    while (!scuffed.isEmpty()) {
                        plan = scuffed.removeLast();
                        player.unit().plans.remove(plan);
                        player.unit().plans.addFirst(plan);
                    }
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
                if (formation != null) range -= formation.pattern.radius();
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
    public PQueue<BuildPlan> sortPlans(Queue<BuildPlan> plans, boolean includeAll, boolean largeFirst) {
        if (plans == null) return null;
        PQueue<BuildPlan> s2 = new PQueue<>(plans.size, Structs.comps(Structs.comparingBool(plan -> plan.block != null && player.unit().shouldSkip(plan, core)), Structs.comparingFloat(plan -> plan.dst(player))));
        plans.each(plan -> {
            AtomicBoolean dumb = new AtomicBoolean(false);
            plan.tile().getLinkedTilesAs(plan.block, t -> {
                if (blocked.get(t.x, t.y)) dumb.set(true);
            });
            if ((radius == 0 || plan.dst(origin) < radius * tilesize) && !dumb.get() && (includeAll || (plan.block != null && !player.unit().shouldSkip(plan, core))) && validPlan(plan)) s2.add(plan);
        });
        if (largeFirst) s2.comparator = s2.comparator.reversed();
        return s2;
    }

    boolean validPlan (BuildPlan req) {
        return (!activeVirus || virus.indexOf(req, true) == -1 || req.tile().block() instanceof LogicBlock) &&
            (req.breaking ?
            Build.validBreak(player.unit().team(), req.x, req.y) :
            Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));
    }
}

