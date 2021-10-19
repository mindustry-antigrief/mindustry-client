package mindustry.client.navigation;

import arc.math.geom.*;
import mindustry.*;
import mindustry.game.*;

public class TurretPathfindingEntity extends Circle {
    public boolean canHitPlayer, canShoot, targetGround;
    private static long nextId = 0;
    public long id;
    public Team team = Team.derelict;
    public boolean turret; // Whether this is a turret or just a unit weapon.

    {
        id = nextId++;
    }

    public TurretPathfindingEntity(float range, boolean turret) {
        this.radius = range + Vars.tilesize;
        this.turret = turret;
    }

    @Override
    public String toString(){
        return "TurretPathfindingEntity{" +
        "x=" + x +
        ", y=" + y +
        ", radius=" + radius +
        '}';
    }

    @Override
    public boolean equals(Object o) {
        if(o == this) return true;
        if(o == null || o.getClass() != this.getClass()) return false;
        TurretPathfindingEntity c = (TurretPathfindingEntity) o;
        return this.id == c.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
