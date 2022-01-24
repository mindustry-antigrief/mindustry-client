package mindustry.client.communication

import arc.graphics.*
import arc.util.*
import mindustry.client.utils.*
import java.nio.*
import kotlin.random.*

class ImageTransmission : Transmission {
    override var id = Random.nextLong()
    override val secureOnly = false
    val message: Short
    val image: Pixmap

    constructor(message: Short, image: Pixmap) {
        this.message = message
        this.image = image
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(b: ByteArray, id: Long, senderID: Int) {
        val metadata = ByteBuffer.wrap(b)
        message = metadata.short
        image = inflateImage(b, 2, b.size - 2)!!
    }

    override fun serialize(): ByteArray {
        return ByteBuffer.allocate(2).putShort(message).array() + compressImage(image).apply { Log.debug("image size: ${size / 1024} KiB") }
    }
}
