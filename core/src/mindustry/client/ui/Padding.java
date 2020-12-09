package mindustry.client.ui;

import arc.scene.Element;

public class Padding extends Element {

    public Padding(float width, float height) {
        setWidth(width);
        setHeight(height);
    }

    @Override
    public float getPrefWidth() {
        return width;
    }

    @Override
    public float getPrefHeight() {
        return height;
    }

    @Override
    public void layout() {
        validate();
    }
}
