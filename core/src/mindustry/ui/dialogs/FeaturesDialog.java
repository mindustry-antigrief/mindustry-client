package mindustry.ui.dialogs;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;

public class FeaturesDialog extends FloatingDialog{

    public FeaturesDialog(){
        super("Features");
        shown(this::setup);
        onResize(this::setup);
    }

    private void setup(){
        cont.clear();
        buttons.clear();

        Table t = new Table();

        label(t, "Hello world!");
        label(t, "Welcome to the documentation");

        cont.add(new ScrollPane(t)).growX();

        addCloseButton();
    }

    private void label(Table t, String text){
        t.add(new Label(text));
        t.row();
    }
}
