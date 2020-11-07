package mindustry.client.ui;

import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.ui.Label;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;

public class StupidMarkupParser {
    private static final LabelStyle headingStyle = new LabelStyle(Fonts.def, Pal.accent);
    private static final Seq<LabelStyle> listStyles = new Seq<>(new LabelStyle[]{ new LabelStyle(Fonts.def, Color.gray), new LabelStyle(Fonts.def, Color.lightGray) });

    public static Element format(String input) {
        // remove block comments
        input = input.replaceAll("\\/\\*\\*(.|\\s)*?\\*\\/", "!!");
        // remove normal comments
        input = input.replaceAll("//.*^$", "");

        String[] lines = input.split("\n");
        Seq<Label> elements = new Seq<>();
        for (String line : lines) {
            if (line.startsWith("# ")) {
                line = line.replace("# ", "");
                Label label = new Label(line);
                label.setStyle(headingStyle);
                elements.add(label);
            }

            if (line.startsWith(" * ")) {
                LabelStyle s = listStyles.first();
                if (elements.any() && listStyles.contains(elements.peek().getStyle())) {
                    int index = (listStyles.indexOf(elements.peek().getStyle()) + 1) % listStyles.size;
                    s = listStyles.get(index);
                }
                line = line.replace(" * ", "  ");
                Label label = new Label(line);
                label.setStyle(s);
                elements.add(label);
            }
        }
        Table table = new Table();
        elements.forEach(e -> {
            table.add(e);
            table.row();
        });
        return table;
    }
}
