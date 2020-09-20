package mindustry.world;

import arc.math.geom.*;

import static mindustry.Vars.tilesize;

/** Methods for a packed position 'struct', contained in an int. */
public class Pos{
    public static final int invalid = get(-1, -1);

    /** Returns packed position from an x/y position. The values must be within short limits. */
    public static int get(int x, int y){
        return (((short)x) << 16) | (((short)y) & 0xFFFF);
    }

    /** Returns the x component of a position. */
    public static short x(int pos){
        return (short)(pos >>> 16);
    }

    /** Returns the y component of a position. */
    public static short y(int pos){
        return (short)(pos & 0xFFFF);
    }

    public static Position toPosition(int pos) { return new Position(){
        public final int x = Pos.x(pos);
        public final int y = Pos.y(pos);

        @Override
        public float getX(){
            return x * tilesize;
        }

        @Override
        public float getY(){
            return y * tilesize;
        }
    };}
}
