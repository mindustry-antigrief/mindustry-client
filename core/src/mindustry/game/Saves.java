package mindustry.game;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.GameState.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.io.SaveIO.*;
import mindustry.maps.Map;
import mindustry.type.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import static mindustry.Vars.*;

public class Saves{
    private static final DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance();

    final Seq<SaveSlot> saves = new Seq<>(0);
    @Nullable SaveSlot current;
    private @Nullable SaveSlot lastSectorSave;
    private boolean saving;
    private float time;
    public boolean hasLoaded;

    long totalPlaytime;
    private long lastTimestamp;

    private final LinkedBlockingDeque<Runnable> previewQueue = new LinkedBlockingDeque<>();

    public Saves(){
        Events.on(StateChangeEvent.class, event -> {
            if(event.to == State.menu){
                totalPlaytime = 0;
                lastTimestamp = 0;
                current = null;
            }
        });
    }

    /** Loads all saves */
    public void load(){
        load(false);
    }

    /** Loads all saves (unless sectorsOnly is specified in which case only sectors are loaded in order to make campaign load faster). */
    public void load(boolean sectorsOnly){
        Time.mark(); Time.mark();
        hasLoaded = true;

        var tasks = new Seq<Future<SaveSlot>>();
        saveDirectory.walk(file -> {
            var name = file.name();
            if (name.endsWith("backup.msav") || !name.endsWith(".msav") || sectorsOnly && !name.startsWith("sector-")) return;
            tasks.add(mainExecutor.submit(() -> {
                try{
                    var s = new SaveSlot(file, SaveIO.getMeta(file));
                    //clear saves from build <130 that had the new naval sectors.
                    if(s.getSector() != null && (s.getSector().id == 108 || s.getSector().id == 216) && s.meta.build <= 130 && s.meta.build > 0){
                        s.getSector().clearInfo();
                        s.file.delete();
                    }
                    return s;
                }catch(Throwable e){
                    Log.err("Failed to load save '" + file.name() + "'", e);
                    return null;
                }
            }));
        });
        var queued = Time.elapsed();

        Time.mark();
        saves.ensureCapacity(tasks.size);
        for(Future<SaveSlot> task : tasks){
            var s = Threads.await(task);
            if(s != null) saves.add(s);
        }
        var blocked = Time.elapsed();
        var loaded = Time.elapsed();

        Time.mark();
        lastSectorSave = saves.find(s -> s.isSector() && s.getName().equals(Core.settings.getString("last-sector-save", "<none>")));

        //automatically (re)assign sector save slots
        content.planets().each(p -> p.sectors.each(s -> s.save = null)); // FINISHME: This is most likely a horrible idea
        for(SaveSlot slot : saves){
            if(slot.getSector() != null){
                if(slot.getSector().save != null){
                    Log.warn("Sector @ has two corresponding saves: @ and @", slot.getSector(), slot.getSector().save.file, slot.file);
                }
                slot.getSector().save = slot;
            }
        }

        Log.debug("Queued saves in: @ms | Loaded @ saves in: @ms | Blocked for: @ms | Post processed saves in in @ms", queued, saves.size, loaded, blocked, Time.elapsed());
    }

    /** Unload all saves to reclaim resources */
    public void unload(){
        saves.each(SaveSlot::unloadTexture);
        saves.clear().shrink(); // Don't want all of this stuff in memory
    }

    public @Nullable SaveSlot getLastSector(){
        return lastSectorSave;
    }

    public @Nullable SaveSlot getCurrent(){
        return current;
    }

    public void update(){
        if(current != null && state.isGame()
        && !(state.isPaused() && Core.scene.hasDialog())){
            if(lastTimestamp != 0){
                totalPlaytime += Time.timeSinceMillis(lastTimestamp);
            }
            lastTimestamp = Time.millis();
        }

        var start = Time.millis();
        while (previewQueue.size() > 0 && Time.timeSinceMillis(start) < 15) {
            previewQueue.pop().run();
        }

        if(state.isGame() && !state.gameOver && current != null && current.isAutosave()){
            time += Time.delta;
            if(time > Core.settings.getInt("saveinterval") * 60){
                saving = true;

                try{
                    current.save();
                }catch(Throwable t){
                    Log.err(t);
                }

                Time.runTask(3f, () -> saving = false);

                time = 0;
            }
        }else{
            time = 0;
        }
    }

    public long getTotalPlaytime(){
        return totalPlaytime;
    }

    public void resetSave(){
        current = null;
    }

    public boolean isSaving(){
        return saving;
    }

    public Fi getSectorFile(Sector sector){
        return saveDirectory.child("sector-" + sector.planet.name + "-" + sector.id + "." + saveExtension);
    }

    public void saveSector(Sector sector){
        if(sector.save == null){
            sector.save = new SaveSlot(getSectorFile(sector));
            sector.save.setName(sector.save.file.nameWithoutExtension());
            saves.add(sector.save);
            if (saves.size == 1) unload();
        }
        sector.save.setAutosave(true);
        sector.save.save();
        lastSectorSave = sector.save;
        Core.settings.put("last-sector-save", sector.save.getName());
    }

    public SaveSlot addSave(String name){
        SaveSlot slot = new SaveSlot(getNextSlotFile());
        slot.setName(name);
        saves.add(slot);
        if (saves.size == 1) unload();
        slot.save();
        return slot;
    }

