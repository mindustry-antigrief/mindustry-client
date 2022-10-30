import arc.graphics.*
import arc.struct.*

object Paletter {

    val blacklist = setOf(
        ".DS_Store", "pack.json", "foo.png", "error.png", "logo.png", "alpha-bg.png", "alphaaaa.png", "clear.png",
        "particle.png", "circle-small.png", "hcircle.png", "clear-effect.png"
    )
    val regexlist = arrayOf(
        ".+-shadow[0-9]?\\.png", ".+-glow\\.png", ".+-vents\\.png", ".+-blur\\.png", ".+-heat(?:_full|-top)?\\.png", "fire[0-9]+\\.png",
        "circle-(?:mid|end)\\.png", ".+-cell\\.png", "edge(?:-stencil)?\\.png",
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
    )

    /** Horribly cursed way around having to spam [Long.toInt] calls everywhere */
    private fun map(vararg values: Long) = IntIntMap.of(*values.map { it.toInt() }.toIntArray())

    val missing = IntSet()

    fun Pixmap.mapColors(fileName: String) {
        for (i in 0 until height * width * 4 step 4) {
            val pixel = pixels.getInt(i)
            if (pixel == 0) continue
            val mapped = mapping[pixel, -1]

            if (mapped == -1) { // Not found FINISHME: Ignore transparent pixels
                if (missing.add(pixel) && !mapping.containsValue(pixel)) println("Missing $pixel | 0x${Integer.toHexString(pixel).uppercase()} in $fileName")
            } else {
                pixels.putInt(i, mapped)
            }
        }
    }
}