package mindustry.world.blocks;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.Timer;
import arc.util.*;
import arc.util.io.*;
import mindustry.annotations.Annotations.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.navigation.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.modules.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static mindustry.Vars.*;
import static mindustry.ui.Styles.*;

/** A block in the process of construction. */
public class ConstructBlock extends Block{
    private static final ConstructBlock[] consBlocks = new ConstructBlock[maxBlockSize];

    private static long lastWarn = 0;
    private static long lastTime = 0;
    private static int pitchSeq = 0;
    private static long lastPlayed;
    private static Toast toast = null;

    public ConstructBlock(int size){
        super("build" + size);
        this.size = size;
        update = true;
        health = 20;
        consumesTap = true;
        solidifes = true;
        consBlocks[size - 1] = this;
        sync = true;
    }

    /** Returns a ConstructBlock by size. */
    public static ConstructBlock get(int size){
        if(size > maxBlockSize) throw new IllegalArgumentException("No. Don't place ConstructBlock of size greater than " + maxBlockSize);
        return consBlocks[size - 1];
    }

    @Remote(called = Loc.server)
    public static void deconstructFinish(Tile tile, Block block, Unit builder){
        if(tile != null && builder != null && block != null){
            if(Navigation.currentlyFollowing instanceof UnAssistPath){
                if(((UnAssistPath) Navigation.currentlyFollowing).assisting == builder.getPlayer()){
                    if(block.isVisible()) {
                        Log.debug("Build: " + tile.build.config() + " Block: " + tile.block().lastConfig);
                        ((UnAssistPath) Navigation.currentlyFollowing).toUndo.add(new BuildPlan(tile.x, tile.y, tile.build.rotation, block, tile.build.config()));
                    }
                }
            }
        }
        if (tile != null && block != null) {
            tile.getLinkedTiles(t -> Events.fire(new BlockBuildEventTile(t, tile.team(), builder, block, Blocks.air, tile.build == null? null : tile.build.config(), null)));
            Team team = tile.team();
            Fx.breakBlock.at(tile.drawx(), tile.drawy(), block.size);
            Events.fire(new BlockBuildEndEvent(tile, builder, team, true, tile.build == null? null : tile.build.config(), tile.block()));
            tile.remove();
            if (shouldPlay()) Sounds.breaks.at(tile, calcPitch(false));
        }
    }

    /** Send a warning in chat when these blocks are broken/picked up/built over as they typically shouldn't be touched. */
    public static void breakWarning(Tile tile, Block block, Unit builder){
        if (!Core.settings.getBool("breakwarnings") || !tile.isCenter() || state.rules.infiniteResources || builder == null || !builder.isPlayer()) return; // Don't warn in sandbox for obvious reasons.

        Seq<Block> warnBlocks = Seq.with(Blocks.powerSource, Blocks.powerVoid, Blocks.itemSource, Blocks.itemVoid, Blocks.liquidSource, Blocks.liquidVoid); // All blocks that shouldn't be broken. Note: Untested with multiblocks, likely to behave in a strange manner.

        if (warnBlocks.contains(block) && Time.timeSinceMillis(tile.lastBreakWarn) > 10_000) { //TODO: Revise this, maybe do break warns per user?
            Timer.schedule(() -> ui.chatfrag.addMessage(Strings.format("[accent]@ [scarlet]just removed/picked up a @ at (@, @)", Strings.stripColors(builder.getPlayer().name), block.localizedName, tile.x, tile.y), "Anti Grief"), 0, 0, 2);
            tile.lastBreakWarn = Time.millis();
        }
    }

