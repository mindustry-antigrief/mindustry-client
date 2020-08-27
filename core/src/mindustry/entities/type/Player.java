package mindustry.entities.type;

import arc.*;
import arc.input.*;
import arc.struct.Queue;
import mindustry.ai.pathfinding.*;
import mindustry.annotations.Annotations.*;
import arc.struct.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.ArcAnnotate.*;
import arc.util.pooling.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.ContentType;
import mindustry.entities.*;
import mindustry.entities.traits.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.io.*;
import mindustry.net.Administration.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.BuildBlock.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.defense.turrets.Turret.*;
import mindustry.world.blocks.units.*;

import java.io.*;
import java.time.*;
import java.util.*;

import static mindustry.Vars.*;

public class Player extends Unit implements BuilderMinerTrait, ShooterTrait{
    public static final int timerSync = 2;
    public static final int timerAbility = 3;
    private static final int timerShootLeft = 0;
    private static final int timerShootRight = 1;
    private static final float liftoffBoost = 0.2f;

    private static final Rect rect = new Rect();

    //region instance variables

    public float baseRotation;
    public float pointerX, pointerY;
    public String name = "noname";
    public @Nullable String uuid, usid;
    public boolean isAdmin, isTransferring, isShooting, isBoosting, isMobile, isTyping, isBuilding = true;
    public boolean buildWasAutoPaused = false;
    public float boostHeat, shootHeat, destructTime;
    public boolean achievedFlight;
    public Color color = new Color();
    public Mech mech = Mechs.starter;
    public SpawnerTrait spawner, lastSpawner;
    public int respawns;

    public @Nullable NetConnection con;
    public boolean isLocal = false;
    public Interval timer = new Interval(6);
    public TargetTrait target;
    public TargetTrait moveTarget;

    public @Nullable String lastText;
    public float textFadeTime;

    private float walktime, itemtime;
    private final Queue<BuildRequest> placeQueue = new Queue<>();
    private Tile mining;
    private final Vec2 movement = new Vec2();
    private boolean moved;
    public Array<InteractionLogItem> log = new Array<>();
    public ObjectSet<Item> toMine = ObjectSet.with(Items.lead, Items.copper, Items.titanium, Items.thorium);
    private Integer buildTarget;
    public String readableName = "";
    public UnitState previousState;
    private final ObjectSet<Block> bannedBlocks = new ObjectSet<>();
    {
        bannedBlocks.addAll(Blocks.thoriumReactor, Blocks.impactReactor, Blocks.differentialGenerator);
    }
    protected StateMachine state2 = new StateMachine();
    public final UnitState
    normal = new UnitState(){

        @Override
        public String getName(){
            return "Normal";
        }

        @Override
        public void entered(){
            clearBuilding();
            buildTarget = null;
            setMineTile(null);
            followingWaypoints = false;
            notDone.clear();
        }

        @Override
        public void exited(){
            previousState = normal;
        }
    },
    mine = new UnitState(){

        @Override
        public String getName(){
            return "Draug";
        }

        @Override
        public Color getColor(){
            return Color.valueOf("d3ddff");
        }

        @Override
        public void update(){
            TileEntity core = player.getClosestCore();
            if(core == null){
                return;
            }
//            TileEntity tile = (TileEntity)core;
//            for(Item i : new Item[](Items.copper, Items.lead, Items.titanium, Items.thorium))
            Array<Item> items = new Array<>();
            for(Item i : toMine){
                if(i.hardness <= player.mech.drillPower){
                    items.add(i);
                }
            }
            Item targetItem = Structs.findMin(items, indexer::hasOre, (a, b) -> -Integer.compare(core.items.get(a), core.items.get(b)));
            target = indexer.findClosestOre(x, y, targetItem);
            if(target == null){
                return;
            }
            if(notDone.size == 0 && target != getMineTile()){
                navigateTo(target.getX(), target.getY());
                setMineTile((Tile)target);
            }
            if(item.amount >= 1){
                if(core.tile.block().acceptStack(item.item, item.amount, core.tile, Player.this) > 0){
                    Call.transferInventory(Player.this, core.tile);
                }
            }
            if(healthf() < 0.5f){
                setState(heal);
            }
        }

        @Override
        public void exited(){
            previousState = mine;
        }
    },
    build = new UnitState(){

        @Override
        public String getName(){
            return "Phantom";
        }

        @Override
        public Color getColor(){
            return Pal.accent;
        }

        @Override
        public void exited(){
            previousState = build;
        }

        @Override
        public void update(){
            if(player.healthf() < 0.5f){
                setState(heal);
            }
//            System.out.println(world.tile(buildTarget));
//            System.out.println("A");
            try{
                if((buildTarget == null || !(world.tile(buildTarget).block() instanceof BuildBlock) || new Rand().chance(1 / 20f)) && new Rand().chance(0.5)){
                    updateBuildTarget();
                }else{
                    if(buildTarget != null){
                        int x = Pos.x(buildTarget);
                        int y = Pos.y(buildTarget);
                        if(Mathf.dst(x * 8, y * 8, player.x, player.y) > placeDistance * 0.8){
                            if(!followingWaypoints){
                                player.navigateTo(x * 8, y * 8);
                            }
                        }else{
                            BuildEntity buildTargetEntity = world.tile(buildTarget).entity instanceof BuildEntity ? world.tile(buildTarget).ent() : null;
//                        if(player.buildQueue().isEmpty()){
                            if(buildTargetEntity != null){
//                                building = new BuildRequest(
//                                    Pos.x(buildTarget),
//                                    Pos.y(buildTarget),
//                                    buildTargetEntity.tile.rotation(),
//                                    buildTargetEntity.block
//                                );
//                                world.tile(buildTarget)
                                Tile tile = world.tile(buildTarget);
                                BuildRequest b = new BuildRequest(tile.x, tile.y, tile.rotation(), buildTargetEntity.cblock, Core.input.keyDown(KeyCode.SHIFT_LEFT) || Core.input.keyDown(KeyCode.SHIFT_RIGHT));
                                b.breaking = buildTargetEntity.isBreaking;
                                if(player.buildQueue().size < 10){
                                    player.addBuildRequest(b, false);
                                }
                                followingWaypoints = false;
                                notDone.clear();
//                                System.out.println("AAA");
//                                player.buildQueue().first().breaking = buildTargetEntity.isBreaking;
//                                player.placeQueue.clear();
//                                player.placeQueue.addFirst(building);
                            }
//                        }//else if(!player.isBuilding){
//                            player.buildQueue().clear();
//                        }
                        }
                    }
                }
            }catch(NullPointerException ignored){} // I don't care anymore
        }
    },
    heal = new UnitState(){

        @Override
        public String getName(){
            return "Heal";
        }

        @Override
        public Color getColor(){
            return Pal.health;
        }

        @Override
        public void exited(){
            previousState = heal;
        }

        @Override
        public void entered(){
            repairPoint = 0;
            navigateToRepairPoint();
        }

        private int repairPoint = 0;

        private void navigateToRepairPoint(){
            Array<Tile> repairPoints = new Array<>();
            for(Tile[] tiles : world.getTiles()){
                for(Tile tile : tiles){
                    if(tile.block() == Blocks.repairPoint && tile.getTeam() == player.team){
                        if(tile.entity.power.status > 0.9f){
                            repairPoints.add(tile);
                        }
                    }
                }
            }
            if(repairPoints.size > 0){
                Tile closest = Geometry.findClosest(player.getX(), player.getY(), repairPoints);
                player.navigateTo(closest.getX(), closest.getY());
                repairPoint = closest.pos();
            }
        }

        @Override
        public void update(){
            if(player.healthf() < 1f){
                Tile tile = world.tile(repairPoint);
                boolean refresh = false;
                if(tile == null){
                    refresh = true;
                }else if(tile.block() == null){
                    refresh = true;
                }else if(tile.block() != Blocks.repairPoint){
                    refresh = true;
                }else if(tile.entity.power.status < 0.5f){
                    refresh = true;
                }else if(!followingWaypoints && player.dst(tile) > ((RepairPoint)Blocks.repairPoint).repairRadius){
                    refresh = true;
                }
                if(refresh){
                    navigateToRepairPoint();
                }
            }else{
                setState(previousState);
            }
        }
    };
    {
        previousState = normal;
    }

