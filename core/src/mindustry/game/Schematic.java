package mindustry.game;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import mindustry.content.*;
import mindustry.mod.Mods.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.storage.*;
import arc.math.geom.*;
import arc.util.serialization.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.logic.*;
import mindustry.io.TypeIO.*;

import static mindustry.Vars.*;

public class Schematic implements Publishable, Comparable<Schematic>{
    public final Seq<Stile> tiles;
    /** These are used for the schematic tag UI. */
    public Seq<String> labels = new Seq<>();
    /** Internal meta tags. */
    public StringMap tags;
    public int width, height;
    public @Nullable Fi file;
    /** Associated mod. If null, no mod is associated with this schematic. */
    public @Nullable LoadedMod mod;

    public Schematic(Seq<Stile> tiles, StringMap tags, int width, int height){
        this.tiles = tiles;
        this.tags = tags;
        this.width = width;
        this.height = height;
    }

    public float powerProduction(){
        return tiles.sumf(s -> s.block instanceof PowerGenerator p ? p.getDisplayedPowerProduction() : 0f);
    }

    public float powerConsumption(){
        return tiles.sumf(s -> s.block.consPower != null ? s.block.consPower.usage : 0f);
    }

    public ItemSeq requirements(){
        return requirements(new ItemSeq());
    }

    public ItemSeq requirements(ItemSeq requirements){
        tiles.each(t -> {
            for(ItemStack stack : t.block.requirements){
                requirements.add(stack.item, stack.amount);
            }
        });

        return requirements;
    }

    public boolean hasCore(){
        return tiles.contains(s -> s.block instanceof CoreBlock);
    }

    public CoreBlock findCore(){
        Stile tile = tiles.find(s -> s.block instanceof CoreBlock);
        if(tile == null) throw new IllegalArgumentException("Schematic is missing a core!");
        return (CoreBlock)tile.block;
    }

    public String name(){
        return tags.get("name", "unknown");
    }

    public String description(){
        return tags.get("description", "");
    }

    public long getCreatedAt(){
        if(tags.containsKey("created")){
            try{
                return Long.parseLong(tags.get("created"));
            }catch(NumberFormatException ignored){}
        }
        if(file != null){
            try{
                BasicFileAttributes attr = Files.readAttributes(file.file().toPath(), BasicFileAttributes.class);
                return attr.creationTime().toMillis();
            }catch(Exception ignored){
                return file.lastModified();
            }
        }
        return 0;
    }

    public long getModifiedAt(){
        if(tags.containsKey("modified")){
            try{
                return Long.parseLong(tags.get("modified"));
            }catch(NumberFormatException ignored){}
        }
        return file != null ? file.lastModified() : 0;
    }

    public void save(){
        schematics.saveChanges(this);
    }

    @Override
    public String getSteamID(){
        return tags.get("steamid");
    }

    @Override
    public void addSteamID(String id){
        tags.put("steamid", id);
        save();
    }

    @Override
    public void removeSteamID(){
        tags.remove("steamid");
        save();
    }

    @Override
    public String steamTitle(){
        return name();
    }

    @Override
    public String steamDescription(){
        return description();
    }

    @Override
    public String steamTag(){
        return "schematic";
    }

    @Override
    public Fi createSteamFolder(String id){
        Fi directory = tmpDirectory.child("schematic_" + id).child("schematic." + schematicExtension);
        file.copyTo(directory);
        return directory;
    }

    @Override
    public Fi createSteamPreview(String id){
        Fi preview = tmpDirectory.child("schematic_preview_" + id + ".png");
        schematics.savePreview(this, preview);
        return preview;
    }

    @Override
    public int compareTo(Schematic schematic){
        return name().compareTo(schematic.name());
    }

    public String writeJson(){
        StringBuilder sb = new StringBuilder(2048);
        writeJson(sb);
        return sb.toString();
    }

