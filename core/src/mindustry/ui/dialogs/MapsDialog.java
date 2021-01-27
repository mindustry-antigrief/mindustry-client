package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.maps.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class MapsDialog extends BaseDialog{
    private BaseDialog dialog;
    private TextField searchField;
    private Table maps = new Table().marginRight(24);

    public MapsDialog(){
        super("@maps");

        buttons.remove();

        addCloseListener();

        shown(this::setup);
        onResize(() -> {
            if(dialog != null){
                dialog.hide();
            }
            setup();
        });
    }

    void setup(){
        buttons.clearChildren();

        if(Core.graphics.isPortrait()){
            buttons.button("@back", Icon.left, this::hide).size(210f*2f, 64f).colspan(2);
            buttons.row();
        }else{
            buttons.button("@back", Icon.left, this::hide).size(210f, 64f);
        }

        buttons.button("@editor.newmap", Icon.add, () -> {
            ui.showTextInput("@editor.newmap", "@editor.mapname", "", text -> {
                Runnable show = () -> ui.loadAnd(() -> {
                    hide();
                    ui.editor.show();
                    ui.editor.editor.tags.put("name", text);
                    Events.fire(new MapMakeEvent());
                });

                if(Vars.maps.byName(text) != null){
                    ui.showErrorMessage("@editor.exists");
                }else{
                    show.run();
                }
            });
        }).size(210f, 64f);

        buttons.button("@editor.importmap", Icon.upload, () -> {
            platform.showFileChooser(true, mapExtension, file -> {
                ui.loadAnd(() -> {
                    Vars.maps.tryCatchMapError(() -> {
                        if(MapIO.isImage(file)){
                            ui.showErrorMessage("@editor.errorimage");
                            return;
                        }

                        Map map = MapIO.createMap(file, true);

                        //when you attempt to import a save, it will have no name, so generate one
                        String name = map.tags.get("name", () -> {
                            String result = "unknown";
                            int number = 0;
                            while(Vars.maps.byName(result + number++) != null);
                            return result + number;
                        });

                        //this will never actually get called, but it remains just in case
                        if(name == null){
                            ui.showErrorMessage("@editor.errorname");
                            return;
                        }

                        Map conflict = Vars.maps.all().find(m -> m.name().equals(name));

                        if(conflict != null && !conflict.custom){
                            ui.showInfo(Core.bundle.format("editor.import.exists", name));
                        }else if(conflict != null){
                            ui.showConfirm("@confirm", Core.bundle.format("editor.overwrite.confirm", map.name()), () -> {
                                Vars.maps.tryCatchMapError(() -> {
                                    Vars.maps.removeMap(conflict);
                                    Vars.maps.importMap(map.file);
                                    setup();
                                });
                            });
                        }else{
                            Vars.maps.importMap(map.file);
                            setup();
                        }

                    });
                });
            });
        }).size(210f, 64f);


        cont.clear();

        cont.add(buttons).growX();
        cont.row();

        cont.table(s -> {
            s.left();
            s.image(Icon.zoom);
            searchField = s.field(null, res -> build()).growX().get();
        }).fillX().padBottom(4).row();
        Time.runTask(2f, () -> Core.scene.setKeyboardFocus(searchField));

        ScrollPane pane = new ScrollPane(maps);
        pane.setFadeScrollBars(false);
        build();
        cont.add(pane).uniformX();
    }

    void build() {
        int maxwidth = Math.max((int)(Core.graphics.getWidth() / Scl.scl(230)), 1);
        float mapsize = 200f;

        int i = 0;

        maps.clearChildren();
        for(Map map : Vars.maps.all()){
            if(searchField.getText().length() > 0 && !map.name().toLowerCase().contains(searchField.getText().toLowerCase())) continue;

            if(i % maxwidth == 0){
                maps.row();
            }

            TextButton button = maps.button("", Styles.cleart, () -> showMapInfo(map)).width(mapsize).pad(8).get();
            button.clearChildren();
            button.margin(9);
            button.add(map.name()).width(mapsize - 18f).center().get().setEllipsis(true);
            button.row();
            button.image().growX().pad(4).color(Pal.gray);
            button.row();
            button.stack(new Image(map.safeTexture()).setScaling(Scaling.fit), new BorderImage(map.safeTexture()).setScaling(Scaling.fit)).size(mapsize - 20f);
            button.row();
            button.add(map.custom ? "@custom" : map.workshop ? "@workshop" : map.mod != null ? "[lightgray]" + map.mod.meta.displayName() : "@builtin").color(Color.gray).padTop(3);

            i++;
        }

        if(Vars.maps.all().size == 0){
            maps.add("@maps.none");
        }
    }

    void showMapInfo(Map map){
        dialog = new BaseDialog("@editor.mapinfo");
        dialog.addCloseButton();

        float mapsize = Core.graphics.isPortrait() ? 160f : 300f;
        Table table = dialog.cont;

        table.stack(new Image(map.safeTexture()).setScaling(Scaling.fit), new BorderImage(map.safeTexture()).setScaling(Scaling.fit)).size(mapsize);

        table.table(Styles.black, desc -> {
            desc.top();
            Table t = new Table();
            t.margin(6);

            ScrollPane pane = new ScrollPane(t);
            desc.add(pane).grow();

            t.top();
            t.defaults().padTop(10).left();

            t.add("@editor.mapname").padRight(10).color(Color.gray).padTop(0);
            t.row();
            t.add(map.name()).growX().wrap().padTop(2);
            t.row();
            t.add("@editor.author").padRight(10).color(Color.gray);
            t.row();
            t.add(!map.custom && map.tags.get("author", "").isEmpty() ? "Anuke" : map.author()).growX().wrap().padTop(2);
            t.row();

            if(!map.tags.get("description", "").isEmpty()){
                t.add("@editor.description").padRight(10).color(Color.gray).top();
                t.row();
                t.add(map.description()).growX().wrap().padTop(2);
            }
        }).height(mapsize).width(mapsize);

        table.row();

        table.button("@editor.openin", Icon.export, () -> {
            try{
                Vars.ui.editor.beginEditMap(map.file);
                dialog.hide();
                hide();
            }catch(Exception e){
                e.printStackTrace();
                ui.showErrorMessage("@error.mapnotfound");
            }
        }).fillX().height(54f).marginLeft(10);

        table.button(map.workshop && steam ? "@view.workshop" : "@delete", map.workshop && steam ? Icon.link : Icon.trash, () -> {
            if(map.workshop && steam){
                platform.viewListing(map);
            }else{
                ui.showConfirm("@confirm", Core.bundle.format("map.delete", map.name()), () -> {
                    Vars.maps.removeMap(map);
                    dialog.hide();
                    setup();
                });
            }
        }).fillX().height(54f).marginLeft(10).disabled(!map.workshop && !map.custom);

        dialog.show();
    }
}