    private void updateBuildTarget(){
        Array<Tile> blocks = new Array<>();
        for(Tile[] tiles : world.getTiles()){
            for(Tile tile : tiles){
                if(tile.block() instanceof BuildBlock){
                    if(((BuildEntity)tile.entity).cblock != null){
                        if(!bannedBlocks.contains(((BuildEntity)tile.entity).cblock)){
                            blocks.add(tile);
                        }
                    }
                }
            }
        }
        if(blocks.size > 0){
            buildTarget = Geometry.findClosest(player.getX(), player.getY(), blocks).pos();
        }
    }

    public void setBuilding(){
        setState(build);
    }

    public void setState(UnitState newState){
        state2.set(newState);
    }

    public UnitState getState(){
        return state2.current();
    }

    //endregion

    public void navigateTo(float drawX, float drawY){
        try{
            drawX = Mathf.clamp(drawX, 0, world.width() * 8);
            drawY = Mathf.clamp(drawY, 0, world.height() * 8);
            Array<TurretEntity> turrets = new Array<>();
            Array<TurretPathfindingEntity> dropZones = new Array<>();
            for(Tile[] tiles : world.getTiles()){
                for(Tile tile : tiles){
                    if(tile.block() instanceof Turret){
                        turrets.add((TurretEntity)tile.entity);
                    }else if(tile.block() == Blocks.spawn){
                        dropZones.add(new TurretPathfindingEntity(tile.x, tile.y, ((Turret)tile.block()).range));
                    }
                }
            }
            followingWaypoints = true;
            repeatWaypoints = false;
            notDone.clear();
            Array<int[]> points = AStar.findPathTurretsDropZone(turrets, this.x, this.y, drawX, drawY, world.width(), world.height(), team, dropZones);
            if(points != null){
                for(int[] point : points){
                    notDone.addLast(new Waypoint(point[0] * 8, point[1] * 8));
                }
            }
        }catch(NullPointerException ignored){} //I PROMISE IT'S FINE
    }

    //region unit and event overrides, utility methods

    @Remote(targets = Loc.server, called = Loc.server)
    public static void onPlayerDeath(Player player){
        if(player == null) return;

        player.dead = true;
        player.placeQueue.clear();
        player.onDeath();
    }

    @Override
    public float getDamageMultipler(){
        return status.getDamageMultiplier() * state.rules.playerDamageMultiplier;
    }

