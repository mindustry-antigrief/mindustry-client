package mindustry.client.antigreif;

import mindustry.client.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.world.*;

import java.util.*;

public class InteractionLogItem{
    public int x;
    public int y;
    public int rotation;
    public Block block;
    public boolean remove;
    public ConfigRequest config;

    public InteractionLogItem(int x, int y, int rotation, Block block, boolean remove){
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.block = block;
        this.remove = remove;
    }

    public InteractionLogItem(BuildRequest req){
        x = req.x;
        y = req.y;
        rotation = req.rotation;
        block = req.block;
        remove = req.breaking;
    }

    public InteractionLogItem(ConfigRequest req){
        config = req;
    }

    public BuildRequest toRequest(){
        BuildRequest req = new BuildRequest(x, y, rotation, block);
        req.breaking = remove;
        return req;
    }

    public BuildRequest undoRequest(){
        if(block != null){
            BuildRequest req = new BuildRequest(x, y, rotation, block);
            req.breaking = !remove;
            Client.undid_hashes.add(req.hashCode());
            return req;
        }
        Client.configRequests.addLast(config.getUndoRequest());
        return null;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        InteractionLogItem that = (InteractionLogItem)o;
        return x == that.x &&
        y == that.y &&
        rotation == that.rotation &&
        remove == that.remove &&
        Objects.equals(block, that.block) &&
        config == that.config;
    }

    @Override
    public int hashCode(){
        return Objects.hash(x, y, rotation, block, remove, config);
    }
}
