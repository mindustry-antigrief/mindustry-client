package mindustry.entities.comp;

import mindustry.annotations.Annotations.*;
import mindustry.client.antigrief.*;
import mindustry.gen.*;
import mindustry.world.blocks.power.*;

@EntityDef(value = PowerGraphUpdaterc.class, serialize = false, genio = false)
@Component
abstract class PowerGraphUpdaterComp implements Entityc{
    public transient PowerGraph graph;

    @Override
    public void update(){
        graph.update();
    }

    @Override
    public void add() {
        PowerInfo.graphs.add(graph);
    }

    @Override
    public void remove(){
        PowerInfo.graphs.remove(graph);
    }
}