    @Override
    public void hitbox(Rect rect){
        rect.setSize(mech.hitsize).setCenter(x, y);
    }

    @Override
    public void hitboxTile(Rect rect){
        rect.setSize(mech.hitsize * 2f / 3f).setCenter(x, y);
    }

    @Override
    public void onRespawn(Tile tile){
        velocity.setZero();
        boostHeat = 1f;
        achievedFlight = true;
        rotation = 90f;
        baseRotation = 90f;
        dead = false;
        spawner = null;
        respawns --;
        Sounds.respawn.at(tile);

        setNet(tile.drawx(), tile.drawy());
        clearItem();
        heal();
    }

    @Override
    public boolean offloadImmediately(){
        return true;
    }

    @Override
    public TypeID getTypeID(){
        return TypeIDs.player;
    }

    @Override
    public void move(float x, float y){
        if(!mech.flying){
            collisions.move(this, x, y);
        }else{
            moveBy(x, y);
        }
    }

    @Override
    public float drag(){
        return mech.drag;
    }

    @Override
    public Interval getTimer(){
        return timer;
    }

    @Override
    public int getShootTimer(boolean left){
        return left ? timerShootLeft : timerShootRight;
    }

    @Override
    public Weapon getWeapon(){
        return mech.weapon;
    }

    @Override
    public float getMinePower(){
        return mech.mineSpeed;
    }

    @Override
    public TextureRegion getIconRegion(){
        return mech.icon(Cicon.full);
    }

    @Override
    public int getItemCapacity(){
        return mech.itemCapacity;
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 1){
            baseRotation = interpolator.values[1];
        }

