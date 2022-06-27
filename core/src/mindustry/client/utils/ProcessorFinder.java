package mindustry.client.utils;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import kotlin.text.Regex;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;

public class ProcessorFinder {
    private static final CopyOnWriteArrayList<LogicBuild> highlighted = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<Regex> queries = new CopyOnWriteArrayList<>();

    public static void search() {
        highlighted.clear();
        for (Regex query : queries) {
            Seq<Building> builds = new Seq<>();
            player.team().data().buildings.getObjects(builds);
    
            int matchCount = 0, processorCount = 0;
            for (Building build : builds) {
                if (build instanceof LogicBuild logicBuild ) {
                    if (query.containsMatchIn((logicBuild.code))) {
                        matchCount++;
                        highlighted.add(logicBuild);
                    }
                    processorCount++;
                }
            }
    
            // Log how many found
            if (matchCount == 0) player.sendMessage("[accent]No matches found.");
            else player.sendMessage(String.format("[accent]Found [coral]%d/%d[] [accent]matches.", matchCount, processorCount));
        }
    }

    public static void list() {
        StringBuilder sb = new StringBuilder();
        sb.append("[accent]Locations: []");
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
