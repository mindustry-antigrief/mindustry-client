package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.client.CursednessLevel;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static mindustry.Vars.*;
import static mindustry.gen.Tex.*;

public class MenuFragment{
    private Table container, submenu;
    private Button currentMenu;
    public MenuRenderer renderer;
    private Seq<MenuButton> customButtons = new Seq<>();

    public void build(Group parent){
        renderer = new MenuRenderer();

        Group group = new WidgetGroup();
        group.setFillParent(true);
        group.visible(() -> !ui.editor.isShown());
        parent.addChild(group);

        parent = group;

        parent.fill((x, y, w, h) -> renderer.render());

        parent.fill(c -> {
            c.pane(Styles.noBarPane, cont -> {
                container = cont;
                cont.name = "menu container";

                if(!mobile){
                    c.left();
                    buildDesktop();
                    Events.on(ResizeEvent.class, event -> buildDesktop());
                }else{
                    buildMobile();
                    Events.on(ResizeEvent.class, event -> buildMobile());
                }
            }).with(pane -> {
                pane.setOverscroll(false, false);
            }).grow();
        });

        parent.fill(c -> c.bottom().right().button(Icon.discord, new ImageButtonStyle(){{ // FINISHME: This is cursed af
            up = discordBanner;
        }}, ui.discord::show).marginTop(9f).marginLeft(10f).tooltip("@discord").size(84, 45).name("discord"));

        parent.fill(c ->
            c.bottom().left().image(
                    Core.atlas.find("flarogus")
            ).tooltip("Foo's Client now includes Flarogus Client and Zxtej's client. Praise the almighty flarogus.")
            .size(60, 60).pad(5)
        );

        //info icon
        if(mobile){
            parent.fill(c -> c.bottom().left().button("", new TextButtonStyle(){{
                font = Fonts.def;
                fontColor = Color.white;
                up = infoBanner;
            }}, ui.about::show).size(84, 45).name("info"));
        }else{
            parent.fill(c -> {
                c.bottom().right().button("Switch to v6", Icon.download, () -> {
                    ui.loadfrag.show();
                    becontrol.checkUpdate(result -> {
                        ui.loadfrag.hide();
                        if(!result){
                            ui.showInfo("@be.noupdates");
                        } else {
                            becontrol.showUpdateDialog();
                        }
                    }, "mindustry-antigrief/mindustry-client-v6-builds");
                }).size(200, 60).padRight(10);

                c.button("", Icon.refresh, () -> {
                    Core.settings.put("updateurl", (Core.settings.getString("updateurl") + "-v7-builds").replaceFirst("((-v6|-v7)?-builds) {2}", ""));
                    ui.loadfrag.show();
                    becontrol.checkUpdate(result -> {
                        ui.loadfrag.hide();
                        if(!result){
                            ui.showInfo("@be.noupdates");
                        } else {
                            becontrol.showUpdateDialog();
                        }
                    });
                }).size(200, 60).padRight(10).update(t -> t.getLabel().setText(Core.settings.getString("updateurl").endsWith("-builds") ? "@client.switchstable" : "@client.switchunstable")).disabled(true); // FINISHME: Re-enable when v7 releases

                c.bottom().right().button("@be.check", Icon.refresh, () -> {
                    ui.loadfrag.show();
                    becontrol.checkUpdate(result -> {
                        ui.loadfrag.hide();
                        if(!result){
                            ui.showInfo("@be.noupdates");
                        } else {
                            becontrol.showUpdateDialog();
                        }
                    });
                }).size(200, 60).name("becheck").update(t -> t.getLabel().setColor(becontrol.isUpdateAvailable() ? Tmp.c1.set(Color.white).lerp(Pal.accent, Mathf.absin(5f, 1f)) : Color.white));
            });
            // TODO: Merge recheck
//        }else if(becontrol.active()){
//            parent.fill(c -> c.bottom().right().button("@be.check", Icon.refresh, () -> {
//                ui.loadfrag.show();
//                becontrol.checkUpdate(result -> {
//                    ui.loadfrag.hide();
//                    if(!result){
//                        ui.showInfo("@be.noupdates");
//                    }
//                });
//            }).size(200, 60).name("becheck").update(t -> {
//                t.getLabel().setColor(becontrol.isUpdateAvailable() ? Tmp.c1.set(Color.white).lerp(Pal.accent, Mathf.absin(5f, 1f)) : Color.white);
//            }));
        }

                // FIX CURSED MENU SCREEN
       String versionText = ((Version.build == -1) ? "[#fc8140aa]" : "[#ffffffba]") + Version.combined() + Strings.format("\n[gray]Don't press H[]\nCursedness by BalaM314 and SBytes. Cursedness Level: @", CursednessLevel.fromInteger(Core.settings.getInt("cursednesslevel")).name());
//        String versionText = (Version.build == -1 ? "[#fc8140aa]" : "[#ffffffba]") + Version.combined() + "\n[gray]Don't press H";
        // String versionText = ((Version.build == -1) ? "[#fc8140aa]" : "[#ffffffba]") + Version.combined() + "\n[gray]Don't press H[]\nClient Fork by Zxtej, BalaM314, SBytes. Cursedness Level: @";
        parent.fill((x, y, w, h) -> {
            TextureRegion logo = Core.atlas.find("logo");
            float width = Core.graphics.getWidth(), height = Core.graphics.getHeight() - Core.scene.marginTop;
            float logoscl = Scl.scl(1) * logo.scale;
            float logow = Math.min(logo.width * logoscl, Core.graphics.getWidth() - Scl.scl(20));
            float logoh = logow * (float)logo.height / logo.width;

            float fx = (int)(width / 2f);
            float fy = (int)(height - 6 - logoh) + logoh / 2 - (Core.graphics.isPortrait() ? Scl.scl(30f) : 0f);

            Draw.color();
            Draw.rect(logo, fx, fy, logow, logoh);

            Fonts.outline.setColor(Color.white);
            Fonts.outline.draw(versionText, fx, fy - logoh/2f - Scl.scl(2f), Align.center);
        }).touchable = Touchable.disabled;
    }

