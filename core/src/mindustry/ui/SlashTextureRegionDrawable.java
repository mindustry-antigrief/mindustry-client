package mindustry.ui;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.layout.*;
import mindustry.graphics.*;

public class SlashTextureRegionDrawable extends TextureRegionDrawable {
	public Color slashColor = Pal.remove;
	public Color slashColorBack = Pal.removeBack;

	public SlashTextureRegionDrawable(TextureRegion region){
		super(region);
	}

	public SlashTextureRegionDrawable(TextureRegion region, Color tint){
		super(region);
		this.tint = tint;
	}

	@Override
	public void draw(float x, float y, float width, float height){
		super.draw(x, y, width, height);
		Lines.stroke(Scl.scl(2f), slashColorBack);
		Lines.line(x, y - 2f + height, x + width, y - 2f);
		Draw.color(slashColor);
		Lines.line(x, y + height, x + width, y);
		Draw.reset();
	}

	@Override
	public void draw(float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation){
		super.draw(x, y, originX, originY, width, height, scaleX, scaleY, rotation);
		Draw.color(slashColor);
	}
}