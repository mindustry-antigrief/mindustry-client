package mindustry.client.ui;

import arc.Core;
import arc.scene.ui.ScrollPane;
import mindustry.ui.dialogs.BaseDialog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ChangelogDialog extends BaseDialog {

    public ChangelogDialog() {
        super("Changelog");
        cont.add(new ScrollPane(StupidMarkupParser.format(Core.files.internal("changelog").readString("UTF-8")))).growX().center();
        addCloseButton();
    }
}