    private void buildMobile(){
        container.clear();
        container.name = "buttons";
        container.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());

        float size = 120f;
        container.defaults().size(size).pad(5).padTop(4f);

        MobileButton
            play = new MobileButton(Icon.play, "@campaign", () -> checkPlay(ui.planet::show)),
            custom = new MobileButton(Icon.rightOpenOut, "@customgame", () -> checkPlay(ui.custom::show)),
            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),
            join = new MobileButton(Icon.add, "@joingame", () -> checkPlay(ui.join::show)),
            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),
            tools = new MobileButton(Icon.settings, "@settings", ui.settings::show),
            mods = new MobileButton(Icon.book, "@mods", ui.mods::show),
            exit = new MobileButton(Icon.exit, "@quit", () -> Core.app.exit()),
            about = new MobileButton(Icon.info, "@about.button", ui.about::show);

        Seq<MobileButton> customs = customButtons.map(b -> new MobileButton(b.icon, b.text, b.runnable == null ? () -> {} : b.runnable));

        if(!Core.graphics.isPortrait()){
            container.marginTop(60f);
            container.add(play);
            container.add(join);
            container.add(custom);
            container.add(maps);
            // add odd custom buttons
            for(int i = 1; i < customs.size; i += 2){
                container.add(customs.get(i));
            }
            container.row();

            container.add(editor);
            container.add(tools);
            container.add(mods);
            // add even custom buttons (before the exit button)
            for(int i = 0; i < customs.size; i += 2){
                container.add(customs.get(i));
            }
            container.add(ios ? about : exit);
        }else{
            container.marginTop(0f);
            container.add(play);
            container.add(maps);
            container.row();
            container.add(custom);
            container.add(join);
            container.row();
            container.add(editor);
            container.add(tools);
            container.row();
            container.add(mods);
            // add custom buttons
            for(int i = 0; i < customs.size; i++){
                container.add(customs.get(i));
                if(i % 2 == 0) container.row();
            }
            container.add(ios ? about : exit);
        }
    }

    private void buildDesktop(){
        container.clear();
        container.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());

        float width = 230f;
        Drawable background = Styles.black6;

        container.left();
        container.add().width(Core.graphics.getWidth()/10f);
        container.table(background, t -> {
            t.defaults().width(width).height(70f);
            t.name = "buttons";

            buttons(t,
                new MenuButton("@play", Icon.play,
                    new MenuButton("@campaign", Icon.play, () -> checkPlay(ui.planet::show)),
                    new MenuButton("@joingame", Icon.add, () -> checkPlay(ui.join::show)),
                    new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),
                    new MenuButton("@loadgame", Icon.download, () -> checkPlay(ui.load::show))
                ),
                new MenuButton("@client", Icon.wrench,
                    new MenuButton("Discord", Icon.discord, () -> { // Link to client discord
                        if (!Core.app.openURI(clientDiscord)) {
                            ui.showErrorMessage("@linkfail");
                            Core.app.setClipboardText(clientDiscord);
                        }
                    }),
                    new MenuButton("Github", Icon.github, () -> { // Link to client github
                        if (!Core.app.openURI("https://github.com/blahblahbloopster/mindustry-client-v6")) {
                            ui.showErrorMessage("@linkfail");
                            Core.app.setClipboardText("https://github.com/blahblahbloopster/mindustry-client-v6");
                        }
                    }),
                    new MenuButton("@client.changelog", Icon.edit, ChangelogDialog.INSTANCE::show),
                    new MenuButton("@client.features", Icon.list, FeaturesDialog.INSTANCE::show),
                    new MenuButton("@client.keyshare", Icon.lock, () -> new TLSKeyDialog().show())
                ), // End of client section
                new MenuButton("@database.button", Icon.menu,
                    new MenuButton("@schematics", Icon.paste, ui.schematics::show),
                    new MenuButton("@database", Icon.book, ui.database::show),
                    new MenuButton("@about.button", Icon.info, ui.about::show)
                ),
                new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)), steam ? new MenuButton("@workshop", Icon.steam, platform::openWorkshop) : null,
                new MenuButton("@mods", Icon.book, ui.mods::show),
                new MenuButton("@settings", Icon.settings, ui.settings::show)
            );
            buttons(t, customButtons.toArray(MenuButton.class));
            buttons(t, new MenuButton("@quit", Icon.exit, Core.app::exit));
        }).width(width).growY();

        container.table(background, t -> {
            submenu = t;
            t.name = "submenu";
            t.color.a = 0f;
            t.top();
            t.defaults().width(width).height(70f);
            t.visible(() -> !t.getChildren().isEmpty());

        }).width(width).growY();
    }

    private void checkPlay(Runnable run){
        if(!mods.hasContentErrors()){
            run.run();
        }else{
            ui.showInfo("@mod.noerrorplay");
        }
    }

    private void fadeInMenu(){
        submenu.clearActions();
        submenu.actions(Actions.alpha(1f, 0.15f, Interp.fade));
    }

    private void fadeOutMenu(){
        //nothing to fade out
        if(submenu.getChildren().isEmpty()){
            return;
        }

        submenu.clearActions();
        submenu.actions(Actions.alpha(1f), Actions.alpha(0f, 0.2f, Interp.fade), Actions.run(() -> submenu.clearChildren()));
    }

    private void buttons(Table t, MenuButton... buttons){
        for(MenuButton b : buttons){
            if(b == null) continue;
            Button[] out = {null};
            out[0] = t.button(b.text, b.icon, Styles.flatToggleMenut, () -> {
                if(currentMenu == out[0]){
                    currentMenu = null;
                    fadeOutMenu();
                }else{
                    if(b.submenu != null){
                        currentMenu = out[0];
                        submenu.clearChildren();
                        fadeInMenu();
                        //correctly offset the button
                        submenu.add().height((Core.graphics.getHeight() - Core.scene.marginTop - Core.scene.marginBottom - out[0].getY(Align.topLeft)) / Scl.scl(1f));
                        submenu.row();
                        buttons(submenu, b.submenu);
                    }else{
                        currentMenu = null;
                        fadeOutMenu();
                        b.runnable.run();
                    }
                }
            }).marginLeft(11f).get();
            out[0].update(() -> out[0].setChecked(currentMenu == out[0]));
            t.row();
        }
    }

    /** Adds a custom button to the menu. */
    public void addButton(String text, Drawable icon, Runnable callback){
        addButton(new MenuButton(text, icon, callback));
    }

    /** Adds a custom button to the menu. */
    public void addButton(String text, Runnable callback){
        addButton(text, Styles.none, callback);
    }

    /**
     * Adds a custom button to the menu.
     * If {@link MenuButton#submenu} is null or the player is on mobile, {@link MenuButton#runnable} is invoked on click.
     * Otherwise, {@link MenuButton#submenu} is shown.
     */
    public void addButton(MenuButton button){
        customButtons.add(button);
    }

    /** Represents a menu button definition. */
    public static class MenuButton{
        public final Drawable icon;
        public final String text;
        /** Runnable ran when the button is clicked. Ignored on desktop if {@link #submenu} is not null. */
        public final Runnable runnable;
        /** Submenu shown when this button is clicked. Used instead of {@link #runnable} on desktop. */
        public final @Nullable MenuButton[] submenu;

        /** Constructs a simple menu button, which behaves the same way on desktop and mobile. */
        public MenuButton(String text, Drawable icon, Runnable runnable){
            this.icon = icon;
            this.text = text;
            this.runnable = runnable;
            this.submenu = null;
        }

        /** Constructs a button that runs the runnable when clicked on mobile or shows the submenu on desktop. */
        public MenuButton(String text, Drawable icon, Runnable runnable, MenuButton... submenu){
            this.icon = icon;
            this.text = text;
            this.runnable = runnable;
            this.submenu = submenu;
        }

        /** Comstructs a desktop-only button; used internally. */
        MenuButton(String text, Drawable icon, MenuButton... submenu){
            this.icon = icon;
            this.text = text;
            this.runnable = () -> {};
            this.submenu = submenu;
        }
    }
}
