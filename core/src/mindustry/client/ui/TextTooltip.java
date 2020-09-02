package mindustry.client.ui;

import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.*;
import mindustry.gen.*;

public class TextTooltip{
    public static void addTooltip(Element element, String text){
        Tooltip tooltip = new Tooltip((table) -> {
            Label label = new Label(text);
            table.table(Tex.button, c -> c.add(label));
        });
        element.addListener(tooltip);
    }
}
