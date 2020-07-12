package mindustry.ui;

import arc.scene.*;
import arc.scene.ui.*;

public class TextTooltip{
    public static void addTooltip(Element element, String text){
        Tooltip tooltip = new Tooltip((table) -> {
            table.add(new Label(text));
        });
        element.addListener(tooltip);
    }
}
