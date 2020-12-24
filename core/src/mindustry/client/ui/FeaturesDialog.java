package mindustry.client.ui;

import arc.Core;
import arc.scene.ui.ScrollPane;
import mindustry.ui.dialogs.BaseDialog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FeaturesDialog extends BaseDialog {

    public FeaturesDialog() {
        super("Features and Documentation");
        cont.pane(StupidMarkupParser.format(Core.files.internal("features").readString("UTF-8"))).growX().get().setScrollingDisabled(true, false);
        addCloseButton();
    }
}
