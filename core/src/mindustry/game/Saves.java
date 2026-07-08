package mindustry.game;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.io.SaveIO.*;
import mindustry.maps.Map;
import mindustry.type.*;
import mindustry.world.*;

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

    private SaveSlot createPreviewFor;
    private final BackgroundTask<Runnable> previewTask = new BackgroundTask<>() {
        @Override
        public boolean processStep() {
            if (!units.isEmpty()) units.removeFirst().run(); // Load previews

            var createPreviews = Core.settings.getBool("createmissingsavepreviews");
            if (createPreviewFor != null && createPreviews) { // Create missing save previews
                doExpensiveStep(50, () -> {
                    createPreview(createPreviewFor);
                    createPreviewFor = null;
                });
            }

            return units.isEmpty() && (createPreviewFor == null || !createPreviews);
        }
    };

    public Saves(){
        Events.on(StateChangeEvent.class, event -> {
            if(event.to == State.menu){
                totalPlaytime = 0;
                lastTimestamp = 0;
                current = null;
            }
        });
    }

    private void clearOldMegabaseSectors(){
        IntSet serpuloRemoval = IntSet.with(27, 245, 244, 243, 242, 247, 246, 237, 150, 157, 138, 251, 103);

        //clear old megabase sectors from the beta period
        saves.removeAll(s -> {
            if(s.getSector() != null && s.getSector().planet == Planets.serpulo && serpuloRemoval.contains(s.getSector().id) && s.meta.build < 157 && s.meta.build > 146){
                s.getSector().clearInfo();
                s.file.delete();
                return true;
            }
            return false;
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

        Seq<Remap> remaps = new Seq<>();
        ObjectSet<Sector> remapped = new ObjectSet<>();

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
                if(s != null) processSave(s, remaps, remapped);
            }
            clearOldMegabaseSectors();
            processRemaps(remaps, remapped);
            saves.shrink();
            Log.debug("Queued saves in: @ms | Blocked for: @/@ms | Loaded @ saves in: @ms", queued, waited/(float)Time.nanosPerMilli, Time.millisSinceNanos(blocked), saves.size, Time.millisSinceNanos(start));
            hasLoaded = true;
        }else if(!loading){ // Non-blocking async
            cancelling = false;
            loading = true;
            for(var task : tasks){
                previewTask.addUnit(() -> {
                    var s = Threads.await(task);
                    if(s != null){
                        processSave(s, remaps, remapped);
                        cons.get(s);
                    }
                });
            }
            previewTask.addUnit(() -> { // Signifies that loading has completed
                if(cancelling){
                    hasLoaded = false;
                    Log.debug("Cancelled loading saves | Size: @", saves.size);
                    unload();
                    Log.debug("Cancelled loading saves (after unload) | Size: @", saves.size);
                }else{
                    clearOldMegabaseSectors();
                    processRemaps(remaps, remapped);
                    saves.shrink();
                    Log.info("Loading saves asynchronously finished in @ms", Time.millisSinceNanos(start));
                    hasLoaded = true;
                    cons.get(null);
                }
                loading = false;
            });
        }
    }

    private void processRemaps(Seq<Remap> remaps, ObjectSet<Sector> remapped) {
        //process remaps later to allow swaps of sectors
        for(var remap : remaps){
            if(remap.sourceSector.planet == Planets.serpulo) Vars.hadSerpuloRemaps = true;
            var remapTarget = remap.destSector;

            //overwrite the target sector's info with the save's info
            Core.settings.putJson(remapTarget.planet.name + "-s-" + remapTarget.id + "-info", remap.sourceInfo);
            remapTarget.loadInfo();

            remapTarget.save = remap.slot;
            try{
                //move file from tmp directory back into the correct location
                remap.sourceFile.moveTo(remap.destFile);
                remap.slot.file = remap.destFile;
            }catch(Exception e){
                Log.err("Failed to move back sector files when remapping: " + remap.sourceSector.id + " -> " + remapTarget.id, e);
            }

            //clear the info, assuming it wasn't a sector that got mapped to
            if(!remapped.contains(remap.sourceSector)){
                remap.sourceSector.clearInfo();
            }
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

    private void processSave(SaveSlot s, Seq<Remap> remaps, ObjectSet<Sector> remapped){
        saves.add(s);
        var sector = s.getSector();
        if(sector != null){
            if(lastSectorSave == null && s.getName().equals(Core.settings.getString("last-sector-save", "<none>"))) lastSectorSave = s;

            String name = s.meta.tags.get("sectorPreset");
            Sector remapTarget = null;

            if(name != null){
                if(!name.isEmpty()){ //if this save had a preset defined...
                    SectorPreset preset = content.sector(name);
                    //...place it in the right sector according to its preset
                    if(preset != null && preset.sector != sector && preset.requireUnlock){
                        remapTarget = preset.sector;
                    }
                }
            }else{ //there was no sector preset in the meta at all, which means this is a legacy save that may need mapping
                SectorPreset target = content.sectors().find(se -> se.planet == sector.planet && se.originalPosition == sector.id);
                if(target != null && target.sector != sector && target.requireUnlock){ //there is indeed a sector preset that used to have this ID, and it needs remapping!
                    remapTarget = target.sector;
                }
            }

            if(remapTarget != null){
                //if the file name matches the destination of the remap, assume it has already been remapped, and skip the file movement procedure
                if(!s.file.equals(getSectorFile(remapTarget))){
                    Log.info("Remapping sector: @ -> @ (@)", sector.id, remapTarget.id, remapTarget.preset);

                    try{
                        SectorInfo info = Core.settings.getJson(sector.planet.name + "-s-" + sector.id + "-info", SectorInfo.class, SectorInfo::new);
                        Fi tmpRemapFile = saveDirectory.child("remap_" + sector.planet.name + "_" + sector.id + "." + saveExtension);
                        s.file.moveTo(tmpRemapFile);

                        remaps.add(new Remap(s, tmpRemapFile, sector, info, getSectorFile(remapTarget), remapTarget));
                        remapped.add(remapTarget);
                    }catch(Exception e){
                        Log.err("Failed to move sector files when remapping: " + sector.id + " -> " + remapTarget.id, e);
                    }
                }

                remapTarget.save = s;
                s.meta.rules.sector = remapTarget;

            }else{
                if(sector.save != null){
                    Log.warn("Sector @ has two corresponding saves: @ and @", sector, sector.save.file, s.file);
                }else{
                    sector.save = s;
                }
            }
        }
    }

    /** Unload all saves to reclaim resources */
    public void unload(){
        Log.debug("Unloading saves");
        cancelling = true;
        previewTask.block();
        previewTask.addUnit(() -> saves.each(SaveSlot::unloadTexture));
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
                long change = Time.timeSinceMillis(lastTimestamp);
                totalPlaytime += change;
                if(state.isCampaign()){
                    state.getPlanet().stats().playtime += change;
                }
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

        createPreviewFor = null; // If we enable the creation of previews we need to run the task again, this is an easy way to do so.
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

    private static class Remap{
        //file in the temp folder
        Fi sourceFile;
        //slot of source sector to move file for
        SaveSlot slot;
        Sector sourceSector;
        //sector info from source sector to move into
        SectorInfo sourceInfo;

        //file to copy to
        Fi destFile;
        //destination sector to move to
        Sector destSector;

        Remap(SaveSlot slot, Fi sourceFile, Sector sourceSector, SectorInfo sourceInfo, Fi destFile, Sector destSector){
            this.slot = slot;
            this.sourceFile = sourceFile;
            this.sourceSector = sourceSector;
            this.sourceInfo = sourceInfo;
            this.destFile = destFile;
            this.destSector = destSector;
        }
    }

    public class SaveSlot{
        public Fi file;
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
            load(world.context);
        }

        public void load(WorldContext context) throws SaveException{
            try{
                SaveIO.load(file, context);
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
                    previewTask.addUnit(this::unloadTexture);
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
                            previewTask.addUnit(() -> {
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
                previewTask.submit(); // preview creation uses the previewTask
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
            //TODO remap sectors
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

        public boolean isBeingPlayed(){
            return getCurrent() == this;
        }

        public boolean hasExternalAssets(){
            return meta.tags.getBool("hasExternalAssets");
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
                if(isBeingPlayed() && hasExternalAssets()){
                    SaveIO.write(to, new SaveOptions(){{
                        embedAssets = true;
                    }});
                }else{
                    file.copyTo(to);
                }
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
