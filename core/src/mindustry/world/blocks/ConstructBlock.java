package mindustry.world.blocks;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.func.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.Timer;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
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
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.modules.*;

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
    private static final Seq<Block> breakWarnBlocks = Seq.with(Blocks.powerSource, Blocks.powerVoid, Blocks.itemSource, Blocks.itemVoid, Blocks.liquidSource, Blocks.liquidVoid); // All blocks that shouldn't be broken. Note: Untested with multiblocks, likely to behave in a strange manner.

    public ConstructBlock(int size){
        super("build" + size);
        this.size = size;
        update = true;
        health = 10;
        consumesTap = true;
        solidifes = true;
        generateIcons = false;
        inEditor = false;
        consBlocks[size - 1] = this;
        sync = true;
    }

    /** Returns a ConstructBlock by size. */
    public static ConstructBlock get(int size){
        if(size > maxBlockSize) throw new IllegalArgumentException("No. Don't place ConstructBlocks of size greater than " + maxBlockSize);
        return consBlocks[size - 1];
    }

    @Remote(called = Loc.server)
    public static void deconstructFinish(Tile tile, Block block, Unit builder){
        Team team = tile.team();
        tile.getLinkedTiles(t -> Events.fire(new BlockBuildEventTile(t, team, builder, block, Blocks.air, tile.build == null ? null : tile.build.config(), null))); // This line is a client thing FINISHME: Move this line into its own method to make merges less painful
        if(!headless && fogControl.isVisibleTile(Vars.player.team(), tile.x, tile.y)){
            block.breakEffect.at(tile.drawx(), tile.drawy(), block.size, block.mapColor);
            if(shouldPlay()) block.breakSound.at(tile, block.breakPitchChange ? calcPitch(false) : 1f);
        }
        Events.fire(new BlockBuildEndEvent(tile, builder, team, true, tile.build == null ? null : tile.build.config(), tile.block())); // FINISHME: This overrides the vanilla class; likely to cause issues w/ mods | Vanilla: Events.fire(new BlockBuildEndEvent(tile, builder, team, true, null));
        tile.remove();
    }

    /** Send a warning in chat when these blocks are broken/picked up/built over as they typically shouldn't be touched. */
    public static void breakWarning(Tile tile, Block block, Unit builder){
        if (!Core.settings.getBool("breakwarnings") || !tile.isCenter() || state.rules.infiniteResources || builder == null || !builder.isPlayer()) return; // Don't warn in sandbox for obvious reasons.

        if (breakWarnBlocks.contains(block) && Time.timeSinceMillis(tile.lastBreakWarn) > 10_000) { // FINISHME: Revise this, maybe do break warns per user?
            Timer.schedule(() -> ui.chatfrag.addMessage(Core.bundle.format("client.breakwarn", Strings.stripColors(builder.getPlayer().name), block.localizedName, tile.x, tile.y)), 0, 0, 2);
            tile.lastBreakWarn = Time.millis();
        }
    }

    @Remote(called = Loc.server)
    public static void constructFinish(Tile tile, Block block, @Nullable Unit builder, byte rotation, Team team, Object config){
        if(tile == null) return;

        float healthf = tile.build == null ? 1f : tile.build.healthf();
        Seq<Building> prev = tile.build instanceof ConstructBuild co ? co.prevBuild : null;
        Cons<Building> customConfig = tile.build instanceof ConstructBuild co ? co.localConfig : null;
        Block prevBlock = tile.block();

        if (block == null) {
            Events.fire(new BlockBreakEvent(tile, team, builder, tile.block(), tile.build == null ? null : tile.build.config(), rotation));
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

            //make sure block indexer knows it's damaged
            indexer.notifyHealthChanged(tile.build);
        }

        //last builder was this local client player, call placed()
        if(tile.build != null && !headless && builder == player.unit()){
            tile.build.playerPlaced(config);
        }

        if(tile.build != null && customConfig != null){
            customConfig.get(tile.build);
        }

        Events.fire(new BlockBuildEndEvent(tile, builder, team, false, config, prevBlock));

        if (tile.build instanceof ConstructBuild b) { // FINISHME: Does this even work?
            for (var item : b.prevBuild != null ? b.prevBuild : new Seq<Building>()) {
                Events.fire(new BlockBuildEventTile(item.tile, item.team, builder, item.block, block, item.config(), null));
            }
        }

        Fx.placeBlock.at(tile.drawx(), tile.drawy(), block.size);
        if(shouldPlay()) block.placeSound.at(tile, block.placePitchChange ? calcPitch(true) : 1f);
        if(fogControl.isVisibleTile(team, tile.x, tile.y)){
            Fx.placeBlock.at(tile.drawx(), tile.drawy(), block.size);
            if(shouldPlay()) block.placeSound.at(tile, block.placePitchChange ? calcPitch(true) : 1f);
        }

        if(tile.build instanceof ConstructBuild b && b.prevBuild != null){ // FINISHME: Does this even work?
            for(var item : b.prevBuild){
                Events.fire(new BlockBuildEventTile(item.tile, item.team, builder, item.block, block, item.config(), null));
            }
        }

        Events.fire(new BlockBuildEndEvent(tile, builder, team, false, config, prevBlock)); // FINISHME: Yet another case of a changed vanilla event. Vanilla: Events.fire(new BlockBuildEndEvent(tile, builder, team, false, config));
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
        Call.constructFinish(tile, block, builder, rotation, team, config);
        if(tile.build != null){
            tile.build.placed();
        }
    }

    @Override
    public boolean isHidden(){
        return true;
    }

    private static Seq<WarnBlock> warnBlocks;
    private static class WarnBlock { // FINISHME: Enums exist.
        final Block block;
        final int warnDistance;
        final int soundDistance;

        private WarnBlock(Block block, int warnDistance, int soundDistance) {
            this.block = block;
            this.warnDistance = warnDistance;
            this.soundDistance = soundDistance;
        }
    }
    static { Events.on(ClientLoadEvent.class, e -> updateWarnBlocks()); }
    public static void updateWarnBlocks() {
        warnBlocks = Seq.with(
            new WarnBlock(Blocks.thoriumReactor, Core.settings.getInt("reactorwarningdistance"), Core.settings.getInt("reactorsounddistance")),
            new WarnBlock(Blocks.incinerator, Core.settings.getInt("incineratorwarningdistance"), Core.settings.getInt("incineratorsounddistance")),
            new WarnBlock(Blocks.melter, Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance")),
            new WarnBlock(Blocks.oilExtractor, Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance")),
            new WarnBlock(Blocks.sporePress, Core.settings.getInt("slagwarningdistance"), Core.settings.getInt("slagsounddistance"))
        );
    }
    public class ConstructBuild extends Building{
        /** The recipe of the block that is being (de)constructed. Never null. */
        public Block current = Blocks.air;
        /** The block that used to be here. Never null. */
        public Block previous = Blocks.air;
        /** Buildings that previously occupied this location. */
        public @Nullable Seq<Building> prevBuild;
        /** Reference to its BuildPlan, for prioritization purposes. */
        public @Nullable BuildPlan attachedPlan;

        public float progress = 0;
        public float buildCost;
        public @Nullable Object lastConfig;
        public @Nullable Cons<Building> localConfig;
        public @Nullable Unit lastBuilder;
        public boolean wasConstructing, activeDeconstruct;
        public float constructColor;

        private float[] accumulator;
        private float[] totalAccumulator;

        private float lastProgress = 0f;

        @Override
        public String getDisplayName(){
            return Core.bundle.format("block.constructing", current.localizedName);
        }

        @Override
        public TextureRegion getDisplayIcon(){
            return current.fullIcon;
        }

        @Override
        public boolean checkSolid(){
            return current.solid || previous.solid;
        }

        @Override
        public Cursor getCursor(){
            return interactable(player.team()) ? SystemCursor.hand : SystemCursor.arrow;
        }

        @Override
        public void tapped(){
            //if the target is constructable, begin constructing
            if(current.isPlaceable()){
                if(control.input.buildWasAutoPaused && !control.input.isBuilding && player.isBuilder()){
                    control.input.isBuilding = true;
                }
                if(attachedPlan == null) attachedPlan = new BuildPlan(tile.x, tile.y, rotation, current, lastConfig);
                player.unit().addBuild(attachedPlan, attachedPlan.priority); // BuildPlan.priority and tail are inverted
                attachedPlan.priority ^= true;
            }
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress) return Mathf.clamp(progress);
            return super.sense(sensor);
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
            //auto-remove air blocks
            if(current == Blocks.air){
                remove();
            }

            constructColor = Mathf.lerpDelta(constructColor, activeDeconstruct ? 1f : 0f, 0.2f);
            activeDeconstruct = false;
        }

        @Override
        public void draw(){
            //do not draw air
            if(current == Blocks.air) return;

            if(previous != current && previous != Blocks.air && previous.fullIcon.found()){
                Draw.rect(previous.fullIcon, x, y, previous.rotate ? rotdeg() : 0);
            }

            Draw.draw(Layer.blockBuilding, () -> {
                Draw.color(Pal.accent, Pal.remove, constructColor);
                boolean noOverrides = current.regionRotated1 == -1 && current.regionRotated2 == -1;
                int i = 0;

                for(TextureRegion region : current.getGeneratedIcons()){
                    Shaders.blockbuild.region = region;
                    Shaders.blockbuild.time = Time.time;
                    Shaders.blockbuild.progress = progress;

                    Draw.rect(region, x, y, current.rotate && (noOverrides || current.regionRotated2 == i || current.regionRotated1 == i) ? rotdeg() : 0);
                    Draw.flush();
                    i ++;
                }

                Draw.color();
            });
        }

        public void construct(Unit builder, @Nullable Building core, float amount, Object config){
            wasConstructing = true;
            activeDeconstruct = false;

            lastBuilder = builder;

            lastConfig = config;

            if(current.requirements.length != accumulator.length || totalAccumulator.length != current.requirements.length){
                setConstruct(previous, current);
            }

            float maxProgress = core == null || team.rules().infiniteResources ? amount : checkRequired(core.items, amount, false);

            for(int i = 0; i < current.requirements.length; i++){
                int reqamount = Math.round(state.rules.buildCostMultiplier * current.requirements[i].amount);
                accumulator[i] += Math.min(reqamount * maxProgress, reqamount - totalAccumulator[i] + 0.00001f); //add min amount progressed to the accumulator
                totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * maxProgress, reqamount);
            }

            maxProgress = core == null || team.rules().infiniteResources ? maxProgress : checkRequired(core.items, maxProgress, true);

            progress = state.rules.infiniteResources ? 1 : Mathf.clamp(progress + maxProgress);
    
            // Warnings
            Player targetPlayer = ClientUtils.getPlayer(lastBuilder);
            if (targetPlayer != null) {
                WarnBlock wb = getWarnBlock();
                if (wb != null && wb.block == Blocks.thoriumReactor) Seer.INSTANCE.thoriumReactor(targetPlayer, tile.dst(targetPlayer));
            }
            
            handleBlockWarning(config);

            if(progress >= 1f || state.rules.infiniteResources){
                if(lastBuilder == null) lastBuilder = builder;
                if(!net.client()){
                    constructed(tile, current, lastBuilder, (byte)rotation, builder.team, config);
                }
            }
        }

        public void deconstruct(Unit builder, @Nullable CoreBuild core, float amount){
            //reset accumulated resources when switching modes
            if(wasConstructing){
                Arrays.fill(accumulator, 0);
                Arrays.fill(totalAccumulator, 0);
            }

            wasConstructing = false;
            activeDeconstruct = true;
            float deconstructMultiplier = state.rules.deconstructRefundMultiplier;

            lastBuilder = builder;


            ItemStack[] requirements = current.requirements;
            if(requirements.length != accumulator.length || totalAccumulator.length != requirements.length){
                setDeconstruct(current);
            }

            //make sure you take into account that you can't deconstruct more than there is deconstructed
            float clampedAmount = Math.min(amount, progress);

            for(int i = 0; i < requirements.length; i++){
                int reqamount = Math.round(state.rules.buildCostMultiplier * requirements[i].amount);
                accumulator[i] += Math.min(clampedAmount * deconstructMultiplier * reqamount, deconstructMultiplier * reqamount - totalAccumulator[i]); //add scaled amount progressed to the accumulator
                totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * clampedAmount * deconstructMultiplier, reqamount);

                int accumulated = (int)(accumulator[i]); //get amount

                if(clampedAmount > 0 && accumulated > 0){ //if it's positive, add it to the core
                    if(core != null && requirements[i].item.unlockedNowHost() && player != null){ //only accept items that are unlocked
                        int accepting = Math.min(accumulated, core.storageCapacity - core.items.get(requirements[i].item));
                        //transfer items directly, as this is not production.
                        core.items.add(requirements[i].item, accepting);
                        accumulator[i] -= accepting;
                    }else{
                        accumulator[i] -= accumulated;
                    }
                }
            }

            progress = Mathf.clamp(progress - amount);

            if(progress <= current.deconstructThreshold || state.rules.infiniteResources){
                Call.deconstructFinish(tile, this.current, lastBuilder);
            }
        }

        private float checkRequired(ItemModule inventory, float amount, boolean remove){
            float maxProgress = amount;

            for(int i = 0; i < current.requirements.length; i++){
                int sclamount = Math.round(state.rules.buildCostMultiplier * current.requirements[i].amount);
                int required = (int)(accumulator[i]); //calculate items that are required now

                if(inventory.get(current.requirements[i].item) == 0 && sclamount != 0){
                    maxProgress = 0f;
                }else if(required > 0){ //if this amount is positive...
                    //calculate how many items it can actually use
                    int maxUse = Math.min(required, inventory.get(current.requirements[i].item));
                    //get this as a fraction
                    float fraction = maxUse / (float)required;

                    //move max progress down if this fraction is less than 1
                    maxProgress = Math.min(maxProgress, maxProgress * fraction);

                    accumulator[i] -= maxUse;

                    //remove stuff that is actually used
                    if(remove){
                        inventory.remove(current.requirements[i].item, maxUse);
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
            if(block == null) return;

            this.constructColor = 0f;
            this.wasConstructing = true;
            this.current = block;
            this.previous = previous;
            this.buildCost = block.buildCost * state.rules.buildCostMultiplier;
            this.accumulator = new float[block.requirements.length];
            this.totalAccumulator = new float[block.requirements.length];
            pathfinder.updateTile(tile);
        }

        public void setDeconstruct(Block previous){
            if(previous == null) return;

            this.constructColor = 1f;
            this.wasConstructing = false;
            this.previous = previous;
            this.progress = 1f;
            this.current = previous;
            this.buildCost = previous.buildCost * state.rules.buildCostMultiplier;
            this.accumulator = new float[previous.requirements.length];
            this.totalAccumulator = new float[previous.requirements.length];
            pathfinder.updateTile(tile);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.s(previous.id);
            write.s(current.id);

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
            if(rid != -1) current = content.block(rid);

            if(previous == null) previous = Blocks.air;
            if(current == null) current = Blocks.air;

            buildCost = current.buildCost * state.rules.buildCostMultiplier;
        }

        public boolean shouldDisplayWarning(){
            return wasConstructing && closestCore() != null && lastBuilder != null
                    && player != null && team == player.team() && progress != lastProgress
                    && lastBuilder != player.unit() && getWarnBlock() != null;
        }
        public void blockWarning(Object config) { // FINISHME: Account for non player building stuff. This method is also horrid
            if (!wasConstructing || closestCore() == null || lastBuilder == null || team != player.team() || progress == lastProgress || !lastBuilder.isPlayer())
                return;
            var wb = warnBlocks.find(b -> b.block == current);
            if (wb != null) {
                lastBuilder.drawBuildPlans(); // Draw their build plans FINISHME: This is kind of dumb because it only draws while they are building one of these blocks rather than drawing whenever there is one in the queue
                AtomicInteger distance = new AtomicInteger(Integer.MAX_VALUE); // FINISHME: Do this from the edges instead, checking all blocks is more expensive than it needs to be
                closestCore().proximity.each(e -> e instanceof StorageBlock.StorageBuild, block -> block.tile.getLinkedTiles(t -> this.tile.getLinkedTiles(ti -> distance.set(Math.min(World.toTile(t.dst(ti)), distance.get()))))); // This stupidity finds the smallest distance between vaults on the closest core and the block being built
                closestCore().tile.getLinkedTiles(t -> this.tile.getLinkedTiles(ti -> distance.set(Math.min(World.toTile(t.dst(ti)), distance.get())))); // This stupidity checks the distance to the core as well just in case it ends up being shorter

                if (wb.warnDistance == 101 || distance.get() <= wb.warnDistance) {
                    String format = Core.bundle.format("client.blockwarn", Strings.stripColors(lastBuilder.getPlayer().name), current.localizedName, tile.x, tile.y, distance.get());
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
                    toast.touchable = Touchable.enabled;
                    toast.clicked(() -> Spectate.INSTANCE.spectate(ClientVars.lastSentPos.cpy().scl(tilesize)));
                    ClientVars.lastSentPos.set(tile.x, tile.y);
                }
            }
        }


        public ConstructBlock.WarnBlock getWarnBlock(){
            return warnBlocks.find(b -> b.block == current);
        }

        /** Returns the smallest distance to the core and/or its connected vaults.*/
        public int distanceToGreaterCore(){
            int lowestDistance = Integer.MAX_VALUE;
            //Loop through the core and all connected storage
            // BALA WARNING: I dont know whether replacing .and with .add was the correct move but I hope it is
            for(Building building : closestCore().proximity.copy().add(closestCore())) {
                if (building instanceof StorageBlock.StorageBuild || building instanceof CoreBuild) {
                    lowestDistance = Math.min(World.toTile(building.tile.dst(this.tile)), lowestDistance);
                }
            }
            return lowestDistance;
        }

        public void handleBlockWarning(Object config) {
            //refactored by BalaM314
            if (!shouldDisplayWarning()) return;
            var warnBlock = getWarnBlock();
            lastBuilder.drawBuildPlans(); // Draw their build plans FINISHME: This is kind of dumb because it only draws while they are building one of these blocks rather than drawing whenever there is one in the queue
            int distance = distanceToGreaterCore();

            // Play warning sound (only played when no reactor has been built for 10s)
            if (warnBlock.soundDistance == 101 || distance <= (warnBlock.soundDistance)) {
                if (Time.timeSinceMillis(lastWarn) > 10 * 1000) Sounds.corexplode.play(.3f * (float)Core.settings.getInt("sfxvol") / 100.0F);
                lastWarn = Time.millis();
            }

            if (warnBlock.warnDistance == 101 || distance <= warnBlock.warnDistance) {
                String toastMessage = Core.bundle.format(
                        "client.blockwarn", ClientUtils.getName(lastBuilder),
                        current.localizedName, tile.x, tile.y, distance
                );
                String toastSubtitle = String.format("%2d%% completed.", Mathf.round(progress * 100));
                if (toast == null || toast.parent == null) {
                    toast = new Toast(2f, 0f);
                } else {
                    toast.clearChildren();
                }
                toast.setFadeAfter(2f);
                toast.add(new Label(toastMessage));
                toast.row();
                toast.add(new Label(toastSubtitle, monoLabel));
                toast.touchable = Touchable.enabled;
                toast.clicked(() -> Spectate.INSTANCE.spectate(ClientVars.lastSentPos.cpy().scl(tilesize)));
                ClientVars.lastSentPos.set(tile.x, tile.y);
            }

            if (
                    lastProgress == 0 && Core.settings.getBool("removecorenukes")
                    && state.rules.reactorExplosions && current instanceof NuclearReactor
                    && !lastBuilder.isLocal() && distance <= 21
            ) { // Automatically remove reactors within explosion radiusof core
                Call.buildingControlSelect(player, closestCore());
                Timer.schedule(() -> player.unit().plans.add(
                        new BuildPlan(tile.x, tile.y)
                ), net.client() ? netClient.getPing()/1000f+.3f : 0);
            }
            lastProgress = progress;
        }
    }
}
