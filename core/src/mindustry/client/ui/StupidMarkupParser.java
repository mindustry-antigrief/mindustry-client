package mindustry.client.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.ui.Label;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;

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
