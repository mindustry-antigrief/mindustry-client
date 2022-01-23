package mindustry.client.graphics

import arc.*
import arc.graphics.*
import arc.graphics.g2d.*
import arc.graphics.gl.*
import arc.math.geom.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.navigation.*
import mindustry.client.utils.*
import mindustry.graphics.*
import kotlin.Pair
import kotlin.collections.set
import kotlin.math.*

object RangeDrawer {
    private val vector = Vec2()
    private var mapping: MutableMap<Color, MutableMap<Float, Pair<FrameBuffer, TextureRegion>?>> = mutableMapOf()
    private var prev = 0f

    fun draw(ranges: List<Pair<TurretPathfindingEntity, Color>>) {
        val scl = 4  // for whatever reason, drawing with one pixel per pixel doesn't look good (I think my pixel calculations are off)

        // the same as the normal version but sides can be calculated outside the function so that they don't change with scale
        fun dashCircle(x: Float, y: Float, radius: Float, sides: Int) {
            vector.set(0f, 0f)

            for (i in 0 until sides) {
                if (i % 2 == 0) continue
                vector.set(radius, 0f).setAngle(360f / sides * i + 90)
                val x1 = vector.x
                val y1 = vector.y
                vector.set(radius, 0f).setAngle(360f / sides * (i + 1) + 90)
                Lines.line(x1 + x, y1 + y, vector.x + x, vector.y + y)
            }
        }

        // find the full set of team range combinations needed to draw the frame
        val unique1 = mutableMapOf<Color, MutableSet<Float>>()

        for (item in ranges) {
            unique1.getOrPut(item.second) { mutableSetOf() }.add(item.first.radius - tilesize)
        }
        val unique = unique1.flatMap { item -> item.value.map { item.key to it } }

        // calculate how many pixels there are per world unit
        Tmp.v3.set(Core.camera.position.x + 1f, 0f)
        Tmp.v3.sub(Core.camera.width / 2, 0f)
        Core.camera.project(Tmp.v3)
        val converted1 = Tmp.v3.x * scl

        // if the zoom is the same as last frame, don't remake the cache (values not included will be drawn the normal way)
        if (converted1 != prev) {
            prev = converted1
            // dispose the buffers from the previous frame
            for (item in mapping.values) {
                for (subItem in item) {
                    subItem.value?.first?.dispose()
                }
            }

            val cache = Array(unique.size) {
                // the radius in pixels
                val convertedRadius = converted1 * unique[it].second

                val diameter = convertedRadius * 2
                val diameterCeil = diameter.ceil()

                // if the texture would be bigger than the screen, it's probably not worth it, draw it the normal way
                if (diameter > min(Core.camera.height, Core.camera.width)) return@Array null

                // make a buffer to draw to
                val b = FrameBuffer(diameterCeil, diameterCeil)

                // ???
                Draw.blend()
                Draw.reset()
                Tmp.m1.set(Draw.proj())
                Tmp.m2.set(Draw.trans())

                Draw.trans().idt()

                b.begin(Color.clear)
                Draw.proj().setOrtho(0f, 0f, b.width.toFloat(), b.height.toFloat())

                // calculate the number of sides as if it's drawing normally (taken from Lines.dashCircle())
                val scaleFactor = 0.6f
                var sides = 10 + (unique[it].second * scaleFactor).toInt()
                if (sides % 2 == 1) sides++

                // same as Drawf.dashCircle() but with the thicknesses scaled and the sides fixed
                Lines.stroke(converted1 * 3, Pal.gray)
                dashCircle(convertedRadius, convertedRadius, convertedRadius, sides)
                Lines.stroke(converted1, unique[it].first)
                dashCircle(convertedRadius, convertedRadius, convertedRadius, sides)

                Draw.reset()

                // ???
                Draw.flush()
                Draw.trans().idt()

                b.end()

                Draw.proj(Tmp.m1)
                Draw.trans(Tmp.m2)

                return@Array b
            }

            // convert it to the performant format used to cache and draw it, converting the item to a TextureRegion for rendering
            mapping = HashMap()
            unique.zip(cache) { a, b -> mapping.getOrPut(a.first, ::HashMap)[a.second] = if (b != null) Pair(b, TextureRegion(b.texture)) else null }
        }

        for (c in ranges) {
            // grab the needed TextureRegion, may be null
            val t = mapping[c.second]?.get(c.first.radius - tilesize)
            val color = c.second
            if (t == null) {
                // if it's either not found or it decided not to do it for performance, draw the regular way
                Drawf.dashCircle(c.first.x, c.first.y, c.first.radius - tilesize, color)
            } else {
                // blit it
                Draw.rect(t.second, c.first.x, c.first.y, (c.first.radius - tilesize) * 2, (c.first.radius - tilesize) * 2)
            }
        }
        Draw.reset()
    }
}
