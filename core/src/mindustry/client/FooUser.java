package mindustry.client;

public class FooUser {

    public static boolean IsUser(String user) { // Check if user is using foo's client
        if (user == null) return false;
        return user.endsWith("[]");
    }

}
