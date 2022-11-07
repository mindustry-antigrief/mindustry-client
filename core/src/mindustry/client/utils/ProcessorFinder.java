package mindustry.client.utils;

import arc.Core;
import arc.struct.Seq;
import kotlin.text.Regex;
import mindustry.Vars;
import mindustry.client.navigation.clientThread;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import java.util.concurrent.CopyOnWriteArrayList;

import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;

public class ProcessorFinder {
    private static final CopyOnWriteArrayList<LogicBuild> highlighted = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<Regex> queries = new CopyOnWriteArrayList<>();

    public static void search() {
        highlighted.clear();
        Seq<Building> builds = player.team().data().buildings.filter(b -> b instanceof LogicBuild);
    
        clientThread.post(() -> {
            int matchCount = 0, processorCount = 0;
            for (Building build : builds) {
                var logicBuild = (LogicBuild) build;
                for (Regex query : queries) {
                    if (query.containsMatchIn((logicBuild.code))) {
                        matchCount++;
                        highlighted.add(logicBuild);
                    }
                    processorCount++;
                }
            }
    
            int finalMatchCount = matchCount;
            int finalProcessorCount = processorCount;
            Core.app.post(() -> {
                if (finalMatchCount == 0) player.sendMessage("[accent]No matches found.");
                else player.sendMessage(String.format("[accent]Found [coral]%d/%d[] [accent]matches.", finalMatchCount, finalProcessorCount));
            });
        });
    
    }
    
    public static void searchAll() {
        highlighted.clear();
    
        Tiles tiles = Vars.world.tiles;
        clientThread.post(() -> {
            int matchCount = 0, processorCount = 0;
            for (Tile tile : tiles) {
                if (tile.build instanceof LogicBuild logicBuild) {
                    for (Regex query : queries) {
                        if (query.containsMatchIn((logicBuild.code))) {
                            matchCount++;
                            highlighted.add(logicBuild);
                        }
                        processorCount++;
                    }
                }
            }
    
            int finalMatchCount = matchCount;
            int finalProcessorCount = processorCount;
            Core.app.post(() -> {
                if (finalMatchCount == 0) player.sendMessage("[accent]No matches found.");
                else player.sendMessage(String.format("[accent]Found [coral]%d/%d[] [accent]matches.", finalMatchCount, finalProcessorCount));
            });
        });
    }

    public static void list() {
        StringBuilder sb = new StringBuilder(Core.bundle.get("client.command.procfind.list"));
        for (LogicBuild build : highlighted) sb.append(String.format("(%d, %d)", (int) build.x >> 3, (int) build.y >> 3)).append(", ");
        player.sendMessage(sb.toString());
    }

    public static void clear() {
        queries.clear();
        highlighted.clear();
    }

    public static int getCount() {
        return highlighted.size();
    }

    public static void draw() {
        for (LogicBuild build : highlighted) {
            Drawf.square(build.x, build.y, build.block.size * tilesize / 2f + 8f);
        }
    }
}
