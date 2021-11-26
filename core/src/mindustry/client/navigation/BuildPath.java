package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.ai.formations.*;
import mindustry.client.*;
import mindustry.client.communication.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.logic.*;

import java.util.concurrent.atomic.*;

import static mindustry.Vars.*;

public class BuildPath extends Path { // FINISHME: Dear god, this file does not belong on this planet, its so bad.
    private boolean show, activeVirus;
    Interval timer = new Interval(2);
    public Queue<BuildPlan> broken = new Queue<>(), boulders = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>(), cleanup = new Queue<>(), networkAssist = new Queue<>(), virus = new Queue<>(), drills = new Queue<>(), belts = new Queue<>(), overdrives = new Queue<>();
    public Seq<Queue<BuildPlan>> queues = new Seq<>();
    private Seq<Item> mineItems;
    private int cap;
    private GridBits blocked = new GridBits(world.width(), world.height()), blockedPlayer = new GridBits(world.width(), world.height());
    private int radius = Core.settings.getInt("defaultbuildpathradius");
    private final Vec2 origin = new Vec2(player.x, player.y);
    private final ObjectMap<Block, Block> upgrades = ObjectMap.of(
        Blocks.conveyor, Blocks.titaniumConveyor,
        Blocks.conduit, Blocks.pulseConduit,
        Blocks.mechanicalDrill, Blocks.pneumaticDrill
    );
    private BuildPlan req;
    private boolean valid;
    private float range;
    private final Pool<BuildPlan> pool = Pools.get(BuildPlan.class, BuildPlan::new);
    private final PQueue<BuildPlan> priority = new PQueue<>(300, Structs.comps(Structs.comparingBool(plan -> plan.block != null && player.unit().shouldSkip(plan, player.core())), Structs.comparingFloat(plan -> plan.dst(player))));
    private final AtomicBoolean isBlocked = new AtomicBoolean(false);
    private final Seq<BuildPlan> freed = new Seq<>();

    {
        Events.on(EventType.WorldLoadEvent.class, e -> { // Account for changing world sizes
            blocked = new GridBits(world.width(), world.height());
            blockedPlayer = new GridBits(world.width(), world.height());
        });
        addListener(pool::clear); // Remove the unneeded items on path end
    }

    public BuildPath() {
        this(null, 0);
    }