        if(interpolator.target.dst(interpolator.last) > 1f){
            walktime += Time.delta();
        }
    }

    @Override
    public float getBuildPower(Tile tile){
        return mech.buildPower;
    }

    @Override
    public float maxHealth(){
        return mech.health * state.rules.playerHealthMultiplier;
    }

    @Override
    public Tile getMineTile(){
        return mining;
    }

    @Override
    public void setMineTile(Tile tile){
        this.mining = tile;
    }

    @Override
    public boolean canMine(Item item){
        return item.hardness <= mech.drillPower;
    }

    @Override
    public float calculateDamage(float amount){
        return amount * Mathf.clamp(1f - (status.getArmorMultiplier() + mech.getExtraArmor(this)) / 100f);
    }

    @Override
    public void added(){
        baseRotation = 90f;
    }

    @Override
    public float mass(){
        return mech.mass;
    }

    @Override
    public boolean isFlying(){
        return mech.flying || boostHeat > liftoffBoost;
    }

    @Override
    public void damage(float amount){
        hitTime = hitDuration;
        if(!net.client()){
            health -= calculateDamage(amount);
        }

        if(health <= 0 && !dead){
            Call.onPlayerDeath(this);
        }
    }

    @Override
    public void set(float x, float y){
        this.x = x;
        this.y = y;
    }

    @Override
    public float maxVelocity(){
        return mech.maxSpeed;
    }

    @Override
    public Queue<BuildRequest> buildQueue(){
        Array<BuildRequest> requests = new Array<>();
        placeQueue.forEach(requests::add);
        requests.sort((req) -> req.priority? 0f : 1f);
        placeQueue.clear();
        requests.forEach(placeQueue::addLast);
        return placeQueue;
    }

    @Override
    public String toString(){
        return "Player{" + name + ", mech=" + mech.name + ", id=" + id + ", local=" + isLocal + ", " + x + ", " + y + "}";
    }

    @Override
    public EntityGroup targetGroup(){
        return playerGroup;
    }

    public void setTeam(Team team){
        this.team = team;
    }

    //endregion

    //region draw methods

    @Override
    public float drawSize(){
        return isLocal ? Float.MAX_VALUE : 40 + placeDistance;
    }

    @Override
    public void drawShadow(float offsetX, float offsetY){
        float scl = mech.flying ? 1f : boostHeat / 2f;

        Draw.rect(getIconRegion(), x + offsetX * scl, y + offsetY * scl, rotation - 90);
    }

    @Override
    public void draw(){
        if(dead) return;

        if(!movement.isZero() && moved && !state.isPaused()){
            walktime += movement.len() * getFloorOn().speedMultiplier * 2f;
            baseRotation = Mathf.slerpDelta(baseRotation, movement.angle(), 0.13f);
        }

        float ft = Mathf.sin(walktime, 6f, 2f) * (1f - boostHeat);

        Floor floor = getFloorOn();

        Draw.color();
        Draw.mixcol(Color.white, hitTime / hitDuration);

        if(!mech.flying){
            if(floor.isLiquid){
                Draw.color(Color.white, floor.color, 0.5f);
            }

            float boostTrnsY = -boostHeat * 3f;
            float boostTrnsX = boostHeat * 3f;
            float boostAng = boostHeat * 40f;

            for(int i : Mathf.signs){
                Draw.rect(mech.legRegion,
                x + Angles.trnsx(baseRotation, ft * i + boostTrnsY, -boostTrnsX * i),
                y + Angles.trnsy(baseRotation, ft * i + boostTrnsY, -boostTrnsX * i),
                mech.legRegion.getWidth() * i * Draw.scl,
                (mech.legRegion.getHeight() - Mathf.clamp(ft * i, 0, 2)) * Draw.scl,
                baseRotation - 90 + boostAng * i);
            }

            Draw.rect(mech.baseRegion, x, y, baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.color(Color.white, floor.color, drownTime);
        }else{
            Draw.color(Color.white);
        }

        Draw.rect(mech.region, x, y, rotation - 90);

        mech.draw(this);

        for(int i : Mathf.signs){
            float tra = rotation - 90, trY = -mech.weapon.getRecoil(this, i > 0) + mech.weaponOffsetY;
            float w = i > 0 ? -mech.weapon.region.getWidth() : mech.weapon.region.getWidth();
            Draw.rect(mech.weapon.region,
            x + Angles.trnsx(tra, (mech.weaponOffsetX + mech.spreadX(this)) * i, trY),
            y + Angles.trnsy(tra, (mech.weaponOffsetX + mech.spreadX(this)) * i, trY),
            w * Draw.scl,
            mech.weapon.region.getHeight() * Draw.scl,
            rotation - 90);
        }

        Draw.reset();
    }

    public void drawBackItems(){
        drawBackItems(itemtime, isLocal);
    }

    @Override
    public void drawStats(){
        mech.drawStats(this);
    }

    @Override
    public void drawOver(){
        if(dead) return;

        if(isBuilding() && isBuilding){
            if(!state.isPaused()){
                drawBuilding();
            }
        }else{
            drawMining();
        }
    }

    @Override
    public void drawUnder(){
        if(dead) return;

        float size = mech.engineSize * (mech.flying ? 1f : boostHeat);
        Draw.color(mech.engineColor);
        Fill.circle(x + Angles.trnsx(rotation + 180, mech.engineOffset), y + Angles.trnsy(rotation + 180, mech.engineOffset),
        size + Mathf.absin(Time.time(), 2f, size / 4f));

        Draw.color(Color.white);
        Fill.circle(x + Angles.trnsx(rotation + 180, mech.engineOffset - 1f), y + Angles.trnsy(rotation + 180, mech.engineOffset - 1f),
        (size + Mathf.absin(Time.time(), 2f, size / 4f)) / 2f);
        Draw.color();
    }

    public void drawName(){
        BitmapFont font = Fonts.def;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        final float nameHeight = 11;
        final float textHeight = 15;

        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f / Scl.scl(1f));
        layout.setText(font, name);

        if(!isLocal){
            Draw.color(0f, 0f, 0f, 0.3f);
            Fill.rect(x, y + nameHeight - layout.height / 2, layout.width + 2, layout.height + 3);
            Draw.color();
            font.setColor(color);
            font.draw(name, x, y + nameHeight, 0, Align.center, false);

            if(isAdmin){
                float s = 3f;
                Draw.color(color.r * 0.5f, color.g * 0.5f, color.b * 0.5f, 1f);
                Draw.rect(Icon.adminSmall.getRegion(), x + layout.width / 2f + 2 + 1, y + nameHeight - 1.5f, s, s);
                Draw.color(color);
                Draw.rect(Icon.adminSmall.getRegion(), x + layout.width / 2f + 2 + 1, y + nameHeight - 1f, s, s);
            }
        }

        if(Core.settings.getBool("playerchat") && ((textFadeTime > 0 && lastText != null) || isTyping)){
            String text = textFadeTime <= 0 || lastText == null ? "[LIGHT_GRAY]" + Strings.animated(Time.time(), 4, 15f, ".") : lastText;
            float width = 100f;
            float visualFadeTime = 1f - Mathf.curve(1f - textFadeTime, 0.9f);
            font.setColor(1f, 1f, 1f, textFadeTime <= 0 || lastText == null ? 1f : visualFadeTime);

            layout.setText(font, text, Color.white, width, Align.bottom, true);

            Draw.color(0f, 0f, 0f, 0.3f * (textFadeTime <= 0 || lastText == null  ? 1f : visualFadeTime));
            Fill.rect(x, y + textHeight + layout.height - layout.height/2f, layout.width + 2, layout.height + 3);
            font.draw(text, x - width/2f, y + textHeight + layout.height, width, Align.center, true);
        }

        Draw.reset();
        Pools.free(layout);
        font.getData().setScale(1f);
        font.setColor(Color.white);
        font.setUseIntegerPositions(ints);
    }

    /** Draw all current build requests. Does not draw the beam effect, only the positions. */
    public void drawBuildRequests(){
        if(!isLocal) return;

        for(BuildRequest request : buildQueue()){
            if(request.progress > 0.01f || (buildRequest() == request && request.initialized && (dst(request.x * tilesize, request.y * tilesize) <= placeDistance || state.isEditor()))) continue;

            request.animScale = 1f;
            if(request.breaking){
                control.input.drawBreaking(request);
            }else{
                request.block.drawRequest(request, control.input.allRequests(),
                    Build.validPlace(getTeam(), request.x, request.y, request.block, request.rotation) || control.input.requestMatches(request));
            }
        }

        Draw.reset();
    }

    //endregion

    //region update methods

