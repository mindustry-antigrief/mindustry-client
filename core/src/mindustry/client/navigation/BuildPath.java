package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.communication.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.logic.*;

import java.util.concurrent.*;

import static mindustry.Vars.*;

public class BuildPath extends Path { // FINISHME: Dear god, this file does not belong on this planet, its so bad.
    private boolean show, activeVirus;
    Interval timer = new Interval(2);
    public Queue<BuildPlan> broken = new Queue<>(), boulders = new Queue<>(), assist = new Queue<>(), unfinished = new Queue<>(), cleanup = new Queue<>(), networkAssist = new Queue<>(), virus = new Queue<>(), drills = new Queue<>(), belts = new Queue<>(), overdrives = new Queue<>();
    public Seq<Queue<BuildPlan>> queues = new Seq<>();
    public Seq<Item> mineItems;
    private int cap;
    private GridBits blocked = new GridBits(world.width(), world.height()), blockedPlayer = new GridBits(world.width(), world.height()), temp = new GridBits(world.width(), world.height());
    private int radius = Core.settings.getInt("defaultbuildpathradius");
    private final Vec2 origin = new Vec2(player.x, player.y);
    private final ObjectMap<Block, Block> upgrades = ObjectMap.of(
        Blocks.conveyor, Blocks.titaniumConveyor,
        Blocks.conduit, Blocks.pulseConduit,
        Blocks.mechanicalDrill, Blocks.pneumaticDrill
    );
    private BuildPlan req;
    private boolean valid;
    private final Pool<BuildPlan> pool = Pools.get(BuildPlan.class, BuildPlan::new, 15_000); // This is cursed but
    private final PQueue<BuildPlan> priority = new PQueue<>(301, Structs.comps(Structs.comparingBool(plan -> plan.block != null && player.unit().shouldSkip(plan, player.core())), Structs.comparingFloat(plan -> plan.dst(player))));
    private final Seq<BuildPlan> freed = new Seq<>();
    private CompletableFuture<Void> job = null;

    static {
        Events.on(EventType.WorldLoadEvent.class, e -> { // Account for changing world sizes
            if (Navigation.currentlyFollowing instanceof BuildPath bp) {
                bp.blocked = new GridBits(world.width(), world.height());
                bp.blockedPlayer = new GridBits(world.width(), world.height());
            }
        });
    }

    {
        addListener(pool::clear); // Remove the unneeded items on path end
    }

    public BuildPath() {
        this("");
    }

    @SuppressWarnings("unchecked")
    public BuildPath(Seq<Item> mineItems, int cap) {
        init(Core.settings.getString("defaultbuildpathargs"));
        this.mineItems = mineItems;
        this.cap = cap;
    }

    @SuppressWarnings("unchecked")
    public BuildPath(String args) {
        if (!args.trim().isEmpty()) init(args); // Init with provided args
        else init(Core.settings.getString("defaultbuildpathargs")); // No args provided, use default
    }
    
    public static BuildPath Self() {
        return new BuildPath("self");
    }

    private void init(String args) {
        queues.clear(); // Clear old selections (defaults)

        var self = false;
        for (String arg : args.split("\\s")) {
            switch (arg.toLowerCase()) {
                case "all", "*" -> queues.addAll(broken, assist, unfinished, networkAssist, drills, belts);
                case "self", "me" -> self = true;
                case "broken", "destroyed", "dead", "killed", "rebuild", "b", "br" -> queues.add(broken);
                case "boulders", "rocks" -> queues.add(boulders);
                case "assist", "help" -> queues.add(assist);
                case "unfinished", "finish" -> queues.add(unfinished);
                case "cleanup", "derelict", "clean" -> queues.add(cleanup);
                case "networkassist", "na", "network" -> queues.add(networkAssist);
                case "virus" -> queues.add(virus); // Intentionally undocumented due to potential false positives
                case "drills", "mines", "mine", "drill", "d" -> queues.add(drills);
                case "belts", "conveyors", "conduits", "pipes", "ducts", "tubes", "be" -> queues.add(belts);
                case "upgrade", "upgrades", "u" -> queues.addAll(drills, belts);
                case "overdrives", "od" -> queues.add(overdrives);
                default -> {
                    if (Strings.parseInt(arg) > 0) radius = Strings.parseInt(arg);
                    else ui.chatfrag.addMessage(Core.bundle.format("client.path.builder.invalid", arg));
                }
            }
        }

        if (queues.isEmpty() && !self) {
            var defaults = Core.settings.getString("defaultbuildpathargs").toLowerCase();
            if (!args.equals(defaults)) {
                player.sendMessage(Core.bundle.format("client.path.builder.allinvalid", defaults, "All, self, broken, boulders, assist, unfinished, cleanup, networkassist, drills, conveyors, upgrade (drills + conveyors)"));
                init(defaults);
            }
        }
    }

    @Override
    public void setShow(boolean show) { this.show = show; }

    @Override
    public boolean getShow() { return show; }

