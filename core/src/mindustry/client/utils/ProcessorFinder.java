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

    public static void query(Regex query) {
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
    
    public static void logClusters() {
        // Find clusters
        // round to nearest multiples of 64 and store those chunk coords.
        Set<Vec2> clusters = new HashSet<>();
        for (LogicBuild build : highlighted) {
            clusters.add(new Vec2(Mathf.floor(build.x / 512), Mathf.floor(build.y / 512))); // 8 * 64
        }
        // Log clusters
        player.sendMessage("[accent]Clusters:");
        for (Vec2 cluster : clusters) {
            player.sendMessage(String.format("[accent](%d, %d)", (int) (cluster.x * 64 + 64) / 2, (int) (cluster.y * 64 + 64) / 2)); // unpack
        }
    }

    public static void clear() {
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
