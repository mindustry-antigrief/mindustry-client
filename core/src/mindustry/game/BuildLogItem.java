package mindustry.game;

import mindustry.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.world.*;

import java.util.*;

public class BuildLogItem{
    public int x;
    public int y;
    public int rotation;
    public Block block;
    public boolean remove;

    public BuildLogItem(BuildRequest req){
        x = req.x;
        y = req.y;
        rotation = req.rotation;
        block = req.block;
        remove = req.breaking;
    }

    public BuildRequest toRequest(){
        BuildRequest req = new BuildRequest(x, y, rotation, block);
        req.breaking = remove;
        return req;
    }

    public BuildRequest undoRequest(){
        BuildRequest req = new BuildRequest(x, y, rotation, block);
        req.breaking = !remove;
//        Vars.undid.add();
//        req.undo = true;
        Vars.undid_hashes.add(req.hashCode());
        return req;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        BuildLogItem that = (BuildLogItem)o;
        return x == that.x &&
        y == that.y &&
        rotation == that.rotation &&
        remove == that.remove &&
        Objects.equals(block, that.block);
    }

    @Override
    public int hashCode(){
        return Objects.hash(x, y, rotation, block, remove);
    }
}