    public void clearQueues() {
        for (var queue : queues) {
            for (var plan : queue) {
                if (queue == networkAssist && !plan.isDone() || plan.freed) continue;
                player.unit().plans.remove(plan);
//                if (!freed.contains(plan, true)) freed.add(plan);
                pool.free(plan);
            }
            if (queue != networkAssist) queue.clear();
        }
//        pool.freeAll(freed);
        freed.clear();
    }

    @Override @SuppressWarnings({"unchecked", "rawtypes"}) // Java sucks so warnings must be suppressed
    public void follow() {
        var core = player.core();
        if (timer.get(15) && core != null) {
            if (mineItems != null) {
                Item item = mineItems.min(i -> indexer.hasOre(i) && player.unit().canMine(i), i -> core.items.get(i));

                if (item != null && core.items.get(item) <= (cap == 0 ? core.storageCapacity : cap) / 2) { // Switch back to MinePath when core is low on items
                    player.sendMessage("[accent]Automatically switching to back to MinePath as the core is low on items.");
                    Navigation.follow(new MinePath(mineItems, cap));
                }
            }

            if (timer.get(1, 300)) {
                clientThread.post(() -> {
                    for (var turret : Navigation.getEnts()) {
                        if (!turret.canShoot()) continue;
                        Geometry.circle(World.toTile(turret.x()), World.toTile(turret.y()), World.toTile(turret.range), (x, y) -> {
                            if (Structs.inBounds(x, y, world.width(), world.height()) && turret.contains(x * tilesize, y * tilesize)) {
                                if (turret.targetGround || turret.canHitPlayer()) {
                                    temp.set(x, y);
                                    if (turret.targetGround) blocked.set(x, y);
                                    if (turret.canHitPlayer()) blockedPlayer.set(x, y);
                                }
                            }
                        });
                    }
                    for (var t : world.tiles) { // Unset tiles as needed
                        if (!temp.get(t.x, t.y)) {
                            blocked.set(t.x, t.y, false);
                            blockedPlayer.set(t.x, t.y, false);
                        }
                    }
                    temp.clear();
                });
            }

            clearQueues();

            if (queues.contains(broken, true) && !player.unit().team.data().plans.isEmpty()) {
                for (Teams.BlockPlan block : player.unit().team.data().plans) {
                    broken.add(pool.obtain().set(block.x, block.y, block.rotation, content.block(block.block), block.config));
                }
            }

            if (queues.contains(assist, true)) {
                for (Unit unit : player.team().data().units) {
                    if (player.unit() != null && unit != player.unit() && unit.isBuilding() && unit.updateBuilding) {
                        for (BuildPlan plan : unit.plans) {
                            if (BuildPlanCommunicationSystem.INSTANCE.isNetworking(plan)) continue;
                            assist.add(plan);
                        }
                    }
                }
            }

            if (queues.contains(overdrives, true)) {
                for (var overdrive : ClientVars.overdrives) {
                    for (var other : ClientVars.overdrives) {
                        if (((OverdriveProjector)overdrive.block).speedBoost > ((OverdriveProjector)other.block).speedBoost && Tmp.cr1.set(overdrive.x, overdrive.y, overdrive.realRange()).contains(Tmp.cr2.set(other.x, other.y, other.realRange()))) {
                            overdrives.add(pool.obtain().set(other.tileX(), other.tileY()));
                        }
                    }
                }
            }

            if (queues.contains(unfinished, true) || queues.contains(boulders, true) || queues.contains(cleanup, true) || queues.contains(virus, true) || queues.contains(drills, true) || queues.contains(belts, true)) {
                for (Tile tile : world.tiles) {
                    if (queues.contains(virus, true) && tile.team() == player.team() && tile.build instanceof LogicBlock.LogicBuild build && build.isVirus) { // Dont add configured processors
                        virus.add(pool.obtain().set(tile.x, tile.y));

                    } else if (queues.contains(boulders, true) && tile.breakable() && tile.block() instanceof Prop || tile.build instanceof ConstructBlock.ConstructBuild build && build.previous instanceof Prop) {
                        boulders.add(pool.obtain().set(tile.x, tile.y));

                    } else if (queues.contains(cleanup, true) && tile.isCenter() && (tile.build instanceof ConstructBlock.ConstructBuild build && !build.wasConstructing && build.lastBuilder != null && build.lastBuilder == player.unit() || tile.team() == Team.derelict && tile.breakable() && !(tile.block() instanceof Prop))) {
                        cleanup.add(pool.obtain().set(tile.x, tile.y));

                    } else if ((queues.contains(belts, true) || queues.contains(drills, true)) && tile.team() == player.team() && tile.build != null && tile.isCenter()) {
                        Block block = tile.build instanceof ConstructBlock.ConstructBuild b ? b.previous : tile.block();

                        if (upgrades.containsKey(block)) {
                            Block upgrade = upgrades.get(block);
                            if ((state.isCampaign() && !upgrade.unlocked()) || Structs.contains(upgrade.requirements, i -> !core.items.has(i.item, 100) && Mathf.round(i.amount * state.rules.buildCostMultiplier) > 0 && !(tile.build instanceof ConstructBlock.ConstructBuild))) continue;
                            if (block == Blocks.mechanicalDrill || (queues.contains(belts, true) && queues.contains(drills, true))) { // FINISHME: Just use a single queue for upgrades
                                drills.add(pool.obtain().set(tile.x, tile.y, tile.build.rotation, upgrade));
                            } else {
                                belts.add(pool.obtain().set(tile.x, tile.y, tile.build.rotation, upgrade));
                            }
                        }

                    } else if (queues.contains(unfinished, true) && tile.team() == player.team() && tile.build instanceof ConstructBlock.ConstructBuild build && tile.isCenter()) {
                        unfinished.add(build.wasConstructing ?
                            pool.obtain().set(tile.x, tile.y, tile.build.rotation, build.current, tile.build.config()) :
                            pool.obtain().set(tile.x, tile.y));
                    }
                }
            }
             if (queues.contains(virus, true)) {
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
            if (player.unit().plans.isEmpty()) {
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < queues.size; j++) {
                        var queue = queues.get(j); // Since we break out of the loop, we can't use the iterator
                        sortPlans(queue, all);
                        if (priority.empty()) continue;

                        for(int k = 0, count = Math.min(priority.size, 300); k < count; k++){ // Imagine a language with a repeat method
                            player.unit().addBuild(priority.poll());
                        }
                        priority.clear();
                        break sort;
                    }
                    all = true;
                }
            }
        }