    @Remote(called = Loc.server)
    public static void constructFinish(Tile tile, Block block, @Nullable Unit builder, byte rotation, Team team, Object config){
        if(tile == null) return;

        float healthf = tile.build == null ? 1f : tile.build.healthf();
        Seq<Building> prev = tile.build instanceof ConstructBuild co ? co.prevBuild : null;
        Block prevBlock = tile.block();


        if (block == null) {
            Events.fire(new BlockBreakEvent(tile, team, builder, tile.block(), tile.build == null? null : tile.build.config()));
        }

        tile.setBlock(block, team, rotation);

        if(tile.build != null){
            tile.build.health = block.health * healthf;

            if(config != null){
                tile.build.configured(builder, config);
            }

            if(prev != null && prev.size > 0){
                tile.build.overwrote(prev);
            }

            if(builder != null && builder.getControllerName() != null){
                tile.build.lastAccessed = builder.getControllerName();
            }
        }

        //last builder was this local client player, call placed()
        if(tile.build != null && !headless && builder == player.unit()){
            tile.build.playerPlaced(config);
        }

        if (prev != null) {
            for (var item : prev) {
                Events.fire(new BlockBuildEventTile(item.tile, item.team, builder, item.block, block, item.config(), null));
            }
        }

        Events.fire(new BlockBuildEndEvent(tile, builder, team, false, config, prevBlock));

        Fx.placeBlock.at(tile.drawx(), tile.drawy(), block.size);

        if(builder != null && tile.build != null){
            tile.getLinkedTiles(t -> t.addToLog(new PlaceTileLog(builder, t, Instant.now().getEpochSecond(), "", block, tile.build.config())));
            if (Core.settings.getBool("viruswarnings") && builder.isPlayer() && config instanceof byte[] && tile.build instanceof LogicBlock.LogicBuild l && BuildPath.virusBlock(l.code)) {
                ui.chatfrag.addMessage(Strings.format("@ has potentially placed a logic virus at (@, @) [accent]SHIFT + @ to view", builder.getPlayer().name, l.tileX(), l.tileY(), Core.keybinds.get(Binding.navigate_to_camera).key.name()), null, Color.scarlet.cpy().mul(.75f));
                control.input.lastVirusWarning = l;
                control.input.lastVirusWarnTime = Time.millis();
                ClientVars.lastSentPos.set(l.tileX(), l.tileY());
            }
            if(Navigation.currentlyFollowing instanceof UnAssistPath){
                if (((UnAssistPath) Navigation.currentlyFollowing).assisting == builder.getPlayer()) {
                    if(Navigation.currentlyFollowing != null) {
                        for (BuildPlan p : ((UnAssistPath) Navigation.currentlyFollowing).toUndo) {
                            if (p.x == tile.x && p.y == tile.y) {
                                ((UnAssistPath) Navigation.currentlyFollowing).toUndo.remove(p);
                            }
                        }
                        ((UnAssistPath) Navigation.currentlyFollowing).toUndo.add(new BuildPlan(tile.x, tile.y));
                        if (config != null) {
                            ClientVars.configs.add(new ConfigRequest(tile.x, tile.y, null));
                        }
                    }
                }
            }
        }
        if(shouldPlay()) Sounds.place.at(tile, calcPitch(true));
    }

    static boolean shouldPlay(){
        if(Time.timeSinceMillis(lastPlayed) >= 32){
            lastPlayed = Time.millis();
            return true;
        }else{
            return false;
        }
    }

    static float calcPitch(boolean up){
        if(Time.timeSinceMillis(lastTime) < 16 * 30){
            lastTime = Time.millis();
            pitchSeq ++;
            if(pitchSeq > 30){
                pitchSeq = 0;
            }
            return 1f + Mathf.clamp(pitchSeq / 30f) * (up ? 1.9f : -0.4f);
        }else{
            pitchSeq = 0;
            lastTime = Time.millis();
            return Mathf.random(0.7f, 1.3f);
        }
    }

    public static void constructed(Tile tile, Block block, Unit builder, byte rotation, Team team, Object config){
        Block prev = tile.block();

        if (tile.build instanceof ConstructBuild b) {
            for (var item : b.prevBuild != null ? b.prevBuild : new Seq<Building>()) {
                Events.fire(new EventType.BlockBuildEventTile(item.tile, item.team, builder, item.block, block, item.config(), null));
            }
        }

        Call.constructFinish(tile, block, builder, rotation, team, config);
        if(tile.build != null){
            tile.build.placed();
        }

        Events.fire(new BlockBuildEndEvent(tile, builder, team, false, config, prev));
    }

    @Override
    public boolean isHidden(){
        return true;
    }

    public class ConstructBuild extends Building{
        /**
         * The recipe of the block that is being constructed.
         * If there is no recipe for this block, as is the case with rocks, 'previous' is used.
         */
        public @Nullable Block cblock;
        public @Nullable Seq<Building> prevBuild;

