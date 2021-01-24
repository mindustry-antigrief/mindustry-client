package mindustry.client.utils;

public class FloatEmbed {

    public static float embedInFloat(float inp, byte item) {
        int bits = Float.floatToIntBits(inp);
        bits >>= 8;
        bits <<= 8;
        bits += item;
        return Float.intBitsToFloat(bits);
    }

    public static boolean isEmbedded(float inp, byte item) {
        int bits = Float.floatToIntBits(inp);
        bits <<= 32 - 8;
        int addend = item;
        addend <<= 32 - 8;
        return bits == addend;
    }
}
