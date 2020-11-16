package mindustry.client.utils;

public class FloatEmbed {
    public static float embedInFloat(float inp) {
        int bits = Float.floatToIntBits(inp);
        bits >>= 8;
        bits <<= 8;
        bits += 0b10101010;
        return Float.intBitsToFloat(bits);
    }

    public static boolean isEmbedded(float inp) {
        int bits = Float.floatToIntBits(inp);
        bits <<= 32 - 8;
        System.out.println(Integer.toBinaryString(bits));
        return bits == 0b10101010000000000000000000000000;
    }
}
