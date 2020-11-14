package mindustry.world.blocks.defense.turrets;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.math.geom.Rect;
import mindustry.client.Client;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public abstract class BaseTurret extends Block{
    public float range = 80f;
    public float rotateSpeed = 5;

    public boolean acceptCoolant = true;
    /** Effect displayed when coolant is used. */
    public Effect coolEffect = Fx.fuelburn;
    /** How much reload is lowered by for each unit of liquid of heat capacity. */
    public float coolantMultiplier = 5f;

    public BaseTurret(String name){
        super(name);

        update = true;
        solid = true;
        outlineIcon = true;
    }

    @Override
    public void init(){
        if(acceptCoolant && !consumes.has(ConsumeType.liquid)){
            hasLiquids = true;
            consumes.add(new ConsumeLiquidFilter(liquid -> liquid.temperature <= 0.5f && liquid.flammability < 0.1f, 0.2f)).update(false).boost();
        }

        super.init();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, Pal.placing);
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(Stat.shootRange, range / tilesize, StatUnit.blocks);
    }

    public class BaseTurretBuild extends Building implements Ranged{
        public float rotation = 90;

        @Override
        public float range(){
            return range;
        }

        @Override
        public void drawSelect(){
            Drawf.dashCircle(x, y, range, team.color);
        }

        @Override
        public void drawAlways() {
            if (Client.showingTurrets) {
                if (team != player.team()) {
                    Rect bounds = new Rect();
                    Core.camera.bounds(bounds);
                    Rect turretBounds = new Rect().setSize(range * 2).setCenter(x, y);
                    if (bounds.overlaps(turretBounds)) {
                        Draw.color(team.color);
                        Drawf.dashCircle(x, y, range, team.color);
                        Draw.color();
                    }
                }
            }
        }
    }
}