    public SaveSlot importSave(Fi file) throws IOException{
        SaveSlot slot = new SaveSlot(getNextSlotFile());
        slot.importFile(file);
        slot.setName(file.nameWithoutExtension());

        saves.add(slot);
        if (saves.size == 1) unload();
        slot.meta = SaveIO.getMeta(slot.file);
        current = slot;
        return slot;
    }

    public Fi getNextSlotFile(){
        int i = 0;
        Fi file;
        while((file = saveDirectory.child(i + "." + saveExtension)).exists()){
            i ++;
        }
        return file;
    }

    public Seq<SaveSlot> getSaveSlots(){
        if(saves.isEmpty()) load();
        return saves;
    }

    public int saveCount(){
        return saveDirectory.findAll(f -> !f.name().contains("backup") && f.extEquals("msav")).size;
    }

    public void deleteAll(){
        var needsLoad = saves.isEmpty();
        if(needsLoad) load(); // Need to load them in order to delete them.
        saves.filter(s -> !s.isSector()).each(SaveSlot::delete); // Delete non sectors
        if(needsLoad) unload(); // Unload if we just loaded
    }

    public class SaveSlot{
        public final Fi file;
        private volatile TextureRegion preview;
        public SaveMeta meta;

        public SaveSlot(Fi file){
            this(file, null);
        }

        public SaveSlot(Fi file, SaveMeta meta){
            this.file = file;
            this.meta = meta;
        }

        public void load() throws SaveException{
            try{
                SaveIO.load(file);
                meta = SaveIO.getMeta(file);
                current = this;
                totalPlaytime = meta.timePlayed;
                savePreview();
            }catch(Throwable e){
                throw new SaveException(e);
            }
        }

        public void save(){
            long prev = totalPlaytime;

            SaveIO.save(file);
            meta = SaveIO.getMeta(file);
            if(state.isGame()){
                current = this;
            }

            totalPlaytime = prev;
            savePreview();
        }

        private void savePreview(){
            mainExecutor.submit(() -> {
                try{
                    previewFile().writePng(renderer.minimap.getPixmap());
                    previewQueue.add(this::unloadTexture);
                }catch(Throwable t){
                    Log.err(t);
                }
            });
        }

        /** Asynchronously loads this save's preview on demand */
        public TextureRegion previewTexture(){
            if(preview == null){
                preview = Core.atlas.find("nomap"); // Prevents loading twice
                if(previewFile().exists()){
                    mainExecutor.execute(() -> {
                        if (preview == null) return; // Don't load the preview at all if it's not needed (prevents most of the pixmaps loading late)
                        var data = TextureData.load(previewFile(), false);
                        data.prepare();
                        previewQueue.add(() -> {
                            if (preview == null) { // By the time the pixmap finished loading, we no longer needed it, so we don't create a texture.
                                data.consumePixmap().dispose(); // Since we don't create a texture, we need to manually dispose the pixmap.
                                return;
                            }
                            preview = new TextureRegion(new Texture(data));
                        });
                    });
                }
            }
            return preview;
        }

        public void unloadTexture(){
            if(preview != null && preview != Core.atlas.find("nomap")) preview.texture.dispose();
            preview = null;
        }

        private String index(){
            return file.nameWithoutExtension();
        }

        private Fi previewFile(){
            return mapPreviewDirectory.child("save_slot_" + index() + ".png");
        }

        public boolean isHidden(){
            return isSector();
        }

        public String getPlayTime(){
            return Strings.formatMillis(current == this ? totalPlaytime : meta.timePlayed);
        }

        public long getTimestamp(){
            return meta.timestamp;
        }

        public String getDate(){
            return dateFormat.format(new Date(meta.timestamp));
        }

        public Map getMap(){
            return meta.map;
        }

        public void cautiousLoad(Runnable run){
            Seq<String> mods = Seq.with(getMods());
            mods.removeAll(Vars.mods.getModStrings());

            if(!mods.isEmpty()){
                ui.showConfirm("@warning", Core.bundle.format("mod.missing", mods.toString("\n")), run);
            }else{
                run.run();
            }
        }

        public String getName(){
            return Core.settings.getString("save-" + index() + "-name", "untitled");
        }

        public void setName(String name){
            Core.settings.put("save-" + index() + "-name", name);
        }

        public String[] getMods(){
            return meta.mods;
        }

        public @Nullable Sector getSector(){
            return meta == null || meta.rules == null ? null : meta.rules.sector;
        }

        public boolean isSector(){
            return getSector() != null;
        }

        public Gamemode mode(){
            return meta.rules.mode();
        }

        public int getBuild(){
            return meta.build;
        }

        public int getWave(){
            return meta.wave;
        }

        public boolean isAutosave(){
            return Core.settings.getBool("save-" + index() + "-autosave", true);
        }

        public void setAutosave(boolean save){
            Core.settings.put("save-" + index() + "-autosave", save);
        }

        public void importFile(Fi from) throws IOException{
            try{
                from.copyTo(file);
                if(previewFile().exists()){
                    unloadTexture();
                    previewFile().delete();
                }
            }catch(Exception e){
                throw new IOException(e);
            }
        }

        public void exportFile(Fi to) throws IOException{
            try{
                file.copyTo(to);
            }catch(Exception e){
                throw new IOException(e);
            }
        }

        public void delete(){
            if(SaveIO.backupFileFor(file).exists()){
                SaveIO.backupFileFor(file).delete();
            }
            file.delete();
            saves.remove(this, true);
            if(this == current){
                current = null;
            }

            unloadTexture();
        }
    }
}
