package mindustry.client.utils;

public class FloatEmbed {

    public static float embedInFloat(float inp, byte item) {
        int bits = Float.floatToIntBits(inp);
        bits &= 0xffffff00;
        bits += item;
        return Float.intBitsToFloat(bits);
    }

    public static boolean isEmbedded(float inp, byte item) {
        int bits = Float.floatToIntBits(inp);
        int itembits = Byte.toUnsignedInt(item);
        return (bits & itembits) == itembits;
    }
}