    @SuppressWarnings("unchecked")
    public BuildPath(Seq<Item> mineItems, int cap) {
        queues.addAll(player.unit().plans, broken, assist, unfinished, networkAssist, drills, belts); // Most queues included by default
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
                case "drills", "mines", "mine", "drill", "d" -> queues.add(drills);
                case "belts", "conveyors", "conduits", "pipes", "ducts", "tubes", "b" -> queues.add(belts);
                case "upgrade", "upgrades", "u" -> queues.addAll(drills, belts);
                case "overdrives", "od" -> queues.add(overdrives);
                default -> {
                    if (Strings.parseInt(arg) > 0) radius = Strings.parseInt(arg);
                    else ui.chatfrag.addMessage(Core.bundle.format("client.path.builder.invalid", arg));
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

    public void clearQueues() {
        for (var queue : queues) {
            if (queue == player.unit().plans) continue; // Don't clear our own buildplans lol

            for (var plan : queue) {
                if (queue == networkAssist && !plan.isDone()) continue;

                player.unit().plans.remove(plan);
                if (!freed.contains(plan)) freed.add(plan);
            }
            if (queue != networkAssist) queue.clear();
        }
        pool.freeAll(freed);
        freed.clear();
    }

    @Override @SuppressWarnings("unchecked rawtypes") // Java sucks so warnings must be suppressed
    public void follow() {
        var core = player.core();
        if (timer.get(15) && core != null) {
            if (mineItems != null) {
                Item item = mineItems.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));

                if (item != null && core.items.get(item) <= cap / 2) { // Switch back to MinePath when core is low on items
                    player.sendMessage("[accent]Automatically switching to back to MinePath as the core is low on items.");
                    Navigation.follow(new MinePath(mineItems, cap));
                }
            }

            if (timer.get(1, 300)) {
                clientThread.taskQueue.post(() -> {
                    blocked.clear();
                    blockedPlayer.clear();
                    synchronized (Navigation.obstacles) {
                        for (var turret : Navigation.obstacles) {
                            if (!turret.canShoot) continue;
                            int lowerXBound = (int)(turret.x - turret.radius) / tilesize;
                            int upperXBound = (int)(turret.x + turret.radius) / tilesize;
                            int lowerYBound = (int)(turret.y - turret.radius) / tilesize;
                            int upperYBound = (int)(turret.y + turret.radius) / tilesize;
                            for (int x = lowerXBound ; x <= upperXBound; x++) {
                                for (int y = lowerYBound ; y <= upperYBound; y++) {
                                    if (Structs.inBounds(x, y, world.width(), world.height()) && turret.contains(x * tilesize, y * tilesize)) {
                                        if (!turret.targetGround) blocked.set(x, y);
                                        if (turret.canHitPlayer) blockedPlayer.set(x, y);
                                    }
                                }
                            }
                        }
                    }
                });
            }

            clearQueues();

            if(queues.contains(broken) && !player.unit().team.data().blocks.isEmpty()) {
                for (Teams.BlockPlan block : player.unit().team.data().blocks) {
                    broken.add(pool.obtain().set(block.x, block.y, block.rotation, content.block(block.block), block.config));
                }
            }

            if(queues.contains(assist)) {
                Units.nearby(player.unit().team, player.unit().x, player.unit().y, Float.MAX_VALUE, unit -> {
                    if(player.unit() != null && unit != player.unit() && unit.isBuilding()) {
                        for (BuildPlan plan : unit.plans) {
                            if (BuildPlanCommunicationSystem.INSTANCE.isNetworking(plan)) continue;
                            assist.add(plan);
                        }
                    }
                });
            }

            if(queues.contains(overdrives)) {
                for (var overdrive : ClientVars.overdrives) {
                    for (var other : ClientVars.overdrives) {
                        if (((OverdriveProjector)overdrive.block).speedBoost > ((OverdriveProjector)other.block).speedBoost && Tmp.cr1.set(overdrive.x, overdrive.y, overdrive.realRange()).contains(Tmp.cr2.set(other.x, other.y, other.realRange()))) {
                            overdrives.add(pool.obtain().set(other.tileX(), other.tileY()));
                        }
                    }
                }
            }

            if(queues.contains(unfinished) || queues.contains(boulders) || queues.contains(cleanup) || queues.contains(virus) || queues.contains(drills) || queues.contains(belts)) {
                for (Tile tile : world.tiles) {
                    if (queues.contains(virus) && tile.team() == player.team() && tile.build instanceof LogicBlock.LogicBuild build && build.isVirus) { // Dont add configured processors
                        virus.add(pool.obtain().set(tile.x, tile.y));

                    } else if (queues.contains(boulders) && tile.breakable() && tile.block() instanceof Prop || tile.build instanceof ConstructBlock.ConstructBuild build && build.previous instanceof Prop) {
                        boulders.add(pool.obtain().set(tile.x, tile.y));

                    } else if (queues.contains(cleanup) && tile.isCenter() && (tile.build instanceof ConstructBlock.ConstructBuild build && !build.wasConstructing && build.lastBuilder != null && build.lastBuilder == player.unit() || tile.team() == Team.derelict && tile.breakable() && !(tile.block() instanceof Prop))) {
                        cleanup.add(pool.obtain().set(tile.x, tile.y));

                    } else if (queues.contains(unfinished) && tile.team() == player.team() && tile.build instanceof ConstructBlock.ConstructBuild build && tile.isCenter()) {
                        unfinished.add(build.wasConstructing ?
                            pool.obtain().set(tile.x, tile.y, tile.build.rotation, build.current, tile.build.config()) :
                            pool.obtain().set(tile.x, tile.y));

                    } else if ((queues.contains(belts) || queues.contains(drills)) && tile.team() == player.team() && tile.build != null && tile.isCenter()) {
                        Block block = tile.build instanceof ConstructBlock.ConstructBuild b ? b.previous : tile.block();

                        if (upgrades.containsKey(block)) {
                            Block upgrade = upgrades.get(block);
                            if ((state.isCampaign() && !upgrade.unlocked()) || Structs.contains(upgrade.requirements, i -> !core.items.has(i.item, 100) && Mathf.round(i.amount * state.rules.buildCostMultiplier) > 0 && !(tile.build instanceof ConstructBlock.ConstructBuild))) continue;
                            if (block == Blocks.mechanicalDrill || (queues.contains(belts) && queues.contains(drills))) { // FINISHME: Just use a single queue for upgrades
                                drills.add(pool.obtain().set(tile.x, tile.y, tile.build.rotation, upgrade));
                            } else {
                                belts.add(pool.obtain().set(tile.x, tile.y, tile.build.rotation, upgrade));
                            }
                        }
                    }
                }
            }
             if (queues.contains(virus)) {
                 activeVirus = !virus.isEmpty();
                 if (!activeVirus) { // All processors broken or configured
                     for (Tile tile : world.tiles) {
                         if (tile.team() == player.team() && (tile.build instanceof ConstructBlock.ConstructBuild cb && cb.current instanceof LogicBlock || tile.build instanceof LogicBlock.LogicBuild build && build.code.startsWith("print \"Logic grief auto removed by:\"\nprint \""))) {
                             virus.add(pool.obtain().set(tile.x, tile.y));
                         }
                     }
                 }
             }

            boolean all = false;
            sort:
            for (int i = 0; i < 2; i++) {
                for (Queue queue : queues) {
                    sortPlans(queue, all);
                    if (priority.empty()) continue;
                    i = 0;
                    BuildPlan plan;
                    while ((plan = priority.poll()) != null && i++ < 300) {
                        player.unit().plans.remove(plan);
                        player.unit().plans.addLast(plan);
                    }
                    priority.clear();
                    break sort;
                }
                all = true;
            }
        }

        // Remove config from the furthest virus blocks until we hit the ratelimit
        while (activeVirus && !virus.isEmpty() && ClientVars.configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
            req = Geometry.findFurthest(player.x, player.y, virus);
            virus.remove(req);
            player.unit().plans.remove(req);
            if (req.build() instanceof LogicBlock.LogicBuild l) {
                Call.tileConfig(player, req.build(), LogicBlock.compress(String.format("print \"Logic grief auto removed by:\"\nprint \"%.34s\"", Strings.stripColors(player.name)), l.relativeConnections()));
            }
        }

        if (player.unit().isBuilding()) { // Approach request if building
            var req = player.unit().buildPlan();

            if(valid = validPlan(req)){
                //move toward the request
                Formation formation = player.unit().formation;
                range = buildingRange - player.unit().hitSize() / 2 - 32; // Range - 4 tiles
                if (formation != null) range -= formation.pattern.radius(); // Account for the player formation
                Path.goTo(req, range);
            }else{
                //discard invalid request
                player.unit().plans.removeFirst();
            }
        } else if (blockedPlayer.get(player.tileX(), player.tileY())) { // Leave enemy turret range while not building
            if (clientThread.taskQueue.size() == 0) clientThread.taskQueue.post(() -> { // FINISHME: This is totally not inefficient at all...
                var safeTiles = new Seq<Tile>(){{
                    world.tiles.eachTile(t -> {
                        if (!blockedPlayer.get(t.x, t.y)) add(t);
                    });
                }};
                var tile = Geometry.findClosest(player.x, player.y, safeTiles);
                waypoint.set(tile.getX(), tile.getY(), 0, 0);
            });
            waypoint.run(0);
        }
    }

    @Override
    public void draw() {
        if (valid && player.unit().isBuilding()) waypoints.draw();
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

    /** Adds all the plans to the priority variable
     * @param includeAll whether to include unaffordable plans (appended to end of affordable ones)*/
    private void sortPlans(Queue<BuildPlan> plans, boolean includeAll) {
        if (plans == null) return;
        plans.each(plan -> {
            isBlocked.set(false);
            plan.tile().getLinkedTilesAs(plan.block, t -> {
                if (blocked.get(t.x, t.y)) isBlocked.set(true);
            });
            if ((radius == 0 || plan.dst(origin) < radius * tilesize) && !isBlocked.get() && (includeAll || (plan.block != null && !player.unit().shouldSkip(plan, player.core()))) && validPlan(plan)) priority.add(plan);
        });
    }

    private boolean validPlan(BuildPlan req) {
        return (!activeVirus || virus.indexOf(req, true) == -1 || req.tile().block() instanceof LogicBlock) &&
            (req.breaking ?
            Build.validBreak(player.unit().team(), req.x, req.y) :
            Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));
    }
}

