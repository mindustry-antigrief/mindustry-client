package mindustry.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.util.*;
import mindustry.entities.type.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Antialiaser implements Disposable{
    private FrameBuffer buffer = new FrameBuffer(2, 2);

    {
        buffer.getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
//        buffer.getTexture().
//        Core.gl.glEnable(Core.gl.GL_TEXTURE_2D);
//        Core.gl.glEnable(gl.GL_SAMPLE_COVERAGE);
//        System.out.println(Core.);
//        System.out.println(new Core.gl.;);
//        System.out.println(Core.gl.SAMPLE);
//        Core.gl.glHint(Core.gl.GL_SAMPLES, 4);
//        Core.gl.glSampleCoverage(0.5F, true);
    }

    public void drawPixelate(){
        float pre = renderer.getScale();
        float scale = renderer.getScale();
        scale = (int)scale;
        renderer.setScale(scale);
        camera.width = (int)camera.width;
        camera.height = (int)camera.height;

        graphics.clear(0f, 0f, 0f, 1f);

        float px = Core.camera.position.x, py = Core.camera.position.y;
        Core.camera.position.set(px + ((camera.width) % 2 == 0 ? 0 : 0.5f), py + ((camera.height) % 2 == 0 ? 0 : 0.5f));

        int w = (int)(Core.camera.width * renderer.landScale());
        int h = (int)(Core.camera.height * renderer.landScale());

        if(!graphics.isHidden() && (buffer.getWidth() != w || buffer.getHeight() != h)){
            buffer.resize(w, h);
        }

        Draw.flush();
        buffer.begin();
        renderer.draw();

        Draw.flush();
        buffer.end();

        Draw.blend(Blending.disabled);
        Draw.rect(Draw.wrap(buffer.getTexture()), Core.camera.position.x, Core.camera.position.y, Core.camera.width, -Core.camera.height);
        Draw.blend();

        playerGroup.draw(p -> !p.isDead(), Player::drawName);

        Core.camera.position.set(px, py);
        renderer.setScale(pre);
    }

    public boolean enabled(){
//        return renderer.getScale() < 0.5;
        return Core.settings.getBool("pixelate");
    }

    @Override
    public void dispose() {

    }
}
