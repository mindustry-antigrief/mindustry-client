package mindustry.core;

import java.lang.reflect.*;

public class PrivateAccessRemover{

    public static Object getPrivateField(Object object, String name){
        try{
            Field f = object.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(object);
        }catch(NoSuchFieldException | IllegalAccessException error){
            System.out.println("Reflective access failed!  The program will now crash.");
            return null;
        }
    }
}
