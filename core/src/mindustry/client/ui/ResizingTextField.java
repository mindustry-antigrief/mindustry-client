package mindustry.client.ui;

import arc.scene.ui.*;
import arc.struct.*;

public class ResizingTextField extends TextField{
    public ResizingTextField(String text){
        super(text);
    }

    public ResizingTextField(String text, TextFieldStyle style){
        super(text, style);
    }


    public FloatArray getGlyphPositions(){
        return glyphPositions;
    }
}