//    @Override
//    public void updateBuilding(){
//        float finalPlaceDst = state.rules.infiniteResources ? Float.MAX_VALUE : placeDistance;
//        Unit unit = (Unit)this;
//        Iterator<BuildRequest> it = buildQueue().iterator();
//        while(it.hasNext()){
//            BuildRequest req = it.next();
//            log.addFirst(req);
//            Tile tile = world.tile(req.x, req.y);
//            if(tile == null || (req.breaking && tile.block() == Blocks.air) || (!req.breaking && (tile.rotation() == req.rotation || !req.block.rotate) && tile.block() == req.block)){
//                it.remove();
//            }
//        }
//
//        TileEntity core = unit.getClosestCore();
//
//        //nothing to build.
//        if(buildRequest() == null) return;
//
//        //find the next build request
//        if(buildQueue().size > 1){
//            int total = 0;
//            BuildRequest req;
//            while((dst((req = buildRequest()).tile()) > finalPlaceDst || shouldSkip(req, core)) && total < buildQueue().size){
//                buildQueue().removeFirst();
//                buildQueue().addLast(req);
//                total++;
//            }
//        }
//
//        BuildRequest current = buildRequest();
//
//        if(dst(current.tile()) > finalPlaceDst) return;
//
//        Tile tile = world.tile(current.x, current.y);
//
//        if(!(tile.block() instanceof BuildBlock)){
//            if(!current.initialized && canCreateBlocks() && !current.breaking && Build.validPlace(getTeam(), current.x, current.y, current.block, current.rotation)){
//                Call.beginPlace(getTeam(), current.x, current.y, current.block, current.rotation);
//            }else if(!current.initialized && canCreateBlocks() && current.breaking && Build.validBreak(getTeam(), current.x, current.y)){
//                Call.beginBreak(getTeam(), current.x, current.y);
//            }else{
//                buildQueue().removeFirst();
//                return;
//            }
//        }else if(tile.getTeam() != getTeam()){
//            buildQueue().removeFirst();
//            return;
//        }
//
//        if(tile.entity instanceof BuildEntity && !current.initialized){
//            Core.app.post(() -> Events.fire(new BuildSelectEvent(tile, unit.getTeam(), this, current.breaking)));
//            current.initialized = true;
//        }
//
//        //if there is no core to build with or no build entity, stop building!
//        if((core == null && !state.rules.infiniteResources) || !(tile.entity instanceof BuildEntity)){
//            return;
//        }
//
//        //otherwise, update it.
//        BuildEntity entity = tile.ent();
//
//        if(entity == null){
//            return;
//        }
//
//        if(unit.dst(tile) <= finalPlaceDst){
//            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(entity), 0.4f);
//        }
//
//        if(current.breaking){
//            entity.deconstruct(unit, core, 1f / entity.buildCost * Time.delta() * getBuildPower(tile) * state.rules.buildSpeedMultiplier);
//        }else{
//            if(entity.construct(unit, core, 1f / entity.buildCost * Time.delta() * getBuildPower(tile) * state.rules.buildSpeedMultiplier, current.hasConfig)){
//                if(current.hasConfig){
//                    Call.onTileConfig(null, tile, current.config);
//                }
//            }
//        }
//
//        current.stuck = Mathf.equal(current.progress, entity.progress);
//        current.progress = entity.progress;
//    }

    @Override
    public void updateMechanics(){
//        for(BuildRequest req : buildQueue()){
//            if(undid_hashes.contains(req.hashCode())){
//                continue;
//            }
//            InteractionLogItem item = new InteractionLogItem(req);
////            if(!log.contains(item)){
////                log.add(item);
////            }
//        }
        if(isBuilding){
            updateBuilding();
        }

        //mine only when not building
        if(buildRequest() == null || !isBuilding){
            updateMining();
        }
    }

    @Override
    public void update(){
//        name = Double.toString(new Rand().nextDouble());
        state2.update();
        if(followingWaypoints){
            if(notDone.size == 0 || player.dead){
                waypointEndTime = Clock.systemUTC().millis();
                if(repeatWaypoints){
                    waypointFollowStartTime = Clock.systemUTC().millis();
                    notDone.clear();
                    for(Waypoint w : waypoints){
                        notDone.addFirst(w);
                    }
                }else{
                    notDone.clear();
                    followingWaypoints = false;
                }
            }else{
                if(Clock.systemUTC().millis() - waypointEndTime > 1000){
                    if(notDone.last().goTo()){
                        notDone.removeLast();
                        followingWaypoints = !autoBuild;
                    }
                }
            }
//        }
        }else if(notDone.size > 0 && autoBuild){
            if(notDone.last().goTo()){
                notDone.removeLast();
            }
        }

        hitTime -= Time.delta();
        textFadeTime -= Time.delta() / (60 * 5);
        itemtime = Mathf.lerpDelta(itemtime, Mathf.num(item.amount > 0), 0.1f);

        if(Float.isNaN(x) || Float.isNaN(y)){
            velocity.set(0f, 0f);
            x = 0;
            y = 0;
            setDead(true);
        }

        if(netServer.isWaitingForPlayers()){
            setDead(true);
        }

        if(!isDead() && isOutOfBounds()){
            destructTime += Time.delta();

            if(destructTime >= boundsCountdown){
                kill();
            }
        }else{
            destructTime = 0f;
        }

        if(!isDead() && isFlying()){
            loops.play(Sounds.thruster, this, Mathf.clamp(velocity.len() * 2f) * 0.3f);
        }

        BuildRequest request = buildRequest();
        if(isBuilding() && isBuilding && request.tile() != null && (request.tile().withinDst(x, y, placeDistance) || state.isEditor())){
            loops.play(Sounds.build, request.tile(), 0.75f);
        }

        if(isDead()){
            isBoosting = false;
            boostHeat = 0f;
            if(respawns > 0 || !state.rules.limitedRespawns){
                updateRespawning();
            }
            return;
        }else{
            spawner = null;
        }

        if(isLocal || net.server()){
            avoidOthers();
        }

        Tile tile = world.tileWorld(x, y);

        boostHeat = Mathf.lerpDelta(boostHeat, (tile != null && tile.solid()) || (isBoosting && ((!movement.isZero() && moved) || !isLocal)) ? 1f : 0f, 0.08f);
        shootHeat = Mathf.lerpDelta(shootHeat, isShooting() ? 1f : 0f, 0.06f);
        mech.updateAlt(this); //updated regardless

        if(boostHeat > liftoffBoost + 0.1f){
            achievedFlight = true;
        }

        if(boostHeat <= liftoffBoost + 0.05f && achievedFlight && !mech.flying){
            if(tile != null){
                if(mech.shake > 1f){
                    Effects.shake(mech.shake, mech.shake, this);
                }
                Effects.effect(Fx.unitLand, tile.floor().color, x, y, tile.floor().isLiquid ? 1f : 0.5f);
            }
            mech.onLand(this);
            achievedFlight = false;
        }

        if(!isLocal){
            interpolate();
            updateMechanics(); //building happens even with non-locals
            status.update(this); //status effect updating also happens with non locals for effect purposes
            updateVelocityStatus(); //velocity too, for visual purposes

            if(net.server()){
                updateShooting(); //server simulates player shooting
            }
            return;
        }else if(world.isZone()){
            //unlock mech when used
            data.unlockContent(mech);
        }

        if(control.input instanceof MobileInput){
            updateTouch();
        }else{
            if(notDone.size > 0){
                updateKeyboardNoMovement();
                if(notDone.size > 0){
                    updateTarget(notDone.last().x, notDone.last().y);
                }
            }else{
                updateKeyboard();
            }
        }

        isTyping = ui.chatfrag.shown();

        updateMechanics();

        if(!mech.flying){
            clampPosition();
        }
    }

    protected void updateKeyboardNoMovement(){
        Tile tile = world.tileWorld(x, y);

        isBoosting = !mech.flying;

        //if player is in solid block
        if(tile != null && tile.solid()){
            isBoosting = true;
        }

        Vec2 vec = Core.input.mouseWorld(control.input.getMouseX(), control.input.getMouseY());
        pointerX = vec.x;
        pointerY = vec.y;
        updateShooting();
    }

    protected void updateKeyboard(){
        Tile tile = world.tileWorld(x, y);
        boolean canMove = !Core.scene.hasKeyboard() || ui.minimapfrag.shown();

        isBoosting = Core.input.keyDown(Binding.dash) && !mech.flying;

        //if player is in solid block
        if(tile != null && tile.solid()){
            isBoosting = true;
        }

        float speed = isBoosting && !mech.flying ? mech.boostSpeed : mech.speed;

        if(mech.flying){
            //prevent strafing backwards, have a penalty for doing so
            float penalty = 0.2f; //when going 180 degrees backwards, reduce speed to 0.2x
            speed *= Mathf.lerp(1f, penalty, Angles.angleDist(rotation, velocity.angle()) / 180f);
        }

        movement.setZero();

        float xa = Core.input.axis(Binding.move_x);
        float ya = Core.input.axis(Binding.move_y);
        if(!(Core.scene.getKeyboardFocus() instanceof TextField)){
            movement.y += ya * speed;
            movement.x += xa * speed;
        }

        if(Core.input.keyDown(Binding.mouse_move)){
            movement.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * 0.005f, -1, 1) * speed;
            movement.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * 0.005f, -1, 1) * speed;
        }

        Vec2 vec = Core.input.mouseWorld(control.input.getMouseX(), control.input.getMouseY());
        pointerX = vec.x;
        pointerY = vec.y;
        updateShooting();

        movement.limit(speed).scl(Time.delta());

        if(canMove){
            velocity.add(movement.x, movement.y);
        }else{
            isShooting = false;
        }
        float prex = x, prey = y;
        updateVelocityStatus();
        moved = dst(prex, prey) > 0.001f;

        if(canMove){
            float baseLerp = mech.getRotationAlpha(this);
            if(!isShooting() || !mech.turnCursor){
                if(!movement.isZero()){
                    rotation = Mathf.slerpDelta(rotation, mech.flying ? velocity.angle() : movement.angle(), 0.13f * baseLerp);
                }
            }else{
                float angle = control.input.mouseAngle(x, y);
                this.rotation = Mathf.slerpDelta(this.rotation, angle, 0.1f * baseLerp);
            }
        }
    }

    protected void updateShooting(){
        if(!state.isEditor() && isShooting() && mech.canShoot(this)){
            if(!mech.turnCursor){
                //shoot forward ignoring cursor
                mech.weapon.update(this, x + Angles.trnsx(rotation, mech.weapon.targetDistance), y + Angles.trnsy(rotation, mech.weapon.targetDistance));
            }else{
                mech.weapon.update(this, pointerX, pointerY);
            }
        }
    }

    protected void updateTouch(){
        if(Units.invalidateTarget(target, this) &&
            !(target instanceof TileEntity && ((TileEntity)target).damaged() && target.isValid() && target.getTeam() == team && mech.canHeal && dst(target) < getWeapon().bullet.range() && !(((TileEntity)target).block instanceof BuildBlock))){
            target = null;
        }

        if(state.isEditor()){
            target = null;
        }

        float targetX = Core.camera.position.x, targetY = Core.camera.position.y;
        float attractDst = 15f;
        float speed = isBoosting && !mech.flying ? mech.boostSpeed : mech.speed;

        if(moveTarget != null && !moveTarget.isDead()){
            targetX = moveTarget.getX();
            targetY = moveTarget.getY();
            boolean tapping = moveTarget instanceof TileEntity && moveTarget.getTeam() == team;
            attractDst = 0f;

            if(tapping){
                velocity.setAngle(angleTo(moveTarget));
            }

            if(dst(moveTarget) <= 2f * Time.delta()){
                if(tapping && !isDead()){
                    Tile tile = ((TileEntity)moveTarget).tile;
                    tile.block().tapped(tile, this);
                }

                moveTarget = null;
            }
        }else{
            moveTarget = null;
        }

        movement.set((targetX - x) / Time.delta(), (targetY - y) / Time.delta()).limit(speed);
        movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));

        if(dst(targetX, targetY) < attractDst){
            movement.setZero();
        }

        float expansion = 3f;

        hitbox(rect);
        rect.x -= expansion;
        rect.y -= expansion;
        rect.width += expansion * 2f;
        rect.height += expansion * 2f;

        isBoosting = collisions.overlapsTile(rect) || dst(targetX, targetY) > 85f;

        velocity.add(movement.scl(Time.delta()));

        if(velocity.len() <= 0.2f && mech.flying){
            rotation += Mathf.sin(Time.time() + id * 99, 10f, 1f);
        }else if(target == null){
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), velocity.len() / 10f);
        }

        float lx = x, ly = y;
        updateVelocityStatus();
        moved = dst(lx, ly) > 0.001f;

        if(mech.flying){
            //hovering effect
            x += Mathf.sin(Time.time() + id * 999, 25f, 0.08f);
            y += Mathf.cos(Time.time() + id * 999, 25f, 0.08f);
        }

        //update shooting if not building, not mining and there's ammo left
        if(!isBuilding() && getMineTile() == null){

            //autofire
            if(target == null){
                isShooting = false;
                if(Core.settings.getBool("autotarget")){
                    target = Units.closestTarget(team, x, y, getWeapon().bullet.range(), u -> u.getTeam() != Team.derelict, u -> u.getTeam() != Team.derelict);

                    if(mech.canHeal && target == null){
                        target = Geometry.findClosest(x, y, indexer.getDamaged(Team.sharded));
                        if(target != null && dst(target) > getWeapon().bullet.range()){
                            target = null;
                        }else if(target != null){
                            target = ((Tile)target).entity;
                        }
                    }

                    if(target != null){
                        setMineTile(null);
                    }
                }
            }else if(target.isValid() || (target instanceof TileEntity && ((TileEntity)target).damaged() && target.getTeam() == team &&
            mech.canHeal && dst(target) < getWeapon().bullet.range())){
                //rotate toward and shoot the target
                if(mech.turnCursor){
                    rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.2f);
                }

                Vec2 intercept = Predict.intercept(this, target, getWeapon().bullet.speed);

                pointerX = intercept.x;
                pointerY = intercept.y;

                updateShooting();
                isShooting = true;
            }

        }
    }

    private void updateTarget(float targetX, Float targetY){
        float attractDst = 8f;
        float speed = isBoosting && !mech.flying ? mech.boostSpeed : mech.speed;

        if(moveTarget != null && !moveTarget.isDead()){
            targetX = moveTarget.getX();
            targetY = moveTarget.getY();
            boolean tapping = moveTarget instanceof TileEntity && moveTarget.getTeam() == team;
            attractDst = 0f;

            velocity.setAngle(angleTo(moveTarget));
//            if(tapping){
//            }

            if(dst(moveTarget) <= 2f * Time.delta()){
                if(tapping && !isDead()){
                    Tile tile = ((TileEntity)moveTarget).tile;
                    tile.block().tapped(tile, this);
                }

                moveTarget = null;
            }
        }else{
            moveTarget = null;
        }

        movement.set((targetX - x) / Time.delta(), (targetY - y) / Time.delta()).limit(speed);
        movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));
