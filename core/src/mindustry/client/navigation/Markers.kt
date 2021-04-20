package mindustry.client.navigation

import arc.graphics.Color
import arc.scene.style.TextureRegionDrawable
import mindustry.Vars
import mindustry.gen.Icon

object Markers : ArrayList<Markers.Marker>() {
    data class Marker(var x: Int, var y: Int, var name: String, var color: Color, val shape: TextureRegionDrawable = Icon.star) {
        val unitX get() = x.toFloat() * Vars.tilesize
        val unitY get() = y.toFloat() * Vars.tilesize
    }
}
