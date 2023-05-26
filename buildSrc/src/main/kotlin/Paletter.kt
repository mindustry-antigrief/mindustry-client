import arc.graphics.*
import arc.struct.*
import kotlin.math.*

class Paletter { // If this is an object the private fields get cached between gradle executions and its annoying

    val blacklist = setOf(
        ".DS_Store", "pack.json", "error.png", "logo.png", "alpha-bg.png", "alphaaaa.png", "clear.png",
        "particle.png", "hcircle.png", "clear-effect.png", "flarogus.png"
    )
    val regexlist = arrayOf(
        ".+-shadow[0-9]?\\.png", ".+-glow\\.png", ".+-vents\\.png", ".+-blur\\.png", ".+-heat(?:_full|-top)?\\.png", "fire[0-9]+\\.png",
        "circle-(?:mid|end|small)\\.png", ".+-cell\\.png", "edge(?:-stencil)?\\.png",
    ).map { it.toRegex() }

    private val mapping = map(
        0x62AE7FFF, 0x50A9ADFF,
        0x84F491FF, 0x6DF5D7FF,
        0xB0BAC0FF, 0x757C80FF,
        0x989AA4FF, 0x5C5E64FF,
        0x6E7080FF, 0x373840FF,
        0xD3816BFF, 0xD48555FF,
        0xEA8778FF, 0xEB7360FF,
        0xFEB380FF, 0xFFA366FF,
        0x767a84FF, 0x4f5f85FF,
        0x8e9097FF, 0x737c96FF,
        0x3a3a50FF, 0x29294fFF,
        0x84f491FF, 0x58f56aFF,
        0xFF62AE7F, 0xFF3EAD68,
    )

    private val notFound = IntSet() // Colors not in mapping that we've already tried and are not similar and are therefore missing from the map
    private var missingCount = 0
    private val similar = map() // Basically just additional keys for [mapping] entries for colors that are similar enough to be considered one of the mapped colors

    /** Horribly cursed way around having to spam [Long.toInt] calls everywhere */
    private fun map(vararg values: Long) = IntIntMap.of(*values.map { it.toInt() }.toIntArray())

    /** Not thread safe. */
    fun Pixmap.mapColors(fileName: String) {
        val colorCache = Color()
        val pc = Color() // pixelColor
        for (i in 0 until height * width * 4 step 4) {
            val pixel = pixels.getInt(i)
            if (pixel == 0) continue

            val mapped = mapping[pixel, -1]
            if (mapped != -1) {
                pixels.putInt(i, mapped)
                continue
            }

            val similarMapped = similar[pixel, -1] // This is *very* hacky
            if (similarMapped != -1) {
                pixels.putInt(i, similarMapped)
                continue
            }

            if (notFound.add(pixel) && !mapping.containsValue(pixel)) { // This is very slow
                pc.set(pixel)
                if (pc.a == 0F) continue
                pc.premultiplyAlpha()
                val values = mapping.values()
                var diff = Float.MAX_VALUE
                var min: Int = -1
                while (values.hasNext()) { // minBy just doesn't work here and I can't be bothered to find out why.
                    val e = values.next()
                    val c = colorCache.set(e).premultiplyAlpha()
                    val v = sqrt((c.r - pc.r) * (c.r - pc.r) +
                            (c.g - pc.g) * (c.g - pc.g) +
                            (c.b - pc.b) * (c.b - pc.b))
                    if (diff > v) {
                        min = e
                        diff = v
                    }
                }

                if (diff < 0.01) {
                    similar.put(pixel, min)
                    pixels.putInt(i, min)
                } else {
                    missingCount++
                    println("Missing 0x${Integer.toHexString(pixel).uppercase()} | Closest: 0x${Integer.toHexString(min).uppercase()} | File: $fileName | Diff: $diff")
                }
            }
        }
    }
}