        public float progress = 0;
        public float buildCost;
        /**
         * The block that used to be here.
         * If a non-recipe block is being deconstructed, this is the block that is being deconstructed.
         */
        public Block previous;
        public @Nullable Object lastConfig;
        public boolean wasConstructing, activeDeconstruct;
        public float constructColor;

        @Nullable
        public Unit lastBuilder;

        private float[] accumulator;
        private float[] totalAccumulator;

        private float lastProgress = 0f;

        @Override
        public String getDisplayName(){
            return Core.bundle.format("block.constructing", cblock == null ? previous.localizedName : cblock.localizedName);
        }

        @Override
        public TextureRegion getDisplayIcon(){
            return (cblock == null ? previous : cblock).icon(Cicon.full);
        }

        @Override
        public boolean checkSolid(){
            return (cblock != null && cblock.solid) || previous == null || previous.solid;
        }

        @Override
        public Cursor getCursor(){
            return interactable(player.team()) ? SystemCursor.hand : SystemCursor.arrow;
        }

        @Override
        public void tapped(){
            //if the target is constructable, begin constructing
            if(cblock != null){
                if(control.input.buildWasAutoPaused && !control.input.isBuilding && player.isBuilder()){
                    control.input.isBuilding = true;
                }
                player.unit().addBuild(new BuildPlan(tile.x, tile.y, rotation, cblock, lastConfig), false);
            }
        }

        @Override
        public void onDestroyed(){
            Fx.blockExplosionSmoke.at(tile);

            if(!tile.floor().solid && tile.floor().hasSurface()){
                Effect.rubble(x, y, size);
            }
        }

        @Override
        public void updateTile(){
            constructColor = Mathf.lerpDelta(constructColor, activeDeconstruct ? 1f : 0f, 0.2f);
            activeDeconstruct = false;
        }

        @Override
        public void draw(){
            if(!(previous == null || cblock == null || previous == cblock) && Core.atlas.isFound(previous.icon(Cicon.full))){
                Draw.rect(previous.icon(Cicon.full), x, y, previous.rotate ? rotdeg() : 0);
            }

            Draw.draw(Layer.blockBuilding, () -> {
                Draw.color(Pal.accent, Pal.remove, constructColor);

                Block target = cblock == null ? previous : cblock;

                if(target != null){
                    for(TextureRegion region : target.getGeneratedIcons()){
                        Shaders.blockbuild.region = region;
                        Shaders.blockbuild.progress = progress;

                        Draw.rect(region, x, y, target.rotate ? rotdeg() : 0);
                        Draw.flush();
                    }
                }

                Draw.color();
            });
        }

        public void construct(Unit builder, @Nullable Building core, float amount, Object config){
            wasConstructing = true;
            activeDeconstruct = false;
            if(cblock == null){
                kill();
                return;
            }

            if(builder.isPlayer()){
                lastBuilder = builder;
            }

            lastConfig = config;

            if(cblock.requirements.length != accumulator.length || totalAccumulator.length != cblock.requirements.length){
                setConstruct(previous, cblock);
            }

            float maxProgress = core == null || team.rules().infiniteResources ? amount : checkRequired(core.items, amount, false);

            for(int i = 0; i < cblock.requirements.length; i++){
                int reqamount = Math.round(state.rules.buildCostMultiplier * cblock.requirements[i].amount);
                accumulator[i] += Math.min(reqamount * maxProgress, reqamount - totalAccumulator[i] + 0.00001f); //add min amount progressed to the accumulator
                totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * maxProgress, reqamount);
            }

            maxProgress = core == null || team.rules().infiniteResources ? maxProgress : checkRequired(core.items, maxProgress, true);

            progress = state.rules.infiniteResources ? 1 : Mathf.clamp(progress + maxProgress);

            blockWarning(config);

            if(progress >= 1f || state.rules.infiniteResources){
                if(lastBuilder == null) lastBuilder = builder;
                constructed(tile, cblock, lastBuilder, (byte)rotation, builder.team, config);
            }
        }

