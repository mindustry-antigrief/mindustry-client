package mindustry.client.ui;

import arc.Core;
import arc.scene.Element;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.ui.dialogs.BaseDialog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ChangelogDialog extends BaseDialog {

    public ChangelogDialog() {
        super("Changelog");
        cont.pane(StupidMarkupParser.format(Core.files.internal("changelog").readString("UTF-8"))).growX().get().setScrollingDisabled(true, false);
        addCloseButton();
    }
}