        // Remove config from the furthest virus blocks until we hit the ratelimit
        if (activeVirus && !virus.isEmpty()) {
            req = Geometry.findFurthest(player.x, player.y, virus);
            virus.remove(req);
            player.unit().plans.remove(req);
            if (req.build() instanceof LogicBlock.LogicBuild l) {
                ClientVars.configs.add(new ConfigRequest(l, LogicBlock.compress(Strings.format("print \"Logic grief auto removed by:\"\nprint \"@\"", Strings.stripColors(player.name)), l.relativeConnections())));
            }
        }

        if (player.unit().isBuilding()) { // Approach request if building
            var req = player.unit().buildPlan();

            if(valid = validPlan(req)){
                //move toward the request
                float range = player.unit().type.buildRange - player.unit().hitSize() / 2f - 32; // Range - 4 tiles
                goTo(req.tile(), range); // Cannot go directly to req as it is pooled so the build changes.
            }else{
                //discard invalid request
                player.unit().plans.removeFirst();
            }
        } else if (blockedPlayer.get(player.tileX(), player.tileY())) { // Leave enemy turret range while not building
            if (job == null || job.isDone()) {
                job = clientThread.post(() -> { // FINISHME: This is totally not inefficient at all...
                    var safeTiles = new Seq<Tile>();
                    world.tiles.eachTile(t -> {
                        if (!blockedPlayer.get(t.x, t.y)) safeTiles.add(t);
                    });
                    var tile = Geometry.findClosest(player.x, player.y, safeTiles);
                    waypoint.set(tile.getX(), tile.getY(), 0, 0);
                });
            }
            waypoint.run(0);
        }
    }

    @Override
    public synchronized void draw() {
        if (valid && player.unit().isBuilding()) waypoints.draw();
    }

    @Override
    public float progress() {
        return 0;
    }

    @Override
    public void reset() {}

    @Override
    public Position next() {
        return null;
    }

    private Seq<Tile> tempTiles = new Seq<>();
    /** Adds all the plans to the priority variable
     * @param includeAll whether to include unaffordable plans (appended to end of affordable ones) */
    private void sortPlans(Queue<BuildPlan> plans, boolean includeAll) { // FINISHME: This is bad. Why does this even use a PQ? Its almost certainly slower than a Seq that is sorted once at the end (not to mention it would fix the PQ's super redundant sorting)
        if (plans == null) return; // FINISHME: Why is this null check a thing? Is the plan queue ever null? If it is, that should be fixed.
        float rad = radius * tilesize;
        for (int i = 0, size = plans.size; i < size; i++) {
            var plan = plans.get(i);
            if ((radius == 0 || plan.within(origin, rad)) && validPlan(plan) && !plan.tile().getLinkedTilesAs(plan.block, tempTiles).contains(t -> blocked.get(t.x, t.y))
                && (includeAll || !player.unit().shouldSkip(plan, player.core()) && !blocked.get(plan.x, plan.y))
            ) { // FINISHME: Implement and use a min-max heap and remove the 300th element whenever the priority queue is larger then that as we only use that many.
                priority.add(plan);
            }
        }
    }

    private boolean validPlan(BuildPlan req) {
        return (!activeVirus || virus.indexOf(req, true) == -1 || req.tile().block() instanceof LogicBlock)
            && (req.breaking ? Build.validBreak(player.team(), req.x, req.y) : Build.validPlace(req.block, player.team(), req.x, req.y, req.rotation));
    }
}