        public void deconstruct(Unit builder, @Nullable Building core, float amount){
            wasConstructing = false;
            activeDeconstruct = true;
            float deconstructMultiplier = state.rules.deconstructRefundMultiplier;

            if(builder.isPlayer()){
                lastBuilder = builder;
            }

            if(cblock != null){
                ItemStack[] requirements = cblock.requirements;
                if(requirements.length != accumulator.length || totalAccumulator.length != requirements.length){
                    setDeconstruct(cblock);
                }

                //make sure you take into account that you can't deconstruct more than there is deconstructed
                float clampedAmount = Math.min(amount, progress);

                for(int i = 0; i < requirements.length; i++){
                    int reqamount = Math.round(state.rules.buildCostMultiplier * requirements[i].amount);
                    accumulator[i] += Math.min(clampedAmount * deconstructMultiplier * reqamount, deconstructMultiplier * reqamount - totalAccumulator[i]); //add scaled amount progressed to the accumulator
                    totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * clampedAmount * deconstructMultiplier, reqamount);

                    int accumulated = (int)(accumulator[i]); //get amount

                    if(clampedAmount > 0 && accumulated > 0){ //if it's positive, add it to the core
                        if(core != null && requirements[i].item.unlockedNow()){ //only accept items that are unlocked
                            int accepting = Math.min(accumulated, ((CoreBuild)core).storageCapacity - core.items.get(requirements[i].item));
                            //transfer items directly, as this is not production.
                            core.items.add(requirements[i].item, accepting);
                            accumulator[i] -= accepting;
                        }else{
                            accumulator[i] -= accumulated;
                        }
                    }
                }
            }

            progress = Mathf.clamp(progress - amount);

