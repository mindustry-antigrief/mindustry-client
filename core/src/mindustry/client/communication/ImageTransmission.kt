package mindustry.client.communication

import arc.graphics.Pixmap
import java.nio.ByteBuffer
import kotlin.random.Random

class ImageTransmission : Transmission {
    override var id = Random.nextLong()
    override val secureOnly = false
    val message: Short
    val image: Pixmap
    val width: Int
    val height: Int

    constructor(message: Short, image: Pixmap) {
        this.message = message
        this.image = image
        this.width = image.width
        this.height = image.height
    }

    private fun int(b: ByteArray, startIndex: Int) =
                b[startIndex].toInt() or
                (b[startIndex + 1].toInt() shl  8) or
                (b[startIndex + 2].toInt() shl 16) or
                (b[startIndex + 3].toInt() shl 24)

    private fun short(b: ByteArray, startIndex: Int) =
        (b[startIndex].toInt() or (b[startIndex + 1].toInt() shl 8)).toShort()

    constructor(b: ByteArray, id: Long, senderID: Int) {
        height = int(b, b.size - 4)
        width = int(b, b.size - 8)
        message = short(b, b.size - 10)

        val buf = ByteBuffer.allocateDirect(b.size - Short.SIZE_BYTES - Int.SIZE_BYTES * 2)
        buf.put(b, 0, b.size - Short.SIZE_BYTES - Int.SIZE_BYTES * 2)
        buf.flip()
        image = Pixmap(buf, width, height)
    }

    override fun serialize(): ByteArray {
        val b = ByteArray(image.pixels.remaining() + Short.SIZE_BYTES + Int.SIZE_BYTES * 2)
        image.pixels.get(b)

        b[b.size - 1] = (height ushr 24).toByte()
        b[b.size - 2] = (height ushr 16).toByte()
        b[b.size - 3] = (height ushr  8).toByte()
        b[b.size - 4] = (height ushr  0).toByte()

        b[b.size - 5] = (width ushr 24).toByte()
        b[b.size - 6] = (width ushr 16).toByte()
        b[b.size - 7] = (width ushr  8).toByte()
        b[b.size - 8] = (width ushr  0).toByte()

        b[b.size -  9] = (message.toInt() ushr 8).toByte()
        b[b.size - 10] = (message.toInt() ushr 0).toByte()

        return b
    }
}