//        movement.setAngle(velocity.angle());

        if(dst(targetX, targetY) < attractDst){
            movement.setZero();
        }

        isBoosting = collisions.overlapsTile(rect) || dst(targetX, targetY) > 85f;

        velocity.add(movement.scl(Time.delta()));

        if(velocity.len() <= 0.2f && mech.flying){
            rotation += Mathf.sin(Time.time() + id * 99, 10f, 1f);
        }else if(target == null){
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), velocity.len() / 10f);
        }

        float lx = x, ly = y;
        updateVelocityStatus();
        moved = dst(lx, ly) > 0.001f;

        //update shooting if not building, not mining and there's ammo left
    }

    //endregion

    //region utility methods

    public void sendMessage(String text){
        if(isLocal){
            if(Vars.ui != null){
                Vars.ui.chatfrag.addMessage(text, null);
            }
        }else{
            Call.sendMessage(con, text, null, null);
        }
    }

    public void sendMessage(String text, Player from){
        sendMessage(text, from, NetClient.colorizeName(from.id, from.name));
    }

    public void sendMessage(String text, Player from, String fromName){
        if(isLocal){
            if(Vars.ui != null){
                Vars.ui.chatfrag.addMessage(text, fromName);
            }
        }else{
            Call.sendMessage(con, text, fromName, from);
        }
    }

    public PlayerInfo getInfo(){
        if(uuid == null){
            throw new IllegalArgumentException("Local players cannot be traced and do not have info.");
        }else{
            return netServer.admins.getInfo(uuid);
        }
    }

    /** Resets all values of the player. */
    public void reset(){
        resetNoAdd();

        add();
    }

    public void resetNoAdd(){
        status.clear();
        team = Team.sharded;
        item.amount = 0;
        placeQueue.clear();
        dead = true;
        lastText = null;
        isBuilding = true;
        textFadeTime = 0f;
        target = null;
        moveTarget = null;
        isShooting = isBoosting = isTransferring = isTyping = false;
        spawner = lastSpawner = null;
        health = maxHealth();
        mining = null;
        boostHeat = drownTime = hitTime = 0f;
        mech = Mechs.starter;
        placeQueue.clear();
        respawns = state.rules.respawns;
    }

    public boolean isShooting(){
        return isShooting && (boostHeat < 0.1f || mech.flying) && mining == null;
    }

    public void updateRespawning(){

        if(state.isEditor()){
            //instant respawn at center of map.
            set(world.width() * tilesize/2f, world.height() * tilesize/2f);
            setDead(false);
        }else if(spawner != null && spawner.isValid()){
            spawner.updateSpawning(this);
        }else if(!netServer.isWaitingForPlayers()){
            if(!net.client()){
                if(lastSpawner != null && lastSpawner.isValid()){
                    this.spawner = lastSpawner;
                }else if(getClosestCore() != null){
                    this.spawner = (SpawnerTrait)getClosestCore();
                }
            }
        }else if(getClosestCore() != null){
            set(getClosestCore().getX(), getClosestCore().getY());
        }
    }

    public void beginRespawning(SpawnerTrait spawner){
        this.spawner = spawner;
        this.lastSpawner = spawner;
        this.dead = true;
        setNet(spawner.getX(), spawner.getY());
        spawner.updateSpawning(this);
    }

    //endregion

    //region read and write methods

    @Override
    public byte version(){
        return 0;
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeBoolean(isLocal);

        if(isLocal){
            stream.writeByte(mech.id);
            stream.writeInt(lastSpawner == null ? noSpawner : lastSpawner.getTile().pos());
            super.writeSave(stream, false);
        }
    }

    @Override
    public void readSave(DataInput stream, byte version) throws IOException{
        boolean local = stream.readBoolean();

        if(local){
            byte mechid = stream.readByte();
            int spawner = stream.readInt();
            Tile stile = world.tile(spawner);
            Player player = headless ? this : Vars.player;
            player.readSaveSuper(stream, version);
            player.mech = content.getByID(ContentType.mech, mechid);
            player.dead = false;
            if(stile != null && stile.entity instanceof SpawnerTrait){
                player.lastSpawner = (SpawnerTrait)stile.entity;
            }
        }
    }

    private void readSaveSuper(DataInput stream, byte version) throws IOException{
        super.readSave(stream, version);

        add();
    }

    @Override
    public void write(DataOutput buffer) throws IOException{
        super.writeSave(buffer, !isLocal);
        TypeIO.writeStringData(buffer, name);
        buffer.writeByte(Pack.byteValue(isAdmin) | (Pack.byteValue(dead) << 1) | (Pack.byteValue(isBoosting) << 2) | (Pack.byteValue(isTyping) << 3)| (Pack.byteValue(isBuilding) << 4));
        buffer.writeInt(color.rgba());
        buffer.writeByte(mech.id);
        buffer.writeInt(mining == null ? noSpawner : mining.pos());
        buffer.writeInt(spawner == null || !spawner.hasUnit(this) ? noSpawner : spawner.getTile().pos());
        buffer.writeShort((short)(baseRotation * 2));

        writeBuilding(buffer);
    }

    @Override
    public void read(DataInput buffer) throws IOException{
        float lastx = x, lasty = y, lastrot = rotation, lastvx = velocity.x, lastvy = velocity.y;

        super.readSave(buffer, version());

        name = TypeIO.readStringData(buffer);
        byte bools = buffer.readByte();
        isAdmin = (bools & 1) != 0;
        dead = (bools & 2) != 0;
        boolean boosting = (bools & 4) != 0;
        isTyping = (bools & 8) != 0;
        boolean building = (bools & 16) != 0;
        color.set(buffer.readInt());
        mech = content.getByID(ContentType.mech, buffer.readByte());
        int mine = buffer.readInt();
        int spawner = buffer.readInt();
        float baseRotation = buffer.readShort() / 2f;

        readBuilding(buffer, !isLocal);

        interpolator.read(lastx, lasty, x, y, rotation, baseRotation);
        rotation = lastrot;
        x = lastx;
        y = lasty;

        if(isLocal){
            velocity.x = lastvx;
            velocity.y = lastvy;
        }else{
            mining = world.tile(mine);
            isBuilding = building;
            isBoosting = boosting;
        }

        Tile tile = world.tile(spawner);
        if(tile != null && tile.entity instanceof SpawnerTrait){
            this.spawner = (SpawnerTrait)tile.entity;
        }else{
            this.spawner = null;
        }
    }

    //endregion
}