            if(progress <= (previous == null ? 0 : previous.deconstructThreshold) || state.rules.infiniteResources){
                if(lastBuilder == null) lastBuilder = builder;
                Call.deconstructFinish(tile, this.cblock == null ? previous : this.cblock, lastBuilder);
            }
        }

        private float checkRequired(ItemModule inventory, float amount, boolean remove){
            float maxProgress = amount;

            for(int i = 0; i < cblock.requirements.length; i++){
                int sclamount = Math.round(state.rules.buildCostMultiplier * cblock.requirements[i].amount);
                int required = (int)(accumulator[i]); //calculate items that are required now

                if(inventory.get(cblock.requirements[i].item) == 0 && sclamount != 0){
                    maxProgress = 0f;
                }else if(required > 0){ //if this amount is positive...
                    //calculate how many items it can actually use
                    int maxUse = Math.min(required, inventory.get(cblock.requirements[i].item));
                    //get this as a fraction
                    float fraction = maxUse / (float)required;

                    //move max progress down if this fraction is less than 1
                    maxProgress = Math.min(maxProgress, maxProgress * fraction);

                    accumulator[i] -= maxUse;

                    //remove stuff that is actually used
                    if(remove){
                        inventory.remove(cblock.requirements[i].item, maxUse);
                    }
                }
                //else, no items are required yet, so just keep going
            }

            return maxProgress;
        }

        public float progress(){
            return progress;
        }

        public void setConstruct(Block previous, Block block){
            this.constructColor = 0f;
            this.wasConstructing = true;
            this.cblock = block;
            this.previous = previous;
            this.accumulator = new float[block.requirements.length];
            this.totalAccumulator = new float[block.requirements.length];
            this.buildCost = block.buildCost * state.rules.buildCostMultiplier;
        }

        public void setDeconstruct(Block previous){
            if(previous == null) return;

            this.constructColor = 1f;
            this.wasConstructing = false;
            this.previous = previous;
            this.progress = 1f;
            if(previous.buildCost >= 0.01f){
                this.cblock = previous;
                this.buildCost = previous.buildCost * state.rules.buildCostMultiplier;
            }else{
                this.buildCost = 20f; //default no-requirement build cost is 20
            }
            this.accumulator = new float[previous.requirements.length];
            this.totalAccumulator = new float[previous.requirements.length];
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.s(previous == null ? -1 : previous.id);
            write.s(cblock == null ? -1 : cblock.id);

            if(accumulator == null){
                write.b(-1);
            }else{
                write.b(accumulator.length);
                for(int i = 0; i < accumulator.length; i++){
                    write.f(accumulator[i]);
                    write.f(totalAccumulator[i]);
                }
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            progress = read.f();
            short pid = read.s();
            short rid = read.s();
            byte acsize = read.b();

            if(acsize != -1){
                accumulator = new float[acsize];
                totalAccumulator = new float[acsize];
                for(int i = 0; i < acsize; i++){
                    accumulator[i] = read.f();
                    totalAccumulator[i] = read.f();
                }
            }

            if(pid != -1) previous = content.block(pid);
            if(rid != -1) cblock = content.block(rid);

            if(cblock != null){
                buildCost = cblock.buildCost * state.rules.buildCostMultiplier;
            }else{
                buildCost = 20f;
            }
        }

        @Override
        public void update() {
            super.update();
        }

        public void blockWarning(Object config) { // TODO: Account for non player building stuff
            if (!wasConstructing || closestCore() == null || cblock == null || lastBuilder == null || team != player.team() || progress == lastProgress || !lastBuilder.isPlayer()) return;

            Map<Block, Pair<Integer, Integer>> warnBlocks = new HashMap<>(); // Block, warndist, sounddist (0 = off, 101 = always)
            warnBlocks.put(Blocks.thoriumReactor, new Pair<>(Core.settings.getInt("reactorwarningdistance"), Core.settings.getInt("reactorsounddistance")));
            warnBlocks.put(Blocks.incinerator, new Pair<>(Core.settings.getInt("incineratorwarningdistance"), Core.settings.getInt("incineratorsounddistance")));
            warnBlocks.put(Blocks.melter, new Pair<>(Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance")));
            warnBlocks.put(Blocks.oilExtractor, new Pair<>(Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance")));
            warnBlocks.put(Blocks.sporePress, new Pair<>(Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance")));

            if (warnBlocks.containsKey(cblock)) {
                lastBuilder.drawBuildPlans(); // Draw their build plans TODO: This is kind of dumb because it only draws while they are building one of these blocks rather than drawing whenever there is one in the queue
                AtomicInteger distance = new AtomicInteger(Integer.MAX_VALUE);
                closestCore().proximity.each(e -> e instanceof StorageBlock.StorageBuild, block -> block.tile.getLinkedTiles(t -> this.tile.getLinkedTiles(ti -> distance.set(Math.min(World.toTile(t.dst(ti)), distance.get()))))); // This stupidity finds the smallest distance between vaults on the closest core and the block being built
                closestCore().tile.getLinkedTiles(t -> this.tile.getLinkedTiles(ti -> distance.set(Math.min(World.toTile(t.dst(ti)), distance.get())))); // This stupidity checks the distance to the core as well just in case it ends up being shorter

                // Play warning sound (only played when no reactor has been built for 10s)
                if (warnBlocks.get(cblock).second == 101 || distance.get() <= warnBlocks.get(cblock).second) {
                    if (Time.timeSinceMillis(lastWarn) > 10 * 1000) Sounds.corexplode.play(.5f * (float)Core.settings.getInt("sfxvol") / 100.0F);
                    lastWarn = Time.millis();
                }

                if (warnBlocks.get(cblock).first == 101 || distance.get() <= warnBlocks.get(cblock).first) {
                    String format = Strings.format("@ is building a @ at @, @ (@ block@ from core).", Strings.stripColors(lastBuilder.playerNonNull().name), cblock.localizedName, tileX(), tileY(), distance.get(), distance.get() == 1 ? "" : "s");
                    String format2 = String.format("%2d%% completed.", Mathf.round(progress * 100));
                    if (toast == null || toast.parent == null) {
                        toast = new Toast(2f, 0f);
                    } else {
                        toast.clearChildren();
                    }
                    toast.setFadeAfter(2f);
                    toast.add(new Label(format));
                    toast.row();
                    toast.add(new Label(format2, monoLabel));
                    toast.clicked(() -> Spectate.spectate(ClientVars.lastSentPos.cpy().scl(tilesize)));
                    ClientVars.lastSentPos.set(tileX(), tileY());
                }

                if (lastProgress == 0 && Core.settings.getBool("removecorenukes") && cblock instanceof NuclearReactor && !lastBuilder.isLocal() && distance.get() <= 20) { // Automatically remove reactors within 20 blocks of core
                    Call.unitControl(player, ((CoreBuild)closestCore()).unit());
                    Timer.schedule(() -> player.unit().plans.add(new BuildPlan(tileX(), tileY())), net.client() ? netClient.getPing()/1000f+.3f : 0);
                }
            }
            lastProgress = progress;
        }
    }
}
