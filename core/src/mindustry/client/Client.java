package mindustry.client;

import arc.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.struct.Queue;
import arc.util.*;
import arc.util.CommandHandler.*;
import mindustry.client.antigreif.*;
import mindustry.client.pathfinding.*;
import mindustry.client.utils.*;
import mindustry.entities.traits.BuilderTrait.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.input.*;
import mindustry.world.*;

import java.security.*;
import java.time.*;
import java.util.*;
import static arc.Core.*;
import static mindustry.Vars.*;

public class Client{
    public static Player stalking = player;
    public static Player following = player;
    public static boolean breakingFollowing = false;
    public static HashSet<Integer> undid_hashes = new HashSet<>();
    public static Array<Waypoint> waypoints = new Array<>();
    public static long waypointStartTime = 0;
    public static boolean followingWaypoints = false;
    public static long waypointFollowStartTime = 0;
    public static Queue<Waypoint> notDone = new Queue<>();
    public static boolean recordingWaypoints = false;
    public static long waypointEndTime = 0;
    public static Vec2 cameraPositionOverride = null;
    public static float flyingOpacity = 0.2F;
    public static boolean repeatWaypoints = true;
    public static boolean autoBuild = false;
    public static boolean autoMine = false;
    public static Queue<ConfigRequest> configRequests = new Queue<>();
    public static Block found;
    public static Vec2 targetPosition = new Vec2();
    public static boolean wayFinding = false;
    public static Array<Vec3> crosshairs = new Array<>();
    public static HashSet<Integer> connected = new HashSet<>();
    public static int powerTilePos = 0;
    public static Array<Command> localCommands = new Array<>();
    public static boolean showTurretRanges = false;
    public static BuildRequest building;
    public static long lastAutoresponseSent = 0;
    public static long lastCommandSent = 0;
    public static boolean xray = false;
    public static ObjectMap<Player, ECDH> cachedKeys = new ObjectMap<>();
    public static boolean showUnits = true;
    public static Array<TileLogItem>[][] tileLogs = null;
    public static long lastSyncTime = 0;

    public static void update(){
        PowerGridFinder.INSTANCE.updatePower();

        AutoItemTransfer.runTransfers();

        for(int i = 0; i < 50; i += 1){
            if(configRequests.size > 0){
                configRequests.removeFirst().runRequest();
                if(configRequests.size % 10 == 0){
                    System.out.printf("%s left...%n\n", configRequests.size);
                }
                if(configRequests.size == 0){
                    System.out.println("Done!!");
                }
            }
        }

        if(following != null && following != player){
            float dx = player.x - following.x;
            float dy = player.y - following.y;
            player.moveBy(Mathf.clamp(-dx, -player.mech.maxSpeed, player.mech.maxSpeed),
            Mathf.clamp(-dy, -player.mech.maxSpeed, player.mech.maxSpeed));
            player.isShooting = following.isShooting;
            player.rotation = following.rotation;
            if(player.buildQueue() != following.buildQueue()){
                player.buildQueue().clear();
                for(BuildRequest b : following.buildQueue()){
                    if(breakingFollowing){
                        b.breaking = !b.breaking;
                    }
                    player.buildQueue().addLast(b);
                }
            }
        }

        if(input.ctrl() && input.keyTap(KeyCode.F)){
            ui.find.show();
        }

        if(scene.getKeyboardFocus() == null && control.input.block == null){
            float speed = (8F / renderer.getScale()) * Time.delta();
            if(Core.input.keyDown(KeyCode.LEFT) || Core.input.keyDown(KeyCode.RIGHT) ||
            Core.input.keyDown(KeyCode.UP) || Core.input.keyDown(KeyCode.DOWN)){
                if(cameraPositionOverride == null){
                    cameraPositionOverride = new Vec2(player.x, player.y);
                }
            }

            if(Core.input.keyDown(KeyCode.RIGHT)){
                cameraPositionOverride.x += speed;
            }

            if(Core.input.keyDown(KeyCode.LEFT)){
                cameraPositionOverride.x -= speed;
            }

            if(Core.input.keyDown(KeyCode.UP)){
                cameraPositionOverride.y += speed;
            }

            if(Core.input.keyDown(KeyCode.DOWN)){
                cameraPositionOverride.y -= speed;
            }
            if(Core.input.keyDown(Binding.zoom_in)){
                renderer.scaleCamera(0.5f);
            }
            if(Core.input.keyDown(Binding.zoom_out)){
                renderer.scaleCamera(-0.5f);
            }
            if(input.keyTap(Binding.xray_toggle)){
                xray = !xray;
            }
        }
    }

    public static void initialize() {
        Events.on(MessageBlockChangeEvent.class, (event) -> {
            String message = MessageSystem.readMessage(event.pos);
            if (message != null) {
                ECDH.handleMessage(event.sender, message);
            }
        });
        Events.on(WorldLoadEvent.class, () -> {
            if (Math.abs(lastSyncTime - Instant.now().getEpochSecond()) > 1)
            tileLogs = new Array[world.height()][world.width()];
            MessageSystem.scanForMessages();
        });
    }
}
