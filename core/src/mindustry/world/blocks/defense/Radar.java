package mindustry.world.blocks.defense;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.annotations.Annotations.*;
import mindustry.client.navigation.*;
import mindustry.client.utils.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Radar extends Block{
    public float discoveryTime = 60f * 10f;
    public float rotateSpeed = 2f;

    public @Load("@-base") TextureRegion baseRegion;
    public @Load("@-glow") TextureRegion glowRegion;

    public Color glowColor = Pal.turretHeat;
    public float glowScl = 5f, glowMag = 0.6f;

    public Radar(String name){
        super(name);

        update = solid = true;
        flags = EnumSet.of(BlockFlag.hasFogRadius);
        outlineIcon = true;
        fogRadius = 10;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{baseRegion, region};
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, fogRadius * tilesize, Pal.accent);
    }

    public class RadarBuild extends Building implements Ranged{ // Client: implement Ranged
        public float progress;
        public float lastRadius = 0f;
        public float smoothEfficiency = 1f;
        public float totalProgress;

        // Client stuff
        public float range(){
            return World.unconv(fogRadius);
        }
        protected TurretPathfindingEntity turretEnt;

        @Override
        public void add(){
            super.add();
            Core.app.post(() -> {
                if(team == Team.blue && CustomMode.flood.b()){
                    turretEnt = new TurretPathfindingEntity(
                        this,
                        () -> true ? range() : 0f, true, true, () -> true
                    );
                    Navigation.addEnt(turretEnt);
                }
            });
        }

        @Override
        public void remove(){
            super.remove();
            if (turretEnt != null) Navigation.removeEnt(turretEnt);
        }
        // end client stuff

        @Override
        public float fogRadius(){
            return fogRadius * progress * smoothEfficiency;
        }

        @Override
        public void updateTile(){
            smoothEfficiency = Mathf.lerpDelta(smoothEfficiency, efficiency, 0.05f);

            if(Math.abs(fogRadius() - lastRadius) >= 0.5f){
                Vars.fogControl.forceUpdate(team, this);
                lastRadius = fogRadius();
            }

            progress += edelta() / discoveryTime;
            progress = Mathf.clamp(progress);

            totalProgress += efficiency * edelta();
        }

        @Override
        public boolean canPickup(){
            return false;
        }

        @Override
        public void drawSelect(){
            if(team == Team.blue && CustomMode.flood.b()){
                Drawf.dashCircle(x, y, range(), team.color, Color.lightGray);
            } else Drawf.dashCircle(x, y, fogRadius() * tilesize, Pal.accent);
        }

        @Override
        public void draw(){
            Draw.rect(baseRegion, x, y);
            Draw.rect(region, x, y, rotateSpeed * totalProgress);

            Drawf.additive(glowRegion, glowColor, glowColor.a * (1f - glowMag + Mathf.absin(glowScl, glowMag)), x, y, rotateSpeed * totalProgress, Layer.blockAdditive);
        }

        @Override
        public float progress(){
            return progress;
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.f(progress);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            progress = read.f();
        }
    }
}