    public void writeJson(StringBuilder sb){
        sb.append("{\n");
        sb.append("  \"name\": ").append(escapeJson(name())).append(",\n");
        sb.append("  \"description\": ").append(escapeJson(description())).append(",\n");
        sb.append("  \"width\": ").append(width).append(",\n");
        sb.append("  \"height\": ").append(height).append(",\n");
        sb.append("  \"area\": ").append(width * height).append(",\n");
        sb.append("  \"powerProduction\": ").append(powerProduction()).append(",\n");
        sb.append("  \"powerConsumption\": ").append(powerConsumption()).append(",\n");
        
        sb.append("  \"requirements\": {\n");
        ItemSeq reqs = requirements();
        boolean firstReq = true;
        for(ItemStack stack : reqs){
            if(stack.amount > 0){
                if(!firstReq) sb.append(",\n");
                sb.append("    ").append(escapeJson(stack.item.name)).append(": ").append(stack.amount);
                firstReq = false;
            }
        }
        sb.append("\n  },\n");

        sb.append("  \"tiles\": [\n");
        for(int i = 0; i < tiles.size; i++){
            Stile tile = tiles.get(i);
            sb.append("    {\n");
            sb.append("      \"block\": ").append(escapeJson(tile.block.name)).append(",\n");
            sb.append("      \"x\": ").append(tile.x).append(",\n");
            sb.append("      \"y\": ").append(tile.y).append(",\n");
            sb.append("      \"rotation\": ").append(tile.rotation).append(",\n");
            sb.append("      \"config\": ");
            configToJson(tile.config, sb);
            sb.append("\n    }");
            if(i < tiles.size - 1){
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
    }

    private static String escapeJson(String str){
        if(str == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for(int i = 0; i < str.length(); i++){
            char ch = str.charAt(i);
            switch(ch){
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if(ch < ' '){
                        String hex = Integer.toHexString(ch);
                        sb.append("\\u");
                        for(int k = 0; k < 4 - hex.length(); k++){
                            sb.append('0');
                        }
                        sb.append(hex);
                    }else{
                        sb.append(ch);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String configToJson(Object config){
        StringBuilder sb = new StringBuilder();
        configToJson(config, sb);
        return sb.toString();
    }

    private static void configToJson(Object config, StringBuilder sb){
        if(config == null){
            sb.append("null");
            return;
        }
        if(config instanceof Boolean || config instanceof Number){
            sb.append(config.toString());
            return;
        }
        if(config instanceof String){
            sb.append(escapeJson((String)config));
            return;
        }
        if(config instanceof MappableContent){
            sb.append(escapeJson(((MappableContent)config).name));
            return;
        }
        if(config instanceof Point2){
            Point2 p = (Point2)config;
            sb.append("[").append(p.x).append(", ").append(p.y).append("]");
            return;
        }
        if(config instanceof Point2[]){
            Point2[] arr = (Point2[])config;
            sb.append("[");
            for(int i = 0; i < arr.length; i++){
                if(i > 0) sb.append(", ");
                if(arr[i] == null){
                    sb.append("null");
                }else{
                    sb.append("[").append(arr[i].x).append(", ").append(arr[i].y).append("]");
                }
            }
            sb.append("]");
            return;
        }
        if(config instanceof TechTree.TechNode){
            sb.append(escapeJson(((TechTree.TechNode)config).content.name));
            return;
        }
        if(config instanceof Building){
            sb.append(((Building)config).pos());
            return;
        }
        if(config instanceof BuildingBox){
            sb.append(((BuildingBox)config).pos);
            return;
        }
        if(config instanceof LAccess){
            sb.append(escapeJson(((LAccess)config).name()));
            return;
        }
        if(config instanceof byte[]){
            byte[] bytes = (byte[])config;
            sb.append("[");
            for(int i = 0; i < bytes.length; i++){
                if(i > 0) sb.append(", ");
                sb.append(bytes[i]);
            }
            sb.append("]");
            return;
        }
        if(config instanceof boolean[]){
            boolean[] arr = (boolean[])config;
            sb.append("[");
            for(int i = 0; i < arr.length; i++){
                if(i > 0) sb.append(", ");
                sb.append(arr[i]);
            }
            sb.append("]");
            return;
        }
        if(config instanceof Unit){
            sb.append(((Unit)config).id);
            return;
        }
        if(config instanceof UnitBox){
            sb.append(((UnitBox)config).id);
            return;
        }
        if(config instanceof Vec2[]){
            Vec2[] arr = (Vec2[])config;
            sb.append("[");
            for(int i = 0; i < arr.length; i++){
                if(i > 0) sb.append(", ");
                if(arr[i] == null){
                    sb.append("null");
                }else{
                    sb.append("[").append(arr[i].x).append(", ").append(arr[i].y).append("]");
                }
            }
            sb.append("]");
            return;
        }
        if(config instanceof Vec2){
            Vec2 v = (Vec2)config;
            sb.append("[").append(v.x).append(", ").append(v.y).append("]");
            return;
        }
        if(config instanceof Team){
            sb.append(escapeJson(((Team)config).name));
            return;
        }
        if(config instanceof int[]){
            int[] arr = (int[])config;
            sb.append("[");
            for(int i = 0; i < arr.length; i++){
                if(i > 0) sb.append(", ");
                sb.append(arr[i]);
            }
            sb.append("]");
            return;
        }
        if(config instanceof IntSeq){
            IntSeq arr = (IntSeq)config;
            sb.append("[");
            for(int i = 0; i < arr.size; i++){
                if(i > 0) sb.append(", ");
                sb.append(arr.get(i));
            }
            sb.append("]");
            return;
        }
        if(config instanceof Object[]){
            Object[] arr = (Object[])config;
            sb.append("[");
            for(int i = 0; i < arr.length; i++){
                if(i > 0) sb.append(", ");
                configToJson(arr[i], sb);
            }
            sb.append("]");
            return;
        }
        sb.append(escapeJson(config.toString()));
    }

    public static Schematic readJson(String jsonString){
        Seq<Schematic> list = readJsonList(jsonString);
        if(list.isEmpty()) throw new IllegalArgumentException("No valid schematics found in JSON");
        return list.first();
    }

    public static Seq<Schematic> readJsonList(String jsonString){
        Seq<Schematic> list = new Seq<>();
        if(jsonString == null) return list;
        if(jsonString.length() > 32 * 1024 * 1024){
            throw new IllegalArgumentException("JSON string too large (limit is 32MB)");
        }
        JsonValue root = null;
        try{
            root = new JsonReader().parse(jsonString);
        }catch(Exception e){
            Log.err("Failed to parse JSON string: " + e.getMessage(), e);
            return list;
        }
        if(root == null) return list;
        
        if(root.isArray()){
            Log.info("Attempting to import bulk JSON schematics list, size: " + root.size);
            for(int i = 0; i < root.size; i++){
                try{
                    list.add(readJsonElement(root.get(i)));
                }catch(Exception e){
                    Log.err("Error importing schematic at array index " + i + ": " + e.getMessage(), e);
                }
            }
        }else if(root.isObject()){
            try{
                list.add(readJsonElement(root));
            }catch(Exception e){
                Log.err("Error importing single schematic: " + e.getMessage(), e);
            }
        }
        Log.info("Imported " + list.size + " JSON schematics successfully.");
        return list;
    }

    private static Schematic readJsonElement(JsonValue root){
        if(root == null) throw new IllegalArgumentException("Invalid JSON content");
        
        String name = root.getString("name", "unknown");
        String description = root.getString("description", "");
        int width = root.getInt("width", 0);
        int height = root.getInt("height", 0);
        if(width < 1 || height < 1 || width > 512 || height > 512){
            throw new IllegalArgumentException("Invalid schematic dimensions: " + width + "x" + height);
        }
        
        StringMap tags = new StringMap();
        tags.put("name", name);
        tags.put("description", description);
        
        Seq<Stile> tiles = new Seq<>();
        JsonValue tilesVal = root.get("tiles");
        if(tilesVal != null && tilesVal.isArray()){
            for(int i = 0; i < tilesVal.size; i++){
                JsonValue tileVal = tilesVal.get(i);
                String blockName = tileVal.getString("block");
                Block block = content.block(blockName);
                if(block == null){
                    Log.warn("Block '" + blockName + "' not found while importing schematic '" + name + "'. Skipping tile.");
                    continue;
                }
                
                int x = tileVal.getInt("x");
                int y = tileVal.getInt("y");
                byte rotation = (byte)tileVal.getInt("rotation", 0);
                
                JsonValue configVal = tileVal.get("config");
                Object config = null;
                try{
                    config = jsonToConfig(block, configVal);
                }catch(Exception e){
                    Log.err("Failed to parse config for block '" + blockName + "' at (" + x + "," + y + ") in schematic '" + name + "': " + e.getMessage(), e);
                }
                
                tiles.add(new Stile(block, x, y, config, rotation));
            }
        }
        return new Schematic(tiles, tags, width, height);
    }

    private static Object jsonToConfig(Block block, JsonValue val){
        if(val == null || val.isNull()) return null;
        
        if(block.configurations.containsKey(Point2.class)){
            Object parsed = valToType(val, Point2.class);
            if(parsed != null) return parsed;
        }
        
        for(Class<?> type : block.configurations.keys()){
            if(type != void.class && type != Point2.class){
                Object parsed = valToType(val, type);
                if(parsed != null){
                    return parsed;
                }
            }
        }
        
        if(val.isString()) return val.asString();
        if(val.isNumber()) return val.asInt();
        if(val.isBoolean()) return val.asBoolean();
        return null;
    }

    private static Object valToType(JsonValue val, Class<?> expected){
        if(val == null || val.isNull()) return null;
        
        if(expected == String.class){
            return val.asString();
        }
        if(expected == Integer.class || expected == int.class){
            if(val.isArray() && val.size == 2 && !val.get(0).isArray()){
                return Point2.pack(val.get(0).asInt(), val.get(1).asInt());
            }
            if(val.isString()){
                String name = val.asString();
                for(ContentType type : ContentType.values()){
                    var c = content.getByName(type, name);
                    if(c != null){
                        return (int)c.id;
                    }
                }
            }
            return val.asInt();
        }
        if(expected == Float.class || expected == float.class){
            return val.asFloat();
        }
        if(expected == Double.class || expected == double.class){
            return val.asDouble();
        }
        if(expected == Boolean.class || expected == boolean.class){
            return val.asBoolean();
        }
        if(expected == Long.class || expected == long.class){
            return val.asLong();
        }
        if(expected == Short.class || expected == short.class){
            if(val.isArray() && val.size == 2 && !val.get(0).isArray()){
                return (short)(((val.get(0).asInt() & 0xFF) << 8) | (val.get(1).asInt() & 0xFF));
            }
            return val.asShort();
        }
        if(expected == Byte.class || expected == byte.class){
            return val.asByte();
        }
        if(expected == Point2.class){
            if(val.isArray() && val.size == 2 && !val.get(0).isArray()){
                return new Point2(val.get(0).asInt(), val.get(1).asInt());
            }
            return null;
        }
        if(expected == Point2[].class){
            if(val.isArray()){
                Seq<Point2> temp = new Seq<>();
                for(int i = 0; i < val.size; i++){
                    JsonValue item = val.get(i);
                    if(item != null && item.isArray() && item.size == 2 && !item.get(0).isArray()){
                        temp.add(new Point2(item.get(0).asInt(), item.get(1).asInt()));
                    }else if(item != null && !item.isNull()){
                        throw new IllegalArgumentException("Invalid Point2 element at index " + i);
                    }
                }
                return temp.toArray(Point2.class);
            }
            return null;
        }
        if(expected == Vec2.class){
            if(val.isArray() && val.size == 2 && !val.get(0).isArray()){
                return new Vec2(val.get(0).asFloat(), val.get(1).asFloat());
            }
            return null;
        }
        if(expected == Vec2[].class){
            if(val.isArray()){
                Seq<Vec2> temp = new Seq<>();
                for(int i = 0; i < val.size; i++){
                    JsonValue item = val.get(i);
                    if(item != null && item.isArray() && item.size == 2 && !item.get(0).isArray()){
                        temp.add(new Vec2(item.get(0).asFloat(), item.get(1).asFloat()));
                    }else if(item != null && !item.isNull()){
                        throw new IllegalArgumentException("Invalid Vec2 element at index " + i);
                    }
                }
                return temp.toArray(Vec2.class);
            }
            return null;
        }
        if(expected == byte[].class){
            if(val.isArray()){
                byte[] arr = new byte[val.size];
                for(int i = 0; i < val.size; i++){
                    arr[i] = val.get(i).asByte();
                }
                return arr;
            }
            return null;
        }
        if(expected == boolean[].class){
            if(val.isArray()){
                boolean[] arr = new boolean[val.size];
                for(int i = 0; i < val.size; i++){
                    arr[i] = val.get(i).asBoolean();
                }
                return arr;
            }
            return null;
        }
        if(expected == int[].class){
            if(val.isArray()){
                int[] arr = new int[val.size];
                for(int i = 0; i < val.size; i++){
                    arr[i] = val.get(i).asInt();
                }
                return arr;
            }
            return null;
        }
        if(expected == IntSeq.class){
            if(val.isArray()){
                IntSeq arr = new IntSeq();
                for(int i = 0; i < val.size; i++){
                    arr.add(val.get(i).asInt());
                }
                return arr;
            }
            return null;
        }
        if(expected == Object[].class){
            if(val.isArray()){
                Object[] arr = new Object[val.size];
                for(int i = 0; i < val.size; i++){
                    arr[i] = valToType(val.get(i), Object.class);
                }
                return arr;
            }
            return null;
        }
        if(expected == Object.class){
            if(val == null || val.isNull()) return null;
            if(val.isString()) return val.asString();
            if(val.isBoolean()) return val.asBoolean();
            if(val.isNumber()){
                double d = val.asDouble();
                if(d == (int)d){
                    return (int)d;
                }
                return val.asFloat();
            }
            if(val.isArray()){
                Object[] arr = new Object[val.size];
                for(int i = 0; i < val.size; i++){
                    arr[i] = valToType(val.get(i), Object.class);
                }
                return arr;
            }
            return null;
        }
        
        if(MappableContent.class.isAssignableFrom(expected)){
            if(val.isString()){
                String name = val.asString();
                for(ContentType type : ContentType.values()){
                    var c = content.getByName(type, name);
                    if(c != null && expected.isInstance(c)){
                        return c;
                    }
                }
            }
            return null;
        }
        
        if(expected == Team.class){
            if(val.isString()){
                String teamName = val.asString();
                for(var t : Team.all){
                    if(t.name.equalsIgnoreCase(teamName)) return t;
                }
            }
            if(val.isNumber()){
                return Team.get(val.asInt());
            }
            return null;
        }
        
        if(expected == LAccess.class){
            if(val.isString()){
                try{
                    return LAccess.valueOf(val.asString());
                }catch(Exception ignored){}
            }
            return null;
        }
        
        if(expected == Building.class){
            if(val.isNumber()) return world.build(val.asInt());
            return null;
        }
        if(expected == BuildingBox.class){
            if(val.isNumber()) return new BuildingBox(val.asInt());
            return null;
        }
        if(expected == Unit.class){
            if(val.isNumber()) return Groups.unit.getByID(val.asInt());
            return null;
        }
        if(expected == UnitBox.class){
            if(val.isNumber()) return new UnitBox(val.asInt());
            return null;
        }
        
        return null;
    }

    public static class Stile{
        public Block block;
        public short x, y;
        public Object config;
        public byte rotation;

        public Stile(Block block, int x, int y, Object config, byte rotation){
            this.block = block;
            this.x = (short)x;
            this.y = (short)y;
            this.config = config;
            this.rotation = rotation;
        }

        //pooling only
        public Stile(){
            block = Blocks.air;
        }

        public Stile set(Stile other){
            block = other.block;
            x = other.x;
            y = other.y;
            config = other.config;
            rotation = other.rotation;
            return this;
        }

        public Stile copy(){
            return Pools.obtain(Stile.class, Stile::new).set(this);
        }
    }
}
