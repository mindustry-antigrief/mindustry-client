package mindustry.entities.abilities;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class ForceFieldAbility extends Ability{
    /** Shield radius. */
    public float radius = 60f;
    /** Shield regen speed in damage/tick. */
    public float regen = 0.1f;
    /** Maximum shield. */
    public float max = 200f;
    /** Cooldown after the shield is broken, in ticks. */
    public float cooldown = 60f * 5;
    /** Sides of shield polygon. */
    public int sides = 6;
    /** Rotation of shield. */
    public float rotation = 0f;

    /** State. */
    protected float radiusScale, alpha;

    private static float realRad;
    private static Unit paramUnit;
    private static ForceFieldAbility paramField;
    private static final Cons<Bullet> shieldConsumer = trait -> {
        if(trait.team != paramUnit.team && trait.type.absorbable && Intersector.isInRegularPolygon(paramField.sides, paramUnit.x, paramUnit.y, realRad, paramField.rotation, trait.x(), trait.y()) && paramUnit.shield > 0){
            trait.absorb();
            Fx.absorb.at(trait);

            //break shield
            if(paramUnit.shield <= trait.damage()){
                paramUnit.shield -= paramField.cooldown * paramField.regen;

                Fx.shieldBreak.at(paramUnit.x, paramUnit.y, paramField.radius, paramUnit.team.color, paramUnit);
            }

            paramUnit.shield -= trait.damage();
            paramField.alpha = 1f;
        }
    };

    public ForceFieldAbility(float radius, float regen, float max, float cooldown){
        this.radius = radius;
        this.regen = regen;
        this.max = max;
        this.cooldown = cooldown;
    }

    public ForceFieldAbility(float radius, float regen, float max, float cooldown, int sides, float rotation){
        this.radius = radius;
        this.regen = regen;
        this.max = max;
        this.cooldown = cooldown;
        this.sides = sides;
        this.rotation = rotation;
    }

    ForceFieldAbility(){}

    @Override
    public void addStats(Table t){
        t.add("[lightgray]" + Stat.health.localized() + ": [white]" + Math.round(max));
        t.row();
        t.add("[lightgray]" + Stat.shootRange.localized() + ": [white]" +  Strings.autoFixed(radius / tilesize, 2) + " " + StatUnit.blocks.localized());
        t.row();
        t.add("[lightgray]" + Stat.repairSpeed.localized() + ": [white]" + Strings.autoFixed(regen * 60f, 2) + StatUnit.perSecond.localized());
        t.row();
        t.add("[lightgray]" + Stat.cooldownTime.localized() + ": [white]" + Strings.autoFixed(cooldown / 60f, 2) + " " + StatUnit.seconds.localized());
        t.row();
    }

    @Override
    public void update(Unit unit){
        if(unit.shield < max){
            unit.shield += Time.delta * regen;
        }

        alpha = Math.max(alpha - Time.delta/10f, 0f);

        if(unit.shield > 0){
            radiusScale = Mathf.lerpDelta(radiusScale, 1f, 0.06f);
            paramUnit = unit;
            paramField = this;
            checkRadius(unit);

            Groups.bullet.intersect(unit.x - realRad, unit.y - realRad, realRad * 2f, realRad * 2f, shieldConsumer);
        }else{
            radiusScale = 0f;
        }
    }

    @Override
    public void draw(Unit unit){
        checkRadius(unit);

        if(unit.shield > 0){
            var a = Mathf.clamp(alpha * UnitType.currentAlpha);
            Draw.color(unit.team.color, Color.white, a);
            Draw.z(Layer.shields + 0.001f * a);

            if(Vars.renderer.animateShields){
                Fill.poly(unit.x, unit.y, sides, realRad, rotation);
                Draw.alpha(1f);
            }else{
                Lines.stroke(1.5f);
                Draw.alpha(0.09f * UnitType.currentAlpha);
                Fill.poly(unit.x, unit.y, sides, radius, rotation);
                Draw.alpha(UnitType.currentAlpha);
                Lines.poly(unit.x, unit.y, sides, radius, rotation);
            }
        }
    }

    @Override
    public void displayBars(Unit unit, Table bars){
        bars.add(new Bar(() -> Core.bundle.get("stat.shieldhealth") + " (" + unit.shield + ")", () -> Pal.accent, () -> unit.shield / max)).row();
    }

    public void checkRadius(Unit unit){
        //timer2 is used to store radius scale as an effect
        realRad = radiusScale * radius;
    }

    @Override
    public String localized(){
        return Strings.format("@ (@ health)", super.localized(), max);
    }
}
