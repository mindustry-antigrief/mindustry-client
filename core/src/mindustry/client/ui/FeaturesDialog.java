package mindustry.client.ui;

import mindustry.ui.dialogs.BaseDialog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FeaturesDialog extends BaseDialog {

    public FeaturesDialog() {
        super("Features and Documentation");
        try {
            cont.add(StupidMarkupParser.format());
        } catch (IOException error) {
            error.printStackTrace();
        }
        addCloseButton();
    }
}
