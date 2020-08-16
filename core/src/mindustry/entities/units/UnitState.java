package mindustry.entities.units;

import arc.graphics.*;

public interface UnitState{

    default String getName(){
        return "";
    }

    default Color getColor(){
        return Color.white;
    }

    default void entered(){
    }

    default void exited(){
    }

    default void update(){
    }
}
