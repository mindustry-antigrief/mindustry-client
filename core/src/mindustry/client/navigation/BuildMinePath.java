package mindustry.client.navigation;

import arc.math.geom.Position;
import arc.util.Interval;
import org.jetbrains.annotations.Nullable;

import static mindustry.Vars.control;
import static mindustry.Vars.player;

public class BuildMinePath extends Path{ // This is so scuffed. Help.
    private boolean show;
    private boolean initMine = true;
    private boolean initBuild = true;
    private Path currentPath;
    private Interval timer = new Interval();
    
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
        // this is more of a personal use thing. You might not want to have this.
        if (control.input.isBuilding && !player.unit().plans.isEmpty()) {
            if (initBuild) {
                currentPath = new BuildPath();
                initBuild = false;
                initMine = true;
            }
            else {
                currentPath.follow();
                timer.reset(0, 0);
            }
        } else {
            if (initMine && timer.get(0, 300)) {
                currentPath = new MinePath();
                initBuild = true;
                initMine = false;
            }
            else currentPath.follow();
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
