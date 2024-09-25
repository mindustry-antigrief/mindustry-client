package mindustry.game;

import arc.*;
import arc.files.*;
import arc.func.*;
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
    /** Whether the saves were fully loaded last time. */
    public boolean hasLoaded;

    /** Whether we are currently loading or cancelling loading */
    public boolean loading, cancelling;

    long totalPlaytime;
    private long lastTimestamp;

    private final LinkedBlockingQueue<Runnable> previewQueue = new LinkedBlockingQueue<>();
    private SaveSlot createPreviewFor;

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
        load(sectorsOnly, null);
    }

    /** Loads all saves (unless sectorsOnly is specified in which case only sectors are loaded in order to make campaign load faster).
     * @param cons Called every time a save is loaded. Called with a null argument when all saves have been loaded */
    public void load(boolean sectorsOnly, Cons<SaveSlot> cons){
        hasLoaded = false;
        Log.debug("Loading saves");
        var start = Time.nanos();
        unload(); // Clear saves (prevents loading two sets of saves at once)
        Time.mark();

        class FiTi{
            final Fi fi;
            final long ti;

            FiTi(Fi fi, long ti){
                this.fi = fi;
                this.ti = ti;
            }
        }
        var tasks = new Seq<Future<SaveSlot>>();
        var files = new Seq<FiTi>();
        saveDirectory.walk(file -> {
            var name = file.name();
            if(name.endsWith("backup.msav") || !name.endsWith(".msav") || sectorsOnly && !name.startsWith("sector-")) return;
            if(cons != null) files.add(new FiTi(file, -file.lastModified()));
            else tasks.add(mainExecutor.submit(callableFor(file)));
        });

        if(cons != null){
            files.sort(f -> f.ti);
            files.each(f -> tasks.add(mainExecutor.submit(callableFor(f.fi))));
        }

        var queued = Time.elapsed();
        var blocked = Time.nanos();
        saves.ensureCapacity(tasks.size);
        lastSectorSave = null;
        content.planets().each(p -> p.sectors.each(s -> s.save = null)); // FINISHME: This is most likely a horrible idea
        long waited = 0;
        if(cons == null){ // Blocking sync
            for(Future<SaveSlot> task : tasks){
                long wait = Time.nanos();
                var s = Threads.await(task);
                waited += Time.nanos() - wait;
                if(s != null) processSave(s);
            }
            saves.shrink();
            Log.debug("Queued saves in: @ms | Blocked for: @/@ms | Loaded @ saves in: @ms", queued, waited/(float)Time.nanosPerMilli, Time.millisSinceNanos(blocked), saves.size, Time.millisSinceNanos(start));
            hasLoaded = true;
        }else if(!loading){ // Non-blocking async
            cancelling = false;
            loading = true;
            for(var task : tasks){
                previewQueue.add(() -> {
                    var s = Threads.await(task);
                    if(s != null){
                        processSave(s);
                        cons.get(s);
                    }
                });
            }
            previewQueue.add(() -> { // Signifies that loading has completed
                if(cancelling){
                    hasLoaded = false;
                    Log.debug("Cancelled loading saves | Size: @", saves.size);
                    unload();
                    Log.debug("Cancelled loading saves (after unload) | Size: @", saves.size);
                }else{
                    Log.info("Loading saves asynchronously finished in @ms", Time.millisSinceNanos(start));
                    hasLoaded = true;
                    cons.get(null);
                }
                loading = false;
            });
        }
    }

    private Callable<SaveSlot> callableFor(Fi file){
        return () -> {
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
        };
    }

    private void processSave(SaveSlot s){
        saves.add(s);
        var sector = s.getSector();
        if(sector != null){
            if(lastSectorSave == null && s.getName().equals(Core.settings.getString("last-sector-save", "<none>"))) lastSectorSave = s;
            if(sector.save != null) Log.warn("Sector @ has two corresponding saves: @ and @", sector, sector.save.file, s.file);
            else sector.save = s;
        }
    }

    /** Unload all saves to reclaim resources */
    public void unload(){
        Log.debug("Unloading saves");
        cancelling = true;
        while(!previewQueue.isEmpty()) previewQueue.remove().run(); // Process the queue immediately (jank)
        saves.each(SaveSlot::unloadTexture);
        saves.clear().shrink(); // Don't want all of this stuff in memory
        createPreviewFor = null;
    }

    /** Doesn't attempt to recreate existing broken previews Vars.control.saves.createMissingPreviews() */
    public void createMissingPreviews(){
        load(); // reload saves to ensure we have them all loaded
        var missing = saves.select(s -> !s.previewFile().exists()).reverse();
        var originallyMissing = missing.size;
        Runnable[] next = {null};
        next[0] = () -> {
            var s = Time.millis();
            do{
                if(missing.isEmpty()){
                    ui.loadfrag.hide();
                    return;
                }
                var save = missing.pop();
                if(save.previewFile().exists()) continue; // Preview was already created by createPreviewFor (this is jank but whatever, it works and is better than creating the preview again as thats slow)
                createPreview(save);
            }while(Time.timeSinceMillis(s) < 1000);
            ui.loadfrag.setText(Core.bundle.format("client.save.createpreviews.progress", originallyMissing - missing.size, originallyMissing));
            ui.loadfrag.setProgress((originallyMissing - missing.size) / (float) originallyMissing);
            ui.loadfrag.snapProgress();
            Core.app.post(next[0]);
        };
        ui.loadfrag.show("[accent]" + Core.bundle.format("client.save.createpreviews.progress", 0, originallyMissing)); // Why does show() not add accent but setText() does smh
        ui.loadfrag.setButton(missing::clear);
        Time.runTask(7, next[0]); // Let the loading screen appear first
    }

    private void createPreview(SaveSlot slot){
        try{
            var pix = SaveIO.generatePreview(slot); // Very slow and expensive
            // The three methods below are intentionally not threaded as that can cause race conditions (even when posting slot.unloadTexture back to the main thread)
            slot.previewFile().writePng(pix);
            pix.dispose();
            slot.unloadTexture(); // Force the preview to be loaded from disk next frame (this is horrible and will cause unneeded reads, but it's super easy, so I'm doing it anyway)
        }catch(Throwable t){
            Log.err(t);
        }
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

        if(state.isGame() && !state.gameOver && current != null && current.isAutosave()){
            time += Time.delta;
            if(time > Core.settings.getInt("saveinterval") * 60 && !Vars.disableSave){
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

    /** Processes a portion of the existing preview/loading queue. Also creates the pending save preview if needed. */
    public long processQueue(long start, long lastPreview, int limit){
        int i = 0; // Time.millis() is not free and these are generally very small tasks, we should run Time.millis() less frequently to reduce the overhead
        while(!previewQueue.isEmpty() && ((i = (i + 1) % 5) == 0 || Time.timeSinceMillis(start) < limit)){
            previewQueue.remove().run();
        }

        if(Core.settings.getBool("createmissingsavepreviews") && createPreviewFor != null && Time.timeSinceMillis(start) < 1 && Time.timeSinceMillis(lastPreview) > 50){ // Only create the preview if we didn't do much work above
            createPreview(createPreviewFor);
            createPreviewFor = null;
            lastPreview = Time.millis();
        }
        return lastPreview;
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

    public int loadedSaveCount(){
        return saves.size;
    }

    public int saveCount(){
        return saveDirectory.findAll(f -> !f.name().contains("backup") && f.extEquals("msav")).size;
    }

    public void deleteAll(){
        var needsLoad = saves.isEmpty();
        if(needsLoad) load(); // Need to load them in order to delete them.
        saves.each(s -> !s.isSector(), SaveSlot::delete); // Delete non sectors
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
            mainExecutor.execute(() -> {
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
                        try {
                            var data = TextureData.load(previewFile(), false);
                            data.prepare();
                            previewQueue.add(() -> {
                                if (preview == null) { // By the time the pixmap finished loading, we no longer needed it, so we don't create a texture.
                                    data.consumePixmap().dispose(); // Since we don't create a texture, we need to manually dispose the pixmap.
                                    return;
                                }
                                preview = new TextureRegion(new Texture(data));
                            });
                        } catch (ArcRuntimeException e) {
                            previewFile().delete();
                            Log.err("Failed to load preview for " + file.path(), e);
                        }
                    });
                }
            }else if(createPreviewFor == null && preview == Core.atlas.find("nomap") && !previewFile().exists()){ // Doesn't have the default
                createPreviewFor = this;
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
