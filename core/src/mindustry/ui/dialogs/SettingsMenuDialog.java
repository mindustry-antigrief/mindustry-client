package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.client.*;
import mindustry.client.antigrief.*;
import mindustry.client.utils.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.logic.*;
import mindustry.service.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.storage.*;

import java.io.*;
import java.util.zip.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class SettingsMenuDialog extends BaseDialog{
    public SettingsTable graphics, sound, game, main, client, moderation;

    private Table prefs;
    private Table menu;
    private BaseDialog dataDialog;
    private Seq<SettingsCategory> categories = new Seq<>();

    public SettingsMenuDialog(){
        super(bundle.get("settings", "Settings"));
        addCloseButton();

        cont.add(main = new SettingsTable());
        shouldPause = true;

        // This is going to break isnt it?
//        hidden(() -> updateSettings()); // FINISHME: Horrible
        hidden(ConstructBlock::updateWarnBlocks); // FINISHME: Horrible

        shown(() -> {
            back();
            rebuildMenu();
        });

        onResize(() -> {
            graphics.rebuild();
            sound.rebuild();
            game.rebuild();
            client.rebuild();
            moderation.rebuild();
            updateScrollFocus();
        });

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table(Tex.button);

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
            TextButtonStyle style = Styles.flatt;

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
                ui.showConfirm("@confirm", "@settings.clearsaves.confirm", control.saves::deleteAll);
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
                            try{
                                file.writeBytes(getLogs().getBytes(Strings.utf8));
                                app.post(() -> ui.showInfo("@crash.exported"));
                            }catch(Throwable e){
                                ui.showException(e);
                            }
                        });
                    }
                }
            }).marginLeft(4);
        });

        row();
        ScrollPane pane = pane(prefs).grow().top().get();
        pane.setFadeScrollBars(true);
        pane.setCancelTouchFocus(false);
        row();
        add(buttons).fillX();

        addSettings();
    }

    // FIX CURSED MENU SCREEN
