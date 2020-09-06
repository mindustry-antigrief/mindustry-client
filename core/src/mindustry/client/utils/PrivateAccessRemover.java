package mindustry.client.utils;

import java.lang.reflect.*;

public class PrivateAccessRemover{

    public static <T> T getPrivateField(Object object, String name, boolean nothing){
        return (T)(PrivateAccessRemover.getPrivateField(object, name));
    }

    public static Object getPrivateField(Object object, String name){
        try{
            Field f = object.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(object);
        }catch(NoSuchFieldException error){
            System.out.println("Reflective access failed!  The program will now crash.");
            return null;
        }catch(IllegalAccessException error){
            System.out.println("Reflective access failed!  The program will now crash.");
            return null;
        }
    }

    public static Method getPrivateMethod(Object object, String name){
        try{
            Method f = object.getClass().getDeclaredMethod(name, object.getClass());
            f.setAccessible(true);
            return f;
        }catch(NoSuchMethodException error){
            System.out.println("Reflective access failed!  The program will now crash.");
            return null;
        }
    }
}
