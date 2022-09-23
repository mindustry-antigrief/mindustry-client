package mindustry.world.blocks.power;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.ui.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ImpactReactor extends PowerGenerator{
    public final int timerUse = timers++;
    public float warmupSpeed = 0.001f;
    public float itemDuration = 60f;
    public float floodNullifierRange;

    public ImpactReactor(String name){
        super(name);
        hasPower = true;
        hasLiquids = true;
        liquidCapacity = 30f;
        hasItems = true;
        outputsPower = consumesPower = true;
        flags = EnumSet.of(BlockFlag.reactor, BlockFlag.generator);
        lightRadius = 115f;
        emitLight = true;
        envEnabled = Env.any;

        drawer = new DrawMulti(new DrawRegion("-bottom"), new DrawPlasma(), new DrawDefault());

        explosionShake = 6f;
        explosionShakeDuration = 16f;
        explosionDamage = 1900 * 4;
        explodeEffect = Fx.impactReactorExplosion;
        explodeSound = Sounds.explosionbig;
    }

    @Override
    public void init(){
        super.init();
        floodNullifierRange = (16 - size/2f) * tilesize; // .io programmn't and didn't factor in the actual reactor size
    }

    @Override
    public void setBars(){
        super.setBars();

        addBar("power", (GeneratorBuild entity) -> new Bar(() ->
        Core.bundle.format("bar.poweroutput",
        Strings.fixed(Math.max(entity.getPowerProduction() - consPower.usage, 0) * 60 * entity.timeScale(), 1)),
        () -> Pal.powerBar,
        () -> entity.productionEfficiency));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        float wx = x * tilesize + offset, wy = y * tilesize + offset;
        Drawf.dashCircle(wx, wy, explosionRadius * tilesize, Color.coral);
        if (ClientUtils.flood()) {
            Drawf.dashCircle(wx, wy, floodNullifierRange, Color.orange);
            indexer.eachBlock(null, wx, wy, floodNullifierRange, b -> b instanceof CoreBlock.CoreBuild && b.within(wx, wy, floodNullifierRange), b -> Drawf.selected(b, Color.orange));
        }
    }

    @Override
    public void drawPlanConfigTop(BuildPlan req, Eachable<BuildPlan> list){
        if (ClientUtils.flood()) {
            Drawf.dashCircle(req.drawx(), req.drawy(), floodNullifierRange, Color.orange);
            indexer.eachBlock(null, req.drawx(), req.drawy(), floodNullifierRange, b -> b instanceof CoreBlock.CoreBuild, b -> Drawf.selected(b, Color.orange));
        }
        if (settings.getBool("showreactors")) {
            Drawf.dashCircle(req.drawx(), req.drawy(), explosionRadius * tilesize, Color.coral);
        }
    }

    @Override
    public void setStats(){
        stats.timePeriod = itemDuration;
        super.setStats();

        if(hasItems){
            stats.add(Stat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    public class ImpactReactorBuild extends GeneratorBuild{
        public float warmup, totalProgress;

        @Override
        public void updateTile(){
            if(efficiency >= 0.9999f && power.status >= 0.99f){
                boolean prevOut = getPowerProduction() <= consPower.requestedPower(this);

                warmup = Mathf.lerpDelta(warmup, 1f, warmupSpeed * timeScale);
                if(Mathf.equal(warmup, 1f, 0.001f)){
                    warmup = 1f;
                }

                if(!prevOut && (getPowerProduction() > consPower.requestedPower(this))){
                    Events.fire(Trigger.impactPower);
                }

                if(timer(timerUse, itemDuration / timeScale)){
                    consume();
                }
            }else{
                warmup = Mathf.lerpDelta(warmup, 0f, 0.01f);
            }

            totalProgress += warmup * Time.delta;

            productionEfficiency = Mathf.pow(warmup, 5f);
        }

        @Override
        public float warmup(){
            return warmup;
        }

        @Override
        public float totalProgress(){
            return totalProgress;
        }

        @Override
        public float ambientVolume(){
            return warmup;
        }
        
        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.heat) return warmup;
            return super.sense(sensor);
        }

        @Override
        public void createExplosion(){
            if(warmup >= 0.3f){
                super.createExplosion();
            }
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            warmup = read.f();
        }
    }
}
