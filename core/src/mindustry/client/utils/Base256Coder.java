package mindustry.client.utils;

import arc.struct.*;
import arc.util.serialization.*;
import java.util.*;

public class Base256Coder{
    public static void main(String[] args){
        byte[] bytes = new byte[8192];
        new Random().nextBytes(bytes);
        System.out.println(Arrays.toString(bytes));
        String encoded = encode(bytes);
        System.out.println(encoded);
        byte[] decoded = decode(encoded);
        System.out.println(Arrays.toString(decoded));
        System.out.println(Arrays.equals(bytes, decoded));
        System.out.println(encoded.length());
        System.out.println(decoded.length);
    }

    public static String encode(byte[] input){
//        BigInteger next = new BigInteger(input);
//        StringBuilder output = new StringBuilder();
//        while(next.divide(new BigInteger("64")).compareTo(BigInteger.ZERO) > 0){
//            output.append(CharMapping.chars.get(next.mod(new BigInteger("64")).intValue()));
//            next = next.divide(new BigInteger("64"));
//        }
//        return output.reverse().toString();
//        BigInteger inp = new BigInteger(input);
//        BigInteger mask = new BigInteger("255");
        Array<Byte> bytes = new Array<>();
        for(byte b : input){
            bytes.add(b);
        }

        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < input.length; i++){
//            BigInteger digit = inp.and(mask);
//            System.out.println(digit.intValue());
            builder.append(CharMapping.chars.get(bytes.pop() + 128));
//            builder.append(CharMapping.chars.get(digit.intValueExact()));
//            inp = inp.shiftRight(8);
        }
//        System.out.println("len = " + len);
        return builder.reverse().toString();
    }

    public static byte[] decode(String input){
//        input = new StringBuilder(input).reverse().toString();
//        BigInteger output = new BigInteger("0");
////        BigInteger power = new BigInteger("1");
//        for(int i = 0; i < input.length(); i += 1){
//            String letter = input.substring(i, i + 1);
////            System.out.println(CharMapping.chars.indexOf(letter));
//            output = output.shiftLeft(8);
//            output = output.add(new BigInteger(String.valueOf(CharMapping.chars.indexOf(letter))));
////            output = output.add(new BigInteger(String.valueOf(CharMapping.chars.indexOf(letter))).multiply(power));
////            power = power.multiply(new BigInteger("128"));
//        }
////        output = output.shiftRight(8);
//        byte[] array = output.toByteArray();
////        if(array.length > input.length()){
////            array = Arrays.copyOfRange(array, 0, input.length());
////        }
//        return array;
        byte[] bytes = new byte[input.length()];
        for(int i = 0; i < input.length(); i++){
            String letter = input.substring(i, i + 1);
            bytes[i] = new Integer(CharMapping.chars.indexOf(letter) - 128).byteValue();
        }
        return bytes;
    }

    public static String encode(String string){
        return encode(string.getBytes());
    }

    public static String decodeString(String input){
        return new String(decode(input));
    }

    static class CharMapping{
        public static final Array<String> chars = new Array<>();
        static{
            for(char c : Base64Coder.urlsafeMap.getEncodingMap()){
                chars.add(Character.toString(c));
            }
//            for(int i = 0xE800; i <= 0xE840; i += 1){
//                chars.add(new String(new int[]{ i }, 0, 1));
//            }
            int max = 0xF8FF;
            int min = 0xF83F;
            for(int i = min; i <= max; i += 1){
                chars.add(new String(new int[]{ i }, 0, 1));
            }
        }
    }

}
