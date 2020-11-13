package mindustry.client.navigation;

import arc.graphics.Color;
import arc.math.geom.Position;
import mindustry.Vars;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.gen.Builderc;
import mindustry.gen.Call;
import mindustry.gen.Minerc;
import mindustry.gen.Player;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.ui;

public class AssistPath extends Path {
    public final Player assisting;
    int i = 1;

    public AssistPath(Player toAssist) {
        assisting = toAssist;
    }

    @Override
    void setShow(boolean show) {}

    @Override
    boolean isShown() {
        return true;
    }

    @Override
    void follow() {
        if (assisting == null || Vars.player == null) {
            return;
        }
        if (assisting.unit() == null || Vars.player.unit() == null) {
            return;
        }

        float tolerance = assisting.unit().hitSize * 1.1f + Vars.player.unit().hitSize * 1.1f + 2;
        new PositionWaypoint(assisting.x, assisting.y, tolerance, tolerance).run();

        try {
            Vars.player.shooting(assisting.unit().isShooting); // Match shoot state
            if (assisting.unit().isShooting) {
                Vars.player.unit().aim(assisting.unit().aimX(), assisting.unit().aimY()); // Match aim coordinates
                if (Vars.player.unit().type.rotateShooting && assisting.unit().isShooting){ // Rotate player if static weapons when assisted player is shooting
                    Vars.player.unit().lookAt(assisting.unit().aimX(), assisting.unit().aimY());}
        }} catch (Exception e) {}
        // TODO: Review -> Only shoot when not moving/free aim turrets (i dont really think its needed)
//        else if(Vars.player.unit().moving()){
//            Vars.player.unit().lookAt(Vars.player.unit().vel.angle());
//        }


        if(Vars.player.unit() instanceof Minerc mine && assisting.unit() instanceof Minerc com){ // Code stolen from formationAi.java, matches player mine state to assisting
            if(com.mineTile() != null && mine.validMine(com.mineTile())){
                mine.mineTile(com.mineTile());

                CoreBlock.CoreBuild core = Vars.player.unit().team.core();

                if(core != null && com.mineTile().drop() != null && Vars.player.unit().within(core, Vars.player.unit().type.range) && !Vars.player.unit().acceptsItem(com.mineTile().drop())){
                    if(core.acceptStack(Vars.player.unit().stack.item, Vars.player.unit().stack.amount, Vars.player.unit()) > 0){
                        Call.transferItemTo(Vars.player.unit().stack.item, Vars.player.unit().stack.amount, Vars.player.unit().x, Vars.player.unit().y, core);

                        Vars.player.unit().clearItem();
                    }
                }
            }else{
                mine.mineTile(null);
            }
        }

        if (assisting.unit() instanceof Builderc && Vars.player.unit() instanceof Builderc) {
            ((Builderc) Vars.player.unit()).clearBuilding();
            if (((Builderc) assisting.unit()).activelyBuilding() && assisting.team() == Vars.player.team()) {
                assisting.builder().plans().forEach(plan -> Vars.player.builder().addBuild(plan, false));
            }
        }
    }

    @Override
    float progress() {
        return assisting == null? 1f : 0f;
    }

    @Override
    Position next() {
        return null;
    }
}
