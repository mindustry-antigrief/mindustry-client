package mindustry.client.navigation;

import static mindustry.Vars.*;
import arc.math.geom.Position;
import org.jetbrains.annotations.Nullable;

public class BuildMinePath extends Path{ // This is so scuffed. Help.
    private boolean show;
    
    private boolean initMine = true;
    private boolean initBuild = true;
    private static Path currentPath;
    
    public BuildMinePath() {}
    
    @Override
    public void setShow(boolean show) {
        this.show = show;
    }
    
    @Override
    public boolean getShow() {
        return this.show;
    }
    
    @Override
    public void follow() {
        // really really scuffed
        if (player.unit().plans.isEmpty()) {
            if (initBuild) {
                currentPath = new MinePath("*");
                initBuild = false;
                initMine = true;
                
                player.sendMessage("\\[BuildMine\\] Swapping to mine path.");
            }
            else currentPath.follow();
        } else {
            if (initMine) {
                currentPath = new BuildPath("self");
                initBuild = true;
                initMine = false;

                player.sendMessage("\\[BuildMine\\] Swapping to build path (self).");
            }
            else {
                currentPath.follow();
            }
        }
    }
    
    @Override
    public float progress() {
        return 0;
    }
    
    @Override
    public void reset() {
        initBuild = true;
        initMine = true;
        currentPath = null;
    }
    
    @Nullable
    @Override
    public Position next() {
        return null;
    }
    
    @Override
    public synchronized void draw() {
        if (currentPath != null) currentPath.draw();
    }
}
