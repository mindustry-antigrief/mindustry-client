package mindustry.client.navigation;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.formations.*;
import mindustry.entities.*;
import mindustry.gen.*;

import static mindustry.Vars.*;

public class RepairPath extends Path {
    Building current;
    Interval delay = new Interval();

    @Override
    public void init() {
        super.init();

        addListener(() -> player.shooting(false));
    }

    @Override
    public void setShow(boolean show) {}

    @Override
    public boolean getShow() {
        return false;
    }

    @Override
    public void follow() {
        Building build = Units.findDamagedTile(player.team(), player.x, player.y);
        if (build == null || player.unit() == null || (build != current && !delay.check(0, 30))) return;
        current = build;
        delay.reset(0, 0);
        player.shooting(player.unit().inRange(build));
        player.unit().aimLook(build);

        Formation formation = player.unit().formation;
        float range = formation == null ? 16f : formation.pattern.radius() + 16f;
        if (Core.settings.getBool("pathnav") && build.dst(player) > range + tilesize * 2) { // FINISHME: Distance based on formation size?
            if (clientThread.taskQueue.size() == 0) clientThread.taskQueue.post(() -> waypoints.set(Seq.with(Navigation.navigator.navigate(v1.set(player.x, player.y), v2.set(build.x, build.y), Navigation.obstacles.toArray(new TurretPathfindingEntity[0])))));
            waypoints.follow();
        } else waypoint.set(build.x, build.y, range, range).run();
    }

    @Override
    public float progress() {
        return Mathf.num(Units.findDamagedTile(player.team(), player.x, player.y) == null);
    }

    @Override
    public void reset() {}

    @Override
    public Position next() {
        return null;
    }
}
