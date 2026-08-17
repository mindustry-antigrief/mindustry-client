package mindustry.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.input.KeyBind.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import java.util.*;

import static arc.Core.*;

public class KeybindDialog extends Dialog{
    protected boolean rebindAxis = false;
    protected boolean rebindMin = true;
    protected KeyCode minKey = null;
    protected Dialog rebindDialog;
    protected Table bindsTable;
    private String searchText = "";

    public KeybindDialog(){
        super(bundle.get("keybind.title"));
        addCloseButton();
        setFillParent(true);
        title.setAlignment(Align.center);
        titleTable.row();
        titleTable.add(new Image()).growX().height(3f).pad(4f).get().setColor(Pal.accent);
        bindsTable = new Table();
        ScrollPane pane = new ScrollPane(bindsTable);
        pane.setFadeScrollBars(false);

        top();

        cont.table(table -> {
            table.left();
            table.image(Icon.zoom);
            var field = table.field(searchText, res -> {
                searchText = res;
                rebuildBinds();
            }).growX().get();

            shown(() -> {
                field.setText(searchText = "");
                rebuildBinds();
                app.post(field::requestKeyboard);
            });
        }).fillX().padBottom(4).top();

        cont.row();
        cont.add(pane).grow();

        rebuildBinds();
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back) hide();
        });
    }

    private void rebuildBinds(){

        Table table = bindsTable;
        bindsTable.clear();

        table.add().height(10);
        table.row();

        String lastCategory = null;
        var tstyle = Styles.grayt;

        float bw = 140f, bh = 40f;

        for(KeyBind keybind : KeyBind.all){
            if(!searchText.isEmpty() && !bundle.get("keybind." + keybind.name + ".name", keybind.name).toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT))){
                continue;
            }

            if(lastCategory != keybind.category && keybind.category != null){
                table.add(bundle.get("category." + keybind.category + ".name", Strings.capitalize(keybind.category))).color(Color.gray).colspan(4).pad(10).padBottom(4).row();
                table.image().color(Color.gray).fillX().height(3).pad(6).colspan(5).padTop(0).padBottom(10).row();
                lastCategory = keybind.category;
            }

            if(keybind.defaultValue instanceof Axis a){
                boolean isAxis = a.min != null;
                table.add(bundle.get("keybind." + keybind.name + ".name", Strings.capitalize(keybind.name)), Color.white).left().padRight(40).padLeft(8);

                table.labelWrap(() -> {
                    Axis axis = keybind.value;
                    return Seq.with(keybind.value.modifiers).toString("", m -> m.getModifierName() + " + ") + (axis.key != null ? axis.key.getName() : axis.min.getName() + " [red]/[] " + axis.max.getName());
                }).color(Pal.accent).left().minWidth(90).fillX().padRight(20);

                table.button("@settings.rebind", tstyle, () -> {
                    rebindAxis = isAxis;
                    rebindMin = isAxis;
                    openDialog(keybind);
                }).size(bw, bh);
                table.button("@client.settings.unbind", tstyle, () -> {
                    rebindAxis = isAxis;
                    rebindMin = isAxis;
                    rebind(keybind, KeyCode.unset);
                }).size(bw, bh).padLeft(4f).disabled(isAxis ? t -> keybind.value.min == KeyCode.unset : t -> keybind.value.key == KeyCode.unset);
            }else{
                table.add(bundle.get("keybind." + keybind.name + ".name", Strings.capitalize(keybind.name)), Color.white).left().padRight(40).padLeft(8);
                table.add(keybind.value.key.getName()).update(l -> {
                    l.setText(Seq.with(keybind.value.modifiers).toString("", m -> m.getModifierName() + " + ") + keybind.value.key.getName());
                    l.setColor(keybind.value.key == KeyCode.unset ? Color.darkGray : Pal.accent);
                }).color(Pal.accent).left().minWidth(90).padRight(20);

                table.button("@settings.rebind", tstyle, () -> {
                    rebindAxis = false;
                    rebindMin = false;
                    openDialog(keybind);
                }).size(bw, bh);

                table.button("@client.settings.unbind", tstyle, () -> {
                    rebindAxis = false;
                    rebindMin = false;
                    rebind(keybind, KeyCode.unset);
                }).size(bw, bh).padLeft(4f).disabled(t -> keybind.value.key == KeyCode.unset);
            }
            table.button("@settings.resetKey", tstyle, keybind::resetToDefault).disabled(t -> keybind.isDefault()).size(bw, bh).pad(2f).padLeft(4f);
            table.row();
        }

        table.button("@settings.reset", Icon.refresh, tstyle, KeyBind::resetAll).minWidth(200f).colspan(5).padTop(4).margin(10f).height(50f).fill();
    }

    void rebind(KeyBind bind, KeyCode newKey){
        rebind(bind, new Seq(0), newKey);
    }
    void rebind(KeyBind bind, Seq<KeyCode> pressedKeys){
        //The last one pressed is always the main key
        KeyCode main = pressedKeys.pop(() -> KeyCode.unset);
        rebind(bind, pressedKeys, main);
    }
    
    void rebind(KeyBind bind, Seq<KeyCode> modifiers, KeyCode newKey){
        if(rebindDialog != null) rebindDialog.hide();
        boolean isAxis = bind.defaultValue instanceof Axis axis && axis.min != null;

        if(isAxis){
            if(newKey.axis || !rebindMin){
                if(newKey == minKey) newKey = KeyCode.unset; //if the user enters the same key for both sides of the axis, set the up side to unset. this keeps the same behavior as previous but stores more sensible data.
                bind.value = newKey.axis ? new Axis(newKey) : new Axis(minKey, newKey);
            }
        }else{
            bind.value = new Axis(newKey);
        }
        if(modifiers.any()) bind.value.modifiers = modifiers.toArray(KeyCode.class);
        else bind.value.clearModifiers();

        bind.save();

        if(rebindAxis && isAxis && rebindMin && !newKey.axis){
            rebindMin = false;
            minKey = newKey;
            if (newKey == KeyCode.unset) rebind(bind, newKey); // unbind the axis
            else openDialog(bind);
        }else{
            rebindAxis = false;
        }
    }

    private void openDialog(KeyBind name){
        rebindDialog = new Dialog(rebindAxis ? bundle.get("keybind.press.axis") : bundle.get("keybind.press"));

        Seq<KeyCode> pressedKeys = new Seq<>(3);

        rebindDialog.titleTable.getCells().first().pad(4);
        rebindDialog.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(Core.app.isAndroid()) return false;
                rebindDialog.hide();
                pressedKeys.add(button);
                rebind(name, pressedKeys);
                return false;
            }

            @Override
            public boolean keyUp(InputEvent event, KeyCode keycode){
                //Doesn't matter which key was released as long as it's one of the pressed keys
                if(pressedKeys.contains(keycode)){
                    rebindDialog.hide();
                    rebind(name, pressedKeys);
                }
                return false;
            }

            @Override
            public boolean keyDown(InputEvent event, KeyCode keycode){
                pressedKeys.add(keycode);
                return false;
            }

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                if(!rebindAxis) return false;
                rebindDialog.hide();
                pressedKeys.add(KeyCode.scroll);
                rebind(name, pressedKeys);
                return false;
            }
        });

        rebindDialog.show();
        Time.runTask(1f, () -> getScene().setScrollFocus(rebindDialog));
    }
}
