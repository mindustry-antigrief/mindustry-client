package mindustry.core;

import arc.*;
import arc.assets.loaders.*;
import arc.assets.loaders.MusicLoader.*;
import arc.assets.loaders.SoundLoader.*;
import arc.audio.*;
import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.gen.*;

import java.net.*;
import java.util.*;

/** Handles files in a modded context. */
public class FileTree implements FileHandleResolver{
    private ObjectMap<String, Fi> files = new ObjectMap<>();
    private ObjectMap<String, Sound> loadedSounds = new ObjectMap<>();
    private ObjectMap<String, Music> loadedMusic = new ObjectMap<>();

    public void addFile(String path, Fi f){
        files.put(path.replace('\\', '/'), f);
    }

    /** Gets an asset file.*/
    public Fi get(String path){
        return get(path, false);
    }

    /** Gets an asset file.*/
    public Fi get(String path, boolean safe){
        if(files.containsKey(path)){
            return files.get(path);
        }else if(files.containsKey("/" + path)){
            return files.get("/" + path);
        }else if(Core.files == null && !safe){ //headless
            return Fi.get(path);
        }else{
            return Core.files.internal(path);
        }
    }

    /** Clears all mod files.*/
    public void clear(){
        files.clear();
    }

    @Override
    public Fi resolve(String fileName){
        return get(fileName);
    }

    /**
     * Loads a sound by name from the sounds/ folder. OGG and MP3 are supported; the extension is automatically added to the end of the file name.
     * Results are cached; consecutive calls to this method with the same name will return the same sound instance.
     * */
    public Sound loadSound(String soundName){
        if(Vars.headless) return Sounds.none;

        return loadedSounds.get(soundName, () -> {
            String name = "sounds/" + soundName;
            String path = Vars.tree.get(name + ".ogg").exists() ? name + ".ogg" : name + ".mp3";

            var sound = new Sound();
            var desc = Core.assets.load(path, Sound.class, new SoundParameter(sound));
            desc.errored = Throwable::printStackTrace;

            return sound;
        });
    }

    /**
     * Loads a music file by name from the music/ folder. OGG and MP3 are supported; the extension is automatically added to the end of the file name.
     * Results are cached; consecutive calls to this method with the same name will return the same music instance.
     * */
    public Music loadMusic(String musicName){
        if(Vars.headless) return new Music();

        return loadedMusic.get(musicName, () -> {
            String name = "music/" + musicName;
            String path = Vars.tree.get(name + ".ogg").exists() ? name + ".ogg" : name + ".mp3";

            var music = new Music();
            var desc = Core.assets.load(path, Music.class, new MusicParameter(music));
            desc.errored = Throwable::printStackTrace;

            return music;
        });
    }


    private boolean checkedOutdated, outdated, notified;
    private boolean outdated(){
        if(!checkedOutdated){
            checkedOutdated = true;
            var hash = Objects.hash(Version.assetUrl, Version.assetRef);
            outdated = Core.settings.getInt("assetkey") != hash;
            if(outdated) Core.settings.put("assetkey", hash);
        }
        return outdated;
    }

    public void loadAudio(DownloadableAudio audio, String path, int length){
        var fi = get(path);
        var clazz = audio.getClass().getSimpleName(); // Used for error messages
        if(audio instanceof Sound sound && fi.parent().name().equals("ui")) sound.setBus(Vars.control.sound.uiBus);

        if(fi.exists()){ // Local copy. Assumed to be up-to-date
            audio.load(fi);
            return;
        }

        var cached = Core.settings.getDataDirectory().child("cache").child(audio instanceof Sound ? path : fi.nameWithoutExtension() + "__" + length + "." + fi.extension()); // See Music#load

        if(!outdated() && cached.exists()){ // Cached up-to-date copy
            audio.loadDirectly(cached);
            return;
        }

        ConsT<Http.HttpResponse, Exception> writeDownloadedAudio = res -> { // This creates garbage, but it's convenient and shouldn't matter as this method is called few times
            if(!cached.exists() || cached.length() != res.getContentLength()){ // Only download if the existing file isn't the same size
                var write = cached.write();
                Streams.copy(res.getResultAsStream(), cached.write());
                write.close();
            }
            audio.loadDirectly(cached);
        };

        if(!Core.settings.getBool("download" + clazz.toLowerCase())) return; // Automatic downloading of music and sound can be disabled
//        FINISHME: Making a get request to the line below would be beneficial as we could compare hashes but that would require a backup for exceeding rate limits (would also be done by directory as it would save many requests)
//        "https://api.github.com/repos/" + Version.assetUrl + "/contents/core/assets/" + path + "?ref=" + Version.assetRef;
        var req = Http.get("https://raw.githubusercontent.com/" + Version.assetUrl + '/' + Version.assetRef  + "/core/assets/" + path);
        req.error(e -> {
            if(e instanceof UnknownHostException){ // Likely Wi-Fi skill issue
                Core.app.post(() -> {
                    if(!notified) Vars.ui.showErrorMessage("@client.audiofail"); // Display at most one dialog
                    if(cached.exists()) audio.loadDirectly(cached); // Use outdated cached audio if it exists, it's better than silence
                    notified = true;
                });
                return;
            }
            Log.debug("@ downloading failed for @ retrying", clazz, fi.name());
            req.error(e2 -> Log.err(clazz + " downloading for " + fi.name() + " failed", e2));
            req.timeout(5000); // The request probably timed out at 2000, it could be a fluke, but it could also just be bad Wi-Fi
            req.block(writeDownloadedAudio); // error() is run on the same thread so no need to submit this time as we're already on an http thread
        });
        req.submit(writeDownloadedAudio);
    }
}
