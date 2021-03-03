package mindustry.client.ui;

import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.graphics.*;
import mindustry.ui.*;

public class StupidMarkupParser {
    private static final LabelStyle headingStyle = new LabelStyle(Fonts.def, Pal.accent);
    private static final Seq<Color> listColors = new Seq<>(new Color[]{Color.gray, Color.lightGray});

    public static Table format(String input) {
        // remove block comments
        input = input.replaceAll("/\\*\\*(.|\\s)*?\\*/", "!!");
        // remove normal comments
        input = input.replaceAll("//.*^$", "");
        // make \n actually function
        input = input.replace("\\n", "\n * ");

        String[] lines = input.split("\n");
        Seq<Label> elements = new Seq<>();
        for (String line : lines) {
            if (line.startsWith("# ")) {
                line = line.replace("# ", "\n");
                elements.add(new Label(line, headingStyle));
            }

            if (line.startsWith(" * ")) {
                Color s = listColors.first();
                if (elements.any() && listColors.contains(elements.peek().getStyle().fontColor)) {
                    int index = (listColors.indexOf(elements.peek().getStyle().fontColor) + 1) % listColors.size;
                    s = listColors.get(index);
                }
                line = line.replace(" * ", "");
                Label label = new Label(line, new LabelStyle(Fonts.def, s));
                elements.add(label);
            }
        }
        Table table = new Table().margin(10);
        elements.forEach(e -> table.add(e).left().growX().wrap().getTable().row());
        return table;
    }
}
