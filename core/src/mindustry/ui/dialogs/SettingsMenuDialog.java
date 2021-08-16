package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.core.GameState.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;

import java.io.*;
import java.util.zip.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class SettingsMenuDialog extends Dialog{
    /** Mods break if these are changed to BetterSettingsTable so instead we cast them into different vars and just use those. */
    public SettingsTable graphics, sound, game, main, client, moderation;

    private Table prefs;
    private Table menu;
    private BaseDialog dataDialog;
    private boolean wasPaused;

    public SettingsMenuDialog(){
        super(bundle.get("settings", "Settings"));
        addCloseButton();

        cont.add(main = new SettingsTable());

        hidden(() -> {
            Sounds.back.play();
            if(state.isGame()){
                if(!wasPaused || Vars.net.active())
                    state.set(State.playing);
            }
            ConstructBlock.updateWarnBlocks(); // FINISHME: Horrible
        });

        shown(() -> {
            back();
            if(state.isGame()){
                wasPaused = state.is(State.paused);
                state.set(State.paused);
            }

            rebuildMenu();
        });

        Events.on(ResizeEvent.class, event -> {
            if(isShown() && Core.scene.getDialog() == this){
                graphics.rebuild();
                sound.rebuild();
                game.rebuild();
                client.rebuild();
                moderation.rebuild();
                updateScrollFocus();
            }
        });

        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image()).growX().height(3f).pad(4f).get().setColor(Pal.accent);

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table(Tex.button);

        // Casting avoids mod problems, no clue how or why
        game = new SettingsTable();
        graphics = new SettingsTable();
        sound = new SettingsTable();
        client = new SettingsTable();
        moderation = new SettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        dataDialog = new BaseDialog("@settings.data");
        dataDialog.addCloseButton();

        dataDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButtonStyle style = Styles.cleart;

            t.button("@settings.cleardata", Icon.trash, style, () -> ui.showConfirm("@confirm", "@settings.clearall.confirm", () -> {
                ObjectMap<String, Object> map = new ObjectMap<>();
                for(String value : Core.settings.keys()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Core.settings.get(value, null));
                    }
                }
                Core.settings.clear();
                Core.settings.putAll(map);

                for(Fi file : dataDirectory.list()){
                    file.deleteDirectory();
                }

                Core.app.exit();
            })).marginLeft(4);

            t.row();

            t.button("@settings.clearsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearsaves.confirm", () -> control.saves.deleteAll());
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearresearch", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearresearch.confirm", () -> {
                    universe.clearLoadoutInfo();
                    for(TechNode node : TechTree.all){
                        node.reset();
                    }
                    content.each(c -> {
                        if(c instanceof UnlockableContent u){
                            u.clearUnlock();
                        }
                    });
                    settings.remove("unlocks");
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearcampaignsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearcampaignsaves.confirm", () -> {
                    for(var planet : content.planets()){
                        for(var sec : planet.sectors){
                            sec.clearInfo();
                            if(sec.save != null){
                                sec.save.delete();
                                sec.save = null;
                            }
                        }
                    }

                    for(var slot : control.saves.getSaveSlots().copy()){
                        if(slot.isSector()){
                            slot.delete();
                        }
                    }
                });
            }).marginLeft(4);

            t.row();

            t.button("@data.export", Icon.upload, style, () -> {
                if(ios){
                    Fi file = Core.files.local("mindustry-data-export.zip");
                    try{
                        exportData(file);
                    }catch(Exception e){
                        ui.showException(e);
                    }
                    platform.shareFile(file);
                }else{
                    platform.showFileChooser(false, "zip", file -> {
                        try{
                            exportData(file);
                            ui.showInfo("@data.exported");
                        }catch(Exception e){
                            e.printStackTrace();
                            ui.showException(e);
                        }
                    });
                }
            }).marginLeft(4);

            t.row();

            t.button("@data.import", Icon.download, style, () -> ui.showConfirm("@confirm", "@data.import.confirm", () -> platform.showFileChooser(true, "zip", file -> {
                try{
                    importData(file);
                    Core.app.exit();
                }catch(IllegalArgumentException e){
                    ui.showErrorMessage("@data.invalid");
                }catch(Exception e){
                    e.printStackTrace();
                    if(e.getMessage() == null || !e.getMessage().contains("too short")){
                        ui.showException(e);
                    }else{
                        ui.showErrorMessage("@data.invalid");
                    }
                }
            }))).marginLeft(4);

            if(!mobile){
                t.row();
                t.button("@data.openfolder", Icon.folder, style, () -> Core.app.openFolder(Core.settings.getDataDirectory().absolutePath())).marginLeft(4);
            }

            t.row();

            t.button("@crash.export", Icon.upload, style, () -> {
                if(settings.getDataDirectory().child("crashes").list().length == 0 && !settings.getDataDirectory().child("last_log.txt").exists()){
                    ui.showInfo("@crash.none");
                }else{
                    if(ios){
                        Fi logs = tmpDirectory.child("logs.txt");
                        logs.writeString(getLogs());
                        platform.shareFile(logs);
                    }else{
                        platform.showFileChooser(false, "txt", file -> {
                            file.writeString(getLogs());
                            app.post(() -> ui.showInfo("@crash.exported"));
                        });
                    }
                }
            }).marginLeft(4);
        });

        row();
        ScrollPane pane = pane(prefs).grow().top().get();
        pane.setFadeScrollBars(true); // TODO: needed in v7?
        pane.setCancelTouchFocus(false);
        row();
        add(buttons).fillX();

        addSettings();
    }

    String getLogs(){
        Fi log = settings.getDataDirectory().child("last_log.txt");

        StringBuilder out = new StringBuilder();
        for(Fi fi : settings.getDataDirectory().child("crashes").list()){
            out.append(fi.name()).append("\n\n").append(fi.readString()).append("\n");
        }

        if(log.exists()){
            out.append("\nlast log:\n").append(log.readString());
        }

        return out.toString();
    }

    void rebuildMenu(){
        menu.clearChildren();

        TextButtonStyle style = Styles.cleart;

        menu.defaults().size(300f, 60f);
        menu.button("@settings.game", style, () -> visible(0));
        menu.row();
        menu.button("@settings.graphics", style, () -> visible(1));
        menu.row();
        menu.button("@settings.sound", style, () -> visible(2));
        menu.row();
        menu.button("@settings.client", style, () -> visible(3));
        menu.row();
        menu.button("@settings.language", style, ui.language::show);
        if(!mobile || Core.settings.getBool("keyboard")){
            menu.row();
            menu.button("@settings.controls", style, ui.controls::show);
        }

        menu.row();
        menu.button("@settings.data", style, () -> dataDialog.show());
    }

    void addSettings(){
        sound.sliderPref("musicvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("sfxvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", 100, 0, 100, 1, i -> i + "%");


        // Client Settings, organized exactly the same as Bundle.properties: text first, sliders second, checked boxes third, unchecked boxes last
        client.category("antigrief");
        client.sliderPref("reactorwarningdistance", 40, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("reactorsounddistance", 25, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorwarningdistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorsounddistance", 3, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagwarningdistance", 10, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagsounddistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.checkPref("breakwarnings", true); // Warnings for removal of certain sandbox stuff (mostly sources)
        client.checkPref("powersplitwarnings", true); // TODO: Add a minimum building requirement and a setting for it
        client.checkPref("viruswarnings", true);
        client.checkPref("commandwarnings", true);
        client.checkPref("removecorenukes", false);

        client.category("chat");
        client.checkPref("clearchatonleave", true);
        client.checkPref("logmsgstoconsole", true);
        client.checkPref("clientjoinleave", true);
        client.checkPref("highlightclientmsg", false);
        client.checkPref("displayasuser", false);
        client.checkPref("broadcastcoreattack", false); // TODO: Multiple people using this setting at once will cause chat spam
        client.checkPref("showuserid", false);

        client.category("controls");
        client.checkPref("blockreplace", true);
        client.checkPref("instantturn", true);
        client.checkPref("autoboost", false);
        client.checkPref("assumeunstrict", false);

        client.category("graphics");
        client.sliderPref("minzoom", 0, 0, 100, s -> Strings.fixed(Mathf.pow(10, 0.0217f * s) / 100f, 2) + "x");
        client.sliderPref("weatheropacity", 50, 0, 100, s -> s + "%");
        client.sliderPref("effectscl", 100, 0, 100, 5, s -> s + "%");
        client.sliderPref("firescl", 50, 0, 150, 5, s -> s + "%[lightgray] (" + Core.bundle.get("client.afterstack") + ": " + s * settings.getInt("effectscl") / 100 + "%)[]");
        client.checkPref("tilehud", true);
        client.checkPref("lighting", true);
        client.checkPref("disablemonofont", true); // Requires Restart
        client.checkPref("unitranges", false);
        client.checkPref("drawhitboxes", false);
        client.checkPref("mobileui", false, i -> mobile = !mobile);
        client.checkPref("showreactors", false);
        client.checkPref("showdomes", false);

        client.category("misc");
        client.updatePref();
        client.sliderPref("minepathcap", 0, 0, 5000, 100, s -> s == 0 ? "Unlimited" : String.valueOf(s));
        client.sliderPref("defaultbuildpathradius", 0, 0, 250, 5, s -> s == 0 ? "Unlimited" : String.valueOf(s));
        client.checkPref("autoupdate", true);
        client.checkPref("discordrpc", true, i -> platform.toggleDiscord(i));
        client.checkPref("nyduspadpatch", true);
        client.checkPref("hidebannedblocks", false);
        client.checkPref("allowjoinany", false);
        client.checkPref("debug", false, i -> Log.level = i ? Log.LogLevel.debug : Log.LogLevel.info); // Sets the log level to debug
        if (steam) client.checkPref("unlockallachievements", false);
        client.checkPref("automega", false, i -> ui.unitPicker.type = i ? UnitTypes.mega : ui.unitPicker.type);
        // End Client Settings


        game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Core.bundle.format("setting.seconds", i));
        if(mobile){
            game.checkPref("autotarget", true);
            if(!ios){
                game.checkPref("keyboard", false, val -> {
                    control.setInput(val ? new DesktopInput() : new MobileInput());
                    input.setUseKeyboard(val);
                });
                if(Core.settings.getBool("keyboard")){
                    control.setInput(new DesktopInput());
                    input.setUseKeyboard(true);
                }
            }
        }
        //the issue with touchscreen support on desktop is that:
        //1) I can't test it
        //2) the SDL backend doesn't support multitouch
        /*else{
            game.checkPref("touchscreen", false, val -> control.setInput(!val ? new DesktopInput() : new MobileInput()));
            if(Core.settings.getBool("touchscreen")){
                control.setInput(new MobileInput());
            }
        }*/

        if(!mobile){
            game.checkPref("crashreport", true);
        }
        game.checkPref("savecreate", true); // Autosave
        game.checkPref("conveyorpathfinding", true);
        game.checkPref("hints", true);
        game.checkPref("logichints", true);

        if(!mobile){
            game.checkPref("backgroundpause", true);
            game.checkPref("buildautopause", false);
        }

        game.checkPref("doubletapmine", false);

        if(!ios){
            game.checkPref("modcrashdisable", true);
        }

        if(steam){
            game.sliderPref("playerlimit", 16, 2, 250, i -> {
                platform.updateLobby();
                return i + "";
            });

            if(!Version.modifier.contains("beta")){
                game.checkPref("publichost", false, i -> platform.updateLobby());
            }
        }

        int[] lastUiScale = {settings.getInt("uiscale", 100)};

        graphics.sliderPref("uiscale", 100, 25, 300, 25, s -> {
            //if the user changed their UI scale, but then put it back, don't consider it 'changed'
            Core.settings.put("uiscalechanged", s != lastUiScale[0]);
            return s + "%";
        });

        graphics.sliderPref("screenshake", 4, 0, 8, i -> (i / 4f) + "x");
        graphics.sliderPref("fpscap", 240, 10, 245, 5, s -> (s > 240 ? Core.bundle.get("setting.fpscap.none") : Core.bundle.format("setting.fpscap.text", s)));
        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");
        graphics.sliderPref("lasersopacity", 100, 0, 100, 5, s -> {
            if(ui.settings != null){
                Core.settings.put("preferredlaseropacity", s);
            }
            return s + "%";
        });
        graphics.sliderPref("bridgeopacity", 100, 0, 100, 5, s -> s + "%");

        if(!mobile){
            graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> {
                if(b && settings.getBool("borderlesswindow")){
                    Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                    settings.put("borderlesswindow", false);
                    graphics.rebuild();
                }

                if(b){
                    Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode());
                }else{
                    Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                }
            });

            graphics.checkPref("borderlesswindow", false, b -> {
                if(b && settings.getBool("fullscreen")){
                    Core.graphics.setWindowedMode(Core.graphics.getWidth(), Core.graphics.getHeight());
                    settings.put("fullscreen", false);
                    graphics.rebuild();
                }
                Core.graphics.setBorderless(b);
            });

            Core.graphics.setVSync(Core.settings.getBool("vsync"));

            if(Core.settings.getBool("fullscreen")){
                Core.app.post(() -> Core.graphics.setFullscreenMode(Core.graphics.getDisplayMode()));
            }

            if(Core.settings.getBool("borderlesswindow")){
                Core.app.post(() -> Core.graphics.setBorderless(true));
            }
        }else if(!ios){
            graphics.checkPref("landscape", false, b -> {
                if(b){
                    platform.beginForceLandscape();
                }else{
                    platform.endForceLandscape();
                }
            });

            if(Core.settings.getBool("landscape")){
                platform.beginForceLandscape();
            }
        }

        graphics.checkPref("effects", true);
        graphics.checkPref("atmosphere", !mobile);
        graphics.checkPref("destroyedblocks", true);
        graphics.checkPref("blockstatus", false);
        graphics.checkPref("playerchat", true);
        graphics.checkPref("coreitems", !mobile);
        graphics.checkPref("minimap", !mobile);
        graphics.checkPref("smoothcamera", true);
        graphics.checkPref("position", false);
        graphics.checkPref("fps", false);
        graphics.checkPref("playerindicators", true);
        graphics.checkPref("indicators", true);
        // graphics.checkPref("showweather", true); FINISHME: Move client weather alpha to this
        graphics.checkPref("animatedwater", true);

        if(Shaders.shield != null){
            graphics.checkPref("animatedshields", !mobile);
        }

        graphics.checkPref("bloom", true, val -> renderer.toggleBloom(val));

        graphics.checkPref("pixelate", false, val -> {
            if(val){
                Events.fire(Trigger.enablePixelation);
            }
        });

        //iOS (and possibly Android) devices do not support linear filtering well, so disable it
        if(!ios){
            graphics.checkPref("linear", !mobile, b -> {
                for(Texture tex : Core.atlas.getTextures()){
                    TextureFilter filter = b ? TextureFilter.linear : TextureFilter.nearest;
                    tex.setFilter(filter, filter);
                }
            });
        }else{
            settings.put("linear", false);
        }

        if(Core.settings.getBool("linear")){
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = TextureFilter.linear;
                tex.setFilter(filter, filter);
            }
        }

        graphics.checkPref("skipcoreanimation", false);

        if(!mobile){
            Core.settings.put("swapdiagonal", false);
        }


        // Start Moderation Settings
        moderation.checkPref("modenabled", true, b -> Client.INSTANCE.setLeaves(b ? new Moderation() : null));
        moderation.sliderPref("leavecount", 100, 5, 1000, 10, String::valueOf);
        // End Moderation Settings
    }

    public void exportData(Fi file) throws IOException{
        Seq<Fi> files = new Seq<>();
        files.add(Core.settings.getSettingsFile());
        files.addAll(customMapDirectory.list());
        files.addAll(saveDirectory.list());
        files.addAll(screenshotDirectory.list());
        files.addAll(modDirectory.list());
        files.addAll(schematicDirectory.list());
        String base = Core.settings.getDataDirectory().path();

        //add directories
        for(Fi other : files.copy()){
            Fi parent = other.parent();
            while(!files.contains(parent) && !parent.equals(settings.getDataDirectory())){
                files.add(parent);
            }
        }

        try(OutputStream fos = file.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
            for(Fi add : files){
                String path = add.path().substring(base.length());
                if(add.isDirectory()) path += "/";
                //fix trailing / in path
                path = path.startsWith("/") ? path.substring(1) : path;
                zos.putNextEntry(new ZipEntry(path));
                if(!add.isDirectory()){
                    Streams.copy(add.read(), zos);
                }
                zos.closeEntry();
            }
        }
    }

    public void importData(Fi file){
        Fi dest = Core.files.local("zipdata.zip");
        file.copyTo(dest);
        Fi zipped = new ZipFi(dest);

        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid save data.");
        }

        //delete old saves so they don't interfere
        saveDirectory.deleteDirectory();

        //purge existing tmp data, keep everything else
        tmpDirectory.deleteDirectory();

        zipped.walk(f -> f.copyTo(base.child(f.path())));
        dest.delete();

        //clear old data
        settings.clear();
        //load data so it's saved on exit
        settings.load();
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    public void visible(int index){
        prefs.clearChildren();
        prefs.add(new Table[]{game, graphics, sound, client, moderation}[index]);
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, () -> {
            if(prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        }).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                if(prefs.getChildren().first() != menu){
                    back();
                }else{
                    hide();
                }
            }
        });
    }

    public interface StringProcessor{
        String get(int i);
    }

    public static class SettingsTable extends Table{
        protected Seq<Setting> list = new Seq<>();

        public SettingsTable(){
            left();
        }

        public Seq<Setting> getSettings(){
            return list;
        }

        public void pref(Setting setting){
            list.add(setting);
            rebuild();
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s){
            return sliderPref(name, def, min, max, 1, s);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s){
            SliderSetting res;
            list.add(res = new SliderSetting(name, def, min, max, step, s));
            settings.defaults(name, def);
            rebuild();
            return res;
        }

        public void checkPref(String name, boolean def){
            list.add(new CheckSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void checkPref(String name, boolean def, Boolc changed){
            list.add(new CheckSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        void rebuild(){
            clearChildren();

            for(Setting setting : list){
                setting.add(this);
            }

            button(bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for(Setting setting : list){
                    if(setting.name == null || setting.title == null) continue;
                    settings.put(setting.name, settings.getDefault(setting.name));
                }
                rebuild();
            }).margin(14).width(240f).pad(6);
        }

        public abstract static class Setting{
            public String name;
            public String title;
            public @Nullable String description;

            Setting(String name){
                this.name = name;
                String winkey = "setting." + name + ".name.windows";
                title = OS.isWindows && bundle.has(winkey) ? bundle.get(winkey) : bundle.get("setting." + name + ".name");
                description = bundle.getOrNull("setting." + name + ".description");
            }

            public abstract void add(SettingsTable table);

            public void addDesc(Element elem){
                if(description == null) return;

                elem.addListener(new Tooltip(t -> t.background(Styles.black8).margin(4f).add(description).color(Color.lightGray)){
                    {
                        allowMobile = true;
                    }
                    @Override
                    protected void setContainerPosition(Element element, float x, float y){
                        this.targetActor = element;
                        Vec2 pos = element.localToStageCoordinates(Tmp.v1.set(0, 0));
                        container.pack();
                        container.setPosition(pos.x, pos.y, Align.topLeft);
                        container.setOrigin(0, element.getHeight());
                    }
                });
            }
        }

        public static class CheckSetting extends Setting{
            boolean def;
            Boolc changed;

            CheckSetting(String name, boolean def, Boolc changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                CheckBox box = new CheckBox(title);

                box.update(() -> box.setChecked(settings.getBool(name)));

                box.changed(() -> {
                    settings.put(name, box.isChecked());
                    if(changed != null){
                        changed.get(box.isChecked());
                    }
                });

                box.left();
                addDesc(table.add(box).left().padTop(3f).get());
                table.row();
            }
        }

        public static class SliderSetting extends Setting{
            int def, min, max, step;
            StringProcessor sp;

            SliderSetting(String name, int def, int min, int max, int step, StringProcessor s){
                super(name);
                this.def = def;
                this.min = min;
                this.max = max;
                this.step = step;
                this.sp = s;
            }

            @Override
            public void add(SettingsTable table){
                Slider slider = new Slider(min, max, step, false);

                slider.setValue(settings.getInt(name));

                Label value = new Label("", Styles.outlineLabel);
                Table content = new Table();
                content.add(title, Styles.outlineLabel).left().growX().wrap();
                content.add(value).padLeft(10f).right();
                content.margin(3f, 33f, 3f, 33f);
                content.touchable = Touchable.disabled;

                slider.changed(() -> {
                    settings.put(name, (int)slider.getValue());
                    value.setText(sp.get((int)slider.getValue()));
                });

                slider.change();

                addDesc(table.stack(slider, content).width(Math.min(Core.graphics.getWidth() / 1.2f, 460f)).left().padTop(4f).get());
                table.row();
            }
        }

        /** Add a section/subcategory. */
        public void category(String name){
            pref(new Category(name));
        }

        /* TODO: Actually add this at some point, this sounds like a massive pain in the ass tho.
        public void textPref(String name, String def){
            settings.defaults(name, def);
            pref(new TextPref(name));
        } */

        // Elements are actually added below
        public static class Category extends Setting{
            Category(String name){
                super(name);
                this.name = name;
                this.title = bundle.get("setting." + name + ".category");
            }

            @Override
            public void add(SettingsTable table){
                table.add("").row(); // Add a cell first as .row doesn't work if there are no cells in the current row.
                table.add("[accent]" + title);
                table.row();
            }
        }


        /** Since the update pref takes half a page and implementing all this in a non static manner is a pain, I'm leaving it here for now. */
        private void updatePref(){
            settings.defaults("updateurl", "mindustry-antigrief/mindustry-client");
            if (!Version.updateUrl.isEmpty()) settings.put("updateurl", Version.updateUrl); // overwrites updateurl on every boot, shouldn't be a real issue
            pref(new Setting("updateurl") {
                boolean urlChanged;

                @Override
                public void add(SettingsTable table) { // Update URL with update button FINISHME: Move this to TextPref when i decide im willing to spend 6 hours doing so
                    name = "updateurl";
                    title = bundle.get("setting." + name + ".name");

                    table.table(t -> {
                        t.button(Icon.refresh, Styles.settingtogglei, 32, () -> {
                            ui.loadfrag.show();
                            becontrol.checkUpdate(result -> {
                                ui.loadfrag.hide();
                                urlChanged = false;
                                if(!result){
                                    ui.showInfo("@be.noupdates");
                                } else {
                                    becontrol.showUpdateDialog();
                                }
                            });
                        }).update(u -> u.setChecked(becontrol.isUpdateAvailable() || urlChanged)).padRight(4);
                        Label label = new Label(title);
                        t.add(label).minWidth(label.getPrefWidth() / Scl.scl(1.0F) + 25.0F);
                        t.field(settings.getString(name), text -> {
                            becontrol.setUpdateAvailable(false); // Set this to false as we don't know if this is even a valid URL.
                            urlChanged = true;
                            settings.put(name, text);
                        }).width(450).get().setMessageText("mindustry-antigrief/mindustry-client");
                    }).left().expandX().padTop(3).height(32).padBottom(3);
                    table.row();
                }
            });
        }
    }
}
