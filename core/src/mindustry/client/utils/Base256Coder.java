package mindustry.client.utils;

import arc.struct.*;
import arc.util.serialization.*;

public class Base256Coder{
    public static String encode(byte[] input){
        Array<Byte> bytes = new Array<>();
        for(byte b : input){
            bytes.add(b);
        }

        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < input.length; i++){
            builder.append(CharMapping.chars.get(bytes.pop() + 128));
        }
        return builder.reverse().toString();
    }

    public static byte[] decode(String input){
        byte[] bytes = new byte[input.length()];
        for(int i = 0; i < input.length(); i++){
            String letter = input.substring(i, i + 1);
            int pos = CharMapping.chars.indexOf(letter);
            if(pos == -1){
                return null;
            }
            bytes[i] = new Integer(pos - 128).byteValue();
        }
        return bytes;
    }

    public static String encode(String string){
        return encode(string.getBytes());
    }

    public static String decodeString(String input){
        byte[] decoded = decode(input);
        if(decoded == null){
            return "";
        }
        return new String(decoded);
    }

    static class CharMapping{
        public static final Array<String> chars = new Array<>();
        static{
            for(char c : Base64Coder.urlsafeMap.getEncodingMap()){
                chars.add(Character.toString(c));
            }
            int max = 0xF8FF;
            int min = 0xF83F;
            for(int i = min; i <= max; i += 1){
                chars.add(new String(new int[]{ i }, 0, 1));
            }
        }
    }
}
