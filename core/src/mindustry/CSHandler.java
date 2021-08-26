package mindustry;

import arc.struct.ObjectMap;

public class CSHandler {
    public static ObjectMap<Class, CustomScripts> listener_arr = new ObjectMap<>();
    public static ObjectMap<String, CustomScripts> client_task_arr = new ObjectMap<>();
    protected int id = 0;

    CSHandler() {

        listener_arr.put(CustomScripts.onCommandCenterChange.listening, new CustomScripts.onCommandCenterChange());

    }

    public static void access(Class c, boolean set) {
        listener_arr.get(c).set(set);
    }
}
