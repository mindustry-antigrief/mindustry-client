package mindustry.client.ui;

import arc.Core;
import mindustry.ui.dialogs.BaseDialog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FeaturesDialog extends BaseDialog {

    public FeaturesDialog() {
        super("Features and Documentation");
        cont.add(StupidMarkupParser.format(Core.files.internal("features").readString("UTF-8")));
        addCloseButton();
    }
}
