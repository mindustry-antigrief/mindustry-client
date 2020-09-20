package mindustry.client;

import arc.struct.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.logic.MessageBlock.*;

import static mindustry.Vars.*;

public class MessageSystem{
    public static ObjectSet<Integer> cachedPositions = new ObjectSet<>();
    public static String prelude = "DO NOT WRITE IN THIS BLOCK.  It is for encrypted chat ONLY. %%";

    public static void scanForMessages() {
        cachedPositions.clear();
        for (Tile[] tiles : world.getTiles()) {
            for (Tile tile : tiles) {
                if (tile != null) {
                    if (tile.block() instanceof MessageBlock) {
                        cachedPositions.add(tile.pos());
                    }
                }
            }
        }
    }

    public static String readMessage(int pos) {
        Tile tile = world.tile(pos);
        if (tile == null) {
            cachedPositions.remove(pos);
            return null;
        }
        if (tile.block() == null) {
            cachedPositions.remove(pos);
            return null;
        }
        if (!(tile.block() instanceof MessageBlock)) {
            cachedPositions.remove(pos);
            return null;
        }

        cachedPositions.add(pos);
        MessageBlockEntity entity = tile.ent();
        if (!entity.message.startsWith(prelude)) {
            cachedPositions.remove(pos);
            return null;
        }

        return entity.message.replace(prelude, "");
    }

    public static void writeMessage(String message) {
        if (cachedPositions.isEmpty()) {
            for (Tile[] tiles : world.getTiles()) {
                for (Tile tile : tiles) {
                    if (tile != null) {
                        if (tile.block() instanceof MessageBlock) {
                            cachedPositions.add(tile.pos());
                        }
                    }
                }
            }
        }
        for (int pos : cachedPositions) {
            Tile tile = world.tile(pos);
            if(tile == null){
                cachedPositions.remove(pos);
                continue;
            }
            if(tile.block() == null){
                cachedPositions.remove(pos);
                continue;
            }
            if(!(tile.block() instanceof MessageBlock)){
                cachedPositions.remove(pos);
                continue;
            }
            MessageBlockEntity entity = tile.ent();
            if(!entity.message.startsWith(prelude)){
                cachedPositions.remove(pos);
                continue;
            }
            Call.setMessageBlockText(player, tile, prelude + message);
            return;
        }
    }
}
