package mindustry.client.pathfinding;

public class TurretPathfindingEntity{
    int x, y;
    float range;

    public TurretPathfindingEntity(int x, int y, float range){
        this.x = x;
        this.y = y;
        this.range = range;
    }

    @Override
    public String toString(){
        return "TurretPathfindingEntity{" +
        "x=" + x +
        ", y=" + y +
        ", range=" + range +
        '}';
    }
}
