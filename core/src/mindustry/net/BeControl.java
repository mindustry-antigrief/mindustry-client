package mindustry.net;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.util.*;
import arc.util.async.*;
import arc.util.serialization.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.net.Administration.*;
import mindustry.net.Packets.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.io.*;
import java.net.*;

import static mindustry.Vars.*;

/** Handles control of bleeding edge builds. */
public class BeControl{
    private static final int updateInterval = 90; // Poll every 90s (40/hr), this leaves us with 20 requests per hour to spare.

    private final AsyncExecutor executor = new AsyncExecutor(1);
    /** Whether or not to automatically display an update prompt on client load and every subsequent minute. */
    private boolean checkUpdates = Core.settings.getBool("autoupdate");
    private boolean updateAvailable;
    private String updateUrl;
    private String updateBuild;

    /** @return whether this is a bleeding edge build. */
    public boolean active(){
        return Version.type.equals("bleeding-edge");
    }

    public BeControl(){
        Events.on(EventType.ClientLoadEvent.class, event -> {
            Timer.schedule(() -> {
                if(checkUpdates && !mobile && !Version.clientVersion.equals("v1.0.0, Jan. 1, 1970")){ // Don't auto update on manually cloned copies of the repo
                    checkUpdate(result -> {
                        if (result) showUpdateDialog();
                    });
                }
                }, 1, updateInterval
            );

            if(OS.hasProp("becopy")){
                try{
                    Fi dest = Fi.get(OS.prop("becopy"));
                    Fi self = Fi.get(BeControl.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

                    for(Fi file : self.parent().findAll(f -> !f.equals(self))) file.delete();

                    self.copyTo(dest);
                }catch(Throwable e){
                    e.printStackTrace();
                }
            }
        });
    }

    /** asynchronously checks for updates. */
    public void checkUpdate(Boolc done){
        Http.get("https://api.github.com/repos/" + Core.settings.getString("updateurl") + "/releases/latest")
        .error(e -> ui.loadfrag.hide())
        .submit(res -> {
            Jval val = Jval.read(res.getResultAsString());
            String newBuild = val.getString("tag_name", "0");
            if(!Version.clientVersion.startsWith(newBuild)){
                Jval asset = val.get("assets").asArray().find(v -> v.getString("name", "").toLowerCase().contains("desktop"));
                if (asset == null) asset = val.get("assets").asArray().find(v -> v.getString("name", "").toLowerCase().contains("mindustry"));
                if (asset == null) {
                    Core.app.post(() -> done.get(false));
                    return;
                }
                updateUrl = asset.getString("browser_download_url", "");
                updateAvailable = true;
                updateBuild = newBuild;
                Core.app.post(() -> done.get(true));
            }else{
                Core.app.post(() -> done.get(false));
            }
        });
    }

    /** @return whether a new update is available */
    public boolean isUpdateAvailable(){
        return updateAvailable;
    }

    /** Sets updateAvailable to the specified value */
    public void setUpdateAvailable(boolean available){
        updateAvailable = available;
    }

    /** shows the dialog for updating the game on desktop, or a prompt for doing so on the server */
    public void showUpdateDialog(){
        if(!updateAvailable) return;

        if(!headless){
            checkUpdates = false;
            ui.showCustomConfirm(Core.bundle.format("be.update", "") + " Current: " + Version.clientVersion + " New: " + updateBuild, "@be.update.confirm", "@ok", "@be.ignore", () -> {
                try{
                    boolean[] cancel = {false};
                    float[] progress = {0};
                    int[] length = {0};
                    Fi file = bebuildDirectory.child("client-be-" + updateBuild + ".jar");
                    Fi fileDest = OS.hasProp("becopy") ?
                        Fi.get(OS.prop("becopy")) :
                        Fi.get(BeControl.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

                    BaseDialog dialog = new BaseDialog("@be.updating");
                    download(updateUrl, file, i -> length[0] = i, v -> progress[0] = v, () -> cancel[0], () -> {
                        try{
                            Log.info(file.absolutePath());
                            Runtime.getRuntime().exec(OS.isMac ?
                                new String[]{"java", "-XstartOnFirstThread", "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()} :
                                new String[]{"java", "-Dberestart", "-Dbecopy=" + fileDest.absolutePath(), "-jar", file.absolutePath()}
                            );
                            Core.app.exit();
                        }catch(IOException e){
                            ui.showException(e);
                        }
                    }, e -> {
                        dialog.hide();
                        ui.showException(e);
                    });

                    dialog.cont.add(new Bar(() -> length[0] == 0 ? Core.bundle.get("be.updating") : (int)(progress[0] * length[0]) / 1024/ 1024 + "/" + length[0]/1024/1024 + " MB", () -> Pal.accent, () -> progress[0])).width(400f).height(70f);
                    dialog.buttons.button("@cancel", Icon.cancel, () -> {
                        cancel[0] = true;
                        dialog.hide();
                    }).size(210f, 64f);
                    dialog.setFillParent(false);
                    dialog.show();
                }catch(Exception e){
                    ui.showException(e);
                }
            }, () -> checkUpdates = false);
        }else{
            Log.info("&lcCurrent: " + Version.clientVersion + " A new update is available: &lyBleeding Edge build @", updateBuild);
            if(Config.autoUpdate.bool()){
                Log.info("&lcAuto-downloading next version...");

                try{
                    //download new file from github
                    Fi source = Fi.get(BeControl.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                    Fi dest = source.sibling("server-be-" + updateBuild + ".jar");

                    download(updateUrl, dest,
                    len -> Core.app.post(() -> Log.info("&ly| Size: @ MB.", Strings.fixed((float)len / 1024 / 1024, 2))),
                    progress -> {},
                    () -> false,
                    () -> Core.app.post(() -> {
                        Log.info("&lcSaving...");
                        SaveIO.save(saveDirectory.child("autosavebe." + saveExtension));
                        Log.info("&lcAutosaved.");

                        netServer.kickAll(KickReason.serverRestarting);
                        Threads.sleep(32);

                        Log.info("&lcVersion downloaded, exiting. Note that if you are not using a auto-restart script, the server will not restart automatically.");
                        //replace old file with new
                        dest.copyTo(source);
                        dest.delete();
                        System.exit(2); //this will cause a restart if using the script
                    }),
                    Throwable::printStackTrace);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            checkUpdates = false;
        }
    }

    private void download(String furl, Fi dest, Intc length, Floatc progressor, Boolp canceled, Runnable done, Cons<Throwable> error){
        executor.submit(() -> {
            try{
                HttpURLConnection con = (HttpURLConnection)new URL(furl).openConnection();
                BufferedInputStream in = new BufferedInputStream(con.getInputStream());
                OutputStream out = dest.write(false, 4096);

                byte[] data = new byte[4096];
                long size = con.getContentLength();
                long counter = 0;
                length.get((int)size);
                int x;
                while((x = in.read(data, 0, data.length)) >= 0 && !canceled.get()){
                    counter += x;
                    progressor.get((float)counter / (float)size);
                    out.write(data, 0, x);
                }
                out.close();
                in.close();
                if(!canceled.get()) done.run();
            }catch(Throwable e){
                error.get(e);
            }
        });
    }
}
