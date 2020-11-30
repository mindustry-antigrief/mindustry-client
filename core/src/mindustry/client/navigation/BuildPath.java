package mindustry.client.navigation;

import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.graphics.Color;
import arc.math.geom.Position;
import arc.struct.Queue;
import arc.struct.Seq;
import arc.util.Interval;
import mindustry.ai.formations.Formation;
import mindustry.client.navigation.waypoints.PositionWaypoint;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Teams;
import mindustry.gen.*;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

public class BuildPath extends Path {
    private boolean show;
    boolean found = false;
    Unit following;

    @Override
    void setShow(boolean show) { this.show = show; }

    @Override
    boolean isShown() { return show; }

    @Override
    void follow() {

        if(following != null){
            //try to follow and mimic someone

            //validate follower
            if(!following.isValid() || !following.activelyBuilding()){
                following = null;
//                player.unit().plans.clear();
                return;
            }

            //set to follower's first build plan, whatever that is
            player.unit().plans.addFirst(following.buildPlan());
        }

        if(player.unit().buildPlan() != null){
            Building core = player.core();
            Queue<BuildPlan> temp = new Queue<>();

            // Find the best build in the player's queue (builds the closest affordable item. if none exists, it builds the closest) theres prob a better method for this but idk what it is
            for(BuildPlan p : player.unit().plans()) {
                if(!(state.rules.infiniteResources || (core != null && (core.items.has(p.block.requirements, state.rules.buildCostMultiplier) || state.rules.infiniteResources)))) temp.addLast(player.unit().plans.removeFirst());
            }
            BuildPlan best = Geometry.findClosest(player.getX(), player.getY(), player.unit().plans);
            for (BuildPlan p : temp) player.unit().plans.addLast(p);
            if (best == null) best = Geometry.findClosest(player.x, player.y, player.unit().plans);
            if (player.unit().buildPlan() != best) {
                player.unit().clearBuilding();
                player.unit().plans.remove(best);
                player.unit().plans.addFirst(best);
            }


            BuildPlan req = player.unit().buildPlan(); //approach request if building

            boolean valid =
                    (req.tile().build instanceof ConstructBlock.ConstructBuild && req.tile().<ConstructBlock.ConstructBuild>bc().cblock == req.block) ||
                            (req.breaking ?
                                    Build.validBreak(player.unit().team(), req.x, req.y) :
                                    Build.validPlace(req.block, player.unit().team(), req.x, req.y, req.rotation));

            if(valid){
                //move toward the request
                Formation formation = player.unit().formation;
                float range = buildingRange - 10;
                if (formation != null) range = formation.pattern.spacing / (float)Math.sin(180f / formation.pattern.slots * Mathf.degRad);
                new PositionWaypoint(req.getX(), req.getY(), 0, range).run();
            }else{
                //discard invalid request
                player.unit().plans.removeFirst();
            }
        } else {
            //follow someone and help them build
            found = false;

            Units.nearby(player.unit().team, player.unit().x, player.unit().y, 1000000000, u -> {
                if(found) return;

                if(u.canBuild() && u != player.unit() && u.activelyBuilding()){
                    BuildPlan plan = u.buildPlan();

                    Building build = world.build(plan.x, plan.y);
                    if(build instanceof ConstructBlock.ConstructBuild cons){
                        float dist = Math.min(cons.dst(player.unit()) - buildingRange, 0);

                        //make sure you can reach the request in time
                        if(dist / player.unit().speed() < cons.buildCost * 0.9f){
                            following = u;
                            found = true;
                        }
                    }
                }
            });

            if(!player.unit().team.data().blocks.isEmpty()){
                Queue<Teams.BlockPlan> blocks = player.unit().team.data().blocks;
                for (Teams.BlockPlan block : blocks) {
                    //check if it's already been placed
                    if (world.tile(block.x, block.y)!=null && world.tile(block.x, block.y).block().id==block.block) {
                        blocks.removeFirst();
                    } else if (Build.validPlace(content.block(block.block), player.unit().team(), block.x, block.y, block.rotation)) { //it's valid.
                        //add build request.
                        player.unit().addBuild(new BuildPlan(block.x, block.y, block.rotation, content.block(block.block), block.config));
                        //shift build plan to tail so next unit builds something else.
                        blocks.addLast(blocks.removeFirst());
                    } else {
                        //shift head of queue to tail, try something else next time
                        blocks.removeFirst();
                        blocks.addLast(block);
                    }
                }
            }
        }
    }

    @Override
    float progress() {
        return 0;
    }

    @Override
    Position next() {
        return null;
    }
}