//    public void updateSettings(){
//        ConstructBlock.updateWarnBlocks();
//        if(Vars.ui.menufrag.renderer.cursednessLevel != CursednessLevel.fromInteger(Core.settings.getInt("cursednesslevel", 1))){
//            Vars.ui.menufrag.renderer.updateCursedness();
//        }
//    }

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

    /** Adds a custom settings category, with the icon being the specified region. */
    public void addCategory(String name, @Nullable String region, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, region == null ? null : new TextureRegionDrawable(atlas.find(region)), builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, @Nullable Drawable icon, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, icon, builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, Cons<SettingsTable> builder){
        addCategory(name, (Drawable)null, builder);
    }
    public Seq<SettingsCategory> getCategories(){
        return categories;
    }

    void rebuildMenu(){
        menu.clearChildren();

        TextButtonStyle style = Styles.flatt;

        float marg = 8f, isize = iconMed;

        menu.defaults().size(300f, 60f);
        menu.button("@settings.game", Icon.settings, style, isize, () -> visible(0)).marginLeft(marg).row();
        menu.button("@settings.graphics", Icon.image, style, isize, () -> visible(1)).marginLeft(marg).row();
        menu.button("@settings.sound", Icon.filters, style, isize, () -> visible(2)).marginLeft(marg).row();
        menu.button("@settings.client", Icon.wrench, style, isize, () -> visible(3)).marginLeft(marg).row();
        menu.button("@settings.language", Icon.chat, style, isize, ui.language::show).marginLeft(marg).row();
        if(!mobile || Core.settings.getBool("keyboard")){
            menu.button("@settings.controls", Icon.move, style, isize, ui.controls::show).marginLeft(marg).row();
        }

        menu.button("@settings.data", Icon.save, style, isize, () -> dataDialog.show()).marginLeft(marg).row();

        int i = 5;
        for(var cat : categories){
            int index = i;
            if(cat.icon == null){
                menu.button(cat.name, style, () -> visible(index)).marginLeft(marg).row();
            }else{
                menu.button(cat.name, cat.icon, style, isize, () -> visible(index)).with(b -> ((Image)b.getChildren().get(1)).setScaling(Scaling.fit)).marginLeft(marg).row();
            }
            i++;
        }
    }

    void addSettings(){
        sound.sliderPref("musicvol", 100, 0, 100, 1, i -> { Musics.load(); return i + "%"; });
        sound.sliderPref("sfxvol", 100, 0, 100, 1, i -> { Sounds.load(); return i + "%"; });
        sound.sliderPref("ambientvol", 100, 0, 100, 1, i -> { Sounds.load(); return i + "%"; });

        // Client Settings, organized exactly the same as Bundle.properties: text first, sliders second, checked boxes third, unchecked boxes last
        client.category("antigrief");
        client.sliderPref("reactorwarningdistance", 40, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("reactorsounddistance", 25, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorwarningdistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("incineratorsounddistance", 3, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagwarningdistance", 10, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.sliderPref("slagsounddistance", 5, 0, 101, s -> s == 101 ? "Always" : s == 0 ? "Never" : Integer.toString(s));
        client.checkPref("breakwarnings", true); // Warnings for removal of certain sandbox stuff (mostly sources)
        client.checkPref("powersplitwarnings", true); // FINISHME: Add a minimum building requirement and a setting for it
        client.checkPref("viruswarnings", true, b -> LExecutor.virusWarnings = b);
        client.checkPref("removecorenukes", false);
        
        // Seer: Client side multiplayer griefing/cheating detections
        if (settings.getBool("client-experimentals")) { // FINISHME: Either remove this or make it properly functional
            client.checkPref("seer-enabled", false); // by default false because still new
            client.checkPref("seer-autokick", false); // by default false to avoid false positives
            client.sliderPref("seer-warnthreshold", 10, 0, 50, String::valueOf);
            client.sliderPref("seer-autokickthreshold", 20, 0, 50, String::valueOf);
            client.sliderPref("seer-scoredecayinterval", 1, 0, 10, i -> String.valueOf(i * 30) + "s");
            client.sliderPref("seer-scoredecay", 5, 0, 20, String::valueOf);
            client.sliderPref("seer-reactorscore", 8, 0, 10, String::valueOf);
            client.sliderPref("seer-reactordistance", 5, 0, 20, String::valueOf);
            client.sliderPref("seer-configscore", 3, 0, 50, i -> String.valueOf(i / 5f)); // 0.60
            client.sliderPref("seer-configdistance", 20, 0, 100, String::valueOf);
            client.sliderPref("seer-proclinkthreshold", 20, 0, 80, String::valueOf);
            client.sliderPref("seer-proclinkscore", 10, 0, 50, String::valueOf);
        }
        

        client.category("chat");
        client.checkPref("clearchatonleave", true);
        client.checkPref("logmsgstoconsole", true);
        client.checkPref("clientjoinleave", true);
        client.checkPref("highlightcryptomsg", true);
        client.checkPref("highlightclientmsg", false);
        client.checkPref("displayasuser", true);
        client.checkPref("broadcastcoreattack", false); // FINISHME: Multiple people using this setting at once will cause chat spam
        client.checkPref("showuserid", false);
        client.checkPref("hideserversbydefault", false); // Inverts behavior of server hiding

        client.category("controls");
        client.checkPref("blockreplace", true);
        client.checkPref("instantturn", true);
        client.checkPref("autoboost", false);
        client.checkPref("assumeunstrict", false);
        client.checkPref("returnonmove", false);
        client.checkPref("decreasedrift", false);
        client.checkPref("zerodrift", false);
        client.checkPref("fastrespawn", false);

        client.category("graphics");
        client.sliderPref("minzoom", 0, 0, 100, s -> Strings.fixed(Mathf.pow(10, 0.0217f * s) / 100f, 2) + "x");
        client.sliderPref("weatheropacity", 50, 0, 100, s -> s + "%");
        client.sliderPref("junctionview", 0, -1, 1, 1, s -> { Junction.setBaseOffset(s); return s == -1 ? "On left side" : s == 1 ? "On right side" : "Do not show"; });
        client.sliderPref("spawntime", 5, -1, 60, s -> { ClientVars.spawnTime = 60 * s; if (Vars.pathfinder.thread == null) Vars.pathfinder.start(); return s == -1 ? "Solid Line" : s == 0 ? "Disabled" : String.valueOf(s); });
        client.sliderPref("traveltime", 10, 0, 60, s -> { ClientVars.travelTime = 60f / s; return s == 0 ? "Disabled" : String.valueOf(s); });
        client.sliderPref("formationopacity", 30, 10, 100, 5, s -> { UnitType.formationAlpha = s / 100f; return s + "%"; });
        client.sliderPref("hitboxopacity", 0, 0, 100, 5, s -> { UnitType.hitboxAlpha = s / 100f; return s == 0 ? "Disabled" : s + "%"; });
        client.checkPref("tilehud", true);
        client.checkPref("lighting", true);
        client.checkPref("disablemonofont", true); // Requires Restart
        client.checkPref("placementfragmentsearch", true);
        client.checkPref("junctionflowratedirection", false, s -> Junction.flowRateByDirection = s);
        client.checkPref("drawwrecks", true);
        client.checkPref("drawallitems", true, i -> UnitType.drawAllItems = i);
        client.checkPref("drawdisplayborder", false);
        client.checkPref("drawpath", true);
        client.checkPref("tracelogicunits", false);
        client.checkPref("enemyunitranges", false);
        client.checkPref("allyunitranges", false);
        client.checkPref("graphdisplay", false);
        client.checkPref("mobileui", false, i -> mobile = !mobile);
        client.checkPref("showreactors", false);
        client.checkPref("showdomes", false);
        client.checkPref("allowinvturrets", true);
        client.checkPref("showtoasts", true);
        client.checkPref("unloaderview", false, i -> Unloader.drawUnloaderItems = i);
        client.checkPref("customnullunloader", false, i -> Unloader.customNullLoader = i);
        client.sliderPref("cursednesslevel", 1, 0, 4, s -> CursednessLevel.fromInteger(s).name());
        client.checkPref("logiclinkorder", false);
    
        client.category("misc");
        client.updatePref();
        client.sliderPref("minepathcap", 0, -100, 5000, 100, s -> s == 0 ? "Unlimited" : s == -100 ? "Never" : String.valueOf(s));
        client.sliderPref("defaultbuildpathradius", 0, 0, 250, 5, s -> s == 0 ? "Unlimited" : String.valueOf(s));
        client.sliderPref("modautoupdate", 1, 0, 2, s -> s == 0 ? "Disabled" : s == 1 ? "In Background" : "Restart Game");
        client.sliderPref("processorstatementscale", 80, 10, 100, 1, s -> String.format("%.2fx", s/100f)); // This is the most scuffed setting you have ever seen
        client.sliderPref("automapvote", 0, 0, 4, s -> s == 0 ? "Never" : s == 4 ? "Random vote" : "Always " + new String[]{"downvote", "novote", "upvote"}[--s]);
        client.textPref("defaultbuildpathargs", "broken assist unfinished networkassist upgrade");
        client.textPref("defaultminepathargs", "copper lead sand coal titanium beryllium graphite tungsten");
        client.textPref("gamejointext", "");
        client.textPref("gamewintext", "gg");
        client.textPref("gamelosetext", "gg");
        client.checkPref("autoupdate", true, i -> becontrol.checkUpdates = i);
        client.checkPref("discordrpc", true, i -> platform.toggleDiscord(i));
        client.checkPref("typingindicator", true, i -> control.input.showTypingIndicator = i);
        client.checkPref("pathnav", true);
        client.checkPref("nyduspadpatch", true);
        client.checkPref("hidebannedblocks", false);
        client.checkPref("allowjoinany", false);
        client.checkPref("debug", false, i -> Log.level = i ? Log.LogLevel.debug : Log.LogLevel.info); // Sets the log level to debug
        if (steam) client.checkPref("unlockallachievements", false, i -> { for (var a : Achievement.all) a.complete(); Core.settings.remove("unlockallachievements"); });
        client.checkPref("automega", false, i -> ui.unitPicker.type = i ? UnitTypes.mega : ui.unitPicker.type);
        client.checkPref("processorconfigs", false);
        client.checkPref("autorestart", true);
        client.checkPref("attemwarfare", true);
        client.checkPref("attemwarfarewhisper", false);
        client.checkPref("onjoinfixcode", true);
        client.checkPref("removeatteminsteadoffixing", true);
        client.checkPref("downloadmusic", true);
        client.checkPref("downloadsound", true);
        client.checkPref("circleassist", false);
        client.checkPref("trackcoreitems", false, i -> CoreItemsDisplay.trackItems = i && !net.server());
        client.checkPref("ignoremodminversion", false);
        // End Client Settings


        game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Core.bundle.format("setting.seconds", i));
        game.checkPref("autotarget", false);
        if(mobile){
            if(!ios){
                game.checkPref("keyboard", false, val -> {
                    control.setInput(val ? new DesktopInput() : new MobileInput());
                    input.setUseKeyboard(val);
                });
                if(Core.settings.getBool("keyboard")){
                    control.setInput(new DesktopInput());
                    input.setUseKeyboard(true);
                }
            }else{
                Core.settings.put("keyboard", false);
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
        game.checkPref("commandmodehold", true);

        if(!ios){
            game.checkPref("modcrashdisable", true);
        }

        if(steam){
            game.sliderPref("playerlimit", 16, 2, 250, i -> {
                platform.updateLobby();
                return i + "";
            });

            game.checkPref("publichost", false, i -> platform.updateLobby());
        }

        if(!mobile){
            game.checkPref("console", false);
        }

        int[] lastUiScale = {settings.getInt("uiscale", 100)};

        graphics.sliderPref("uiscale", 100, 25, 300, 5, s -> {
            //if the user changed their UI scale, but then put it back, don't consider it 'changed'
            Core.settings.put("uiscalechanged", s != lastUiScale[0]);
            return s + "%";
        });

        graphics.sliderPref("screenshake", 4, 0, 8, i -> (i / 4f) + "x");

        graphics.sliderPref("bloomintensity", 6, 0, 16, i -> (int)(i/4f * 100f) + "%");
        graphics.sliderPref("bloomblur", 2, 1, 16, i -> i + "x");

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
                    Core.graphics.setFullscreen();
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
                Core.app.post(() -> Core.graphics.setFullscreen());
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

        Cons2<Boolean, Boolean> setFilters = (setNonText, setText) -> {
            ObjectSet<Texture> atlas = new ObjectSet<>(Core.atlas.getTextures());
            final boolean lText = Core.settings.getBool("lineartext");
            var fontFilter = Fonts.getTextFilter(lText);
            for(Font f: new Font[]{Fonts.def, Fonts.outline, Fonts.mono(), Fonts.monoOutline()}){
                f.getRegions().each(t -> {
                    if(setText) {
                        t.texture.setFilter(fontFilter);
                    }
                    atlas.remove(t.texture);
                });
            }
            if(setNonText){
                final var filter = Core.settings.getBool("linear") ? TextureFilter.linear : TextureFilter.nearest;
                atlas.each(t -> t.setFilter(filter));
            }
        };
        //iOS (and possibly Android) devices do not support linear filtering well, so disable it
        if(!ios){
            graphics.checkPref("linear", !mobile, b -> {
                setFilters.get(true, false);
            });
            graphics.checkPref("lineartext", Core.settings.getBool("linear"), b -> {
                setFilters.get(false, true);
            });
        }else{
            settings.put("linear", false);
            settings.put("lineartext", false);
        }

        setFilters.get(true, true);

        graphics.checkPref("skipcoreanimation", false);
        graphics.checkPref("hidedisplays", false);

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
        Fi zipped = new ZipFi(file);

        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid save data.");
        }

        //delete old saves so they don't interfere
        saveDirectory.deleteDirectory();

        //purge existing tmp data, keep everything else
        tmpDirectory.deleteDirectory();

        zipped.walk(f -> f.copyTo(base.child(f.path())));
        

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

        Seq<Table> tables = Seq.with(game, graphics, sound, client, moderation);
        categories.each(c -> tables.add(c.table));

        prefs.add(tables.get(index));
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

    public static class SettingsCategory{
        public String name;
        public @Nullable Drawable icon;
        public Cons<SettingsTable> builder;
        public SettingsTable table;

        public SettingsCategory(String name, Drawable icon, Cons<SettingsTable> builder){
            this.name = name;
            this.icon = icon;
            this.builder = builder;

            table = new SettingsTable();
            builder.get(table);
        }
    }

    public static class SettingsTable extends Table{
        protected Seq<Setting> list = new Seq<>();
        private static Seq<Setting> listSorted = new Seq<>();
        private String search = "";
        private Table searchBarTable;
        private TextField searchBar;

        public SettingsTable(){
            left();
            makeSearchBar();
        }

        public Seq<Setting> getSettings(){
            return list;
        }

        public void pref(Setting setting){
            list.add(setting);
            rebuild();
        }

        private void makeSearchBar(){
            searchBarTable = table(s -> {
                s.left();
                s.image(Icon.zoom);
                searchBar = s.field(search, res -> {
                    search = res;
                    rebuild();
                }).growX().get();
            }).get();
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

        public void textPref(String name, String def){
            list.add(new TextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def, Cons<String> changed){
            list.add(new TextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def){
            list.add(new AreaTextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def, Cons<String> changed){
            list.add(new AreaTextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void rebuild(){
            boolean hasFocus = searchBar.hasKeyboard();
            clearChildren();
            // TODO inefficient. And rebuild() is also called every. single. setting.
            add(searchBarTable).fillX().padBottom(4);
            row();

            if(search.isEmpty()){
                for(Setting setting : list){
                    setting.add(this);
                }
            }else{
                listSorted.selectFrom(list, s -> !(s instanceof Category));
                listSorted.sort(Structs.comparingFloat(u ->
                    u.title == null ? Float.POSITIVE_INFINITY :
                            BiasedLevenshtein.biasedLevenshteinLengthIndependentInsensitive(search, Strings.stripColors(u.title))
                        // Maybe if distance == length, do not include? But troublesome
                ));
                for(Setting setting : listSorted){
                    setting.add(this);
                }
            }

            button(bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for(Setting setting : list){
                    if(setting.name == null || setting.title == null) continue;
                    settings.remove(setting.name);
                }
                rebuild();
            }).margin(14).width(240f).pad(6);

            if(hasFocus){
                searchBar.requestKeyboard();
            }
        }

        public abstract static class Setting{
            public String name;
            public String title;
            public @Nullable String description;

            public Setting(String name){
                this.name = name;
                String winkey = "setting." + name + ".name.windows";
                title = OS.isWindows && bundle.has(winkey) ? bundle.get(winkey) : bundle.get("setting." + name + ".name", name);
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

            public CheckSetting(String name, boolean def, Boolc changed){
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

            public SliderSetting(String name, int def, int min, int max, int step, StringProcessor s){
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
        public void category(String name){ // FINISHME: Rename this to header or something, it doesn't do the same thing as SettingsCategory
            pref(new Category(name));
        }

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
                        t.button(Icon.refresh, Styles.settingTogglei, 32, () -> {
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

        public static class TextSetting extends Setting{
            String def;
            Cons<String> changed;

            public TextSetting(String name, String def, Cons<String> changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                TextField field = new TextField(settings.getString(name));
                field.setMessageText(def);
                field.typed(c -> {
                    settings.put(name, field.getText());
                    if(changed != null){
                        changed.get(field.getText());
                    }
                });

                Table prefTable = table.table().left().padTop(3f).get();
                prefTable.label(() -> title);
                prefTable.add(field).width(450);
                addDesc(prefTable);
                table.row();
            }
        }

        public static class AreaTextSetting extends TextSetting{
            public AreaTextSetting(String name, String def, Cons<String> changed){
                super(name, def, changed);
            }

            @Override
            public void add(SettingsTable table){
                TextArea area = new TextArea(settings.getString(name));
                area.setPrefRows(5);

                area.typed(c -> {
                    settings.put(name, area.getText());
                    if(changed != null){
                        changed.get(area.getText());
                    }
                });

                addDesc(table.label(() -> title).left().padTop(3f).get());
                table.row().add(area).left();
                table.row();
            }
        }
    }
}
