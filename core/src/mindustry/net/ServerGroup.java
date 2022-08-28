package mindustry.net;

import arc.*;

public class ServerGroup{
    public String name;
    public String[] addresses;

    public ServerGroup(String name, String[] addresses){
        this.name = name;
        this.addresses = addresses;
    }

    public ServerGroup(){
    }

    public boolean hidden(){
        return Core.settings.getBool(key() + "-hidden", false) != Core.settings.getBool("hideserversbydefault");
    }

    public void setHidden(boolean hidden){
        var hiddenByDefault = Core.settings.getBool("hideserversbydefault");
        if (hidden != hiddenByDefault) Core.settings.put(key() + "-hidden", true);
        else Core.settings.remove(key() + "-hidden"); // Delete redundant setting, no need to have it around if its doing nothing (unless people want to swap between hidden/shown by default for some reason?)
    }

    String key(){
        return "server-" + (name.isEmpty() ? addresses.length == 0 ? "" : addresses[0] : name);
    }
}
