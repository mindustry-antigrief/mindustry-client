package mindustry.client.communication

import arc.graphics.Pixmap
import arc.graphics.PixmapIO
import mindustry.client.utils.decodeJPG
import mindustry.client.utils.jpg
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.random.Random

class ImageTransmission : Transmission {
    override var id = Random.nextLong()
    override val secureOnly = false
    val message: Short
    val image: Pixmap
//    val width: Int
//    val height: Int

    companion object {
//        private val writer = PixmapIO.PngWriter()
//        private val reader = PixmapIO.PngReader()
//        private val out = ByteArrayOutputStream()
    }

    constructor(message: Short, image: Pixmap) {
        this.message = message
        this.image = image
//        this.width = image.width
//        this.height = image.height
//        println("SENDING width: $width height: $height message ID: $message")
    }

    constructor(b: ByteArray, id: Long, senderID: Int) {
//        val b = input.inflate()
        val metadata = ByteBuffer.wrap(b)
//        height = int(b, b.size - 4)
//        width = int(b, b.size - 8)
//        message = short(b, b.size - 10)
        message = metadata.short
//        width = metadata.int
//        height = metadata.int
//        println("width: $width height: $height message ID: $message")

//        val buf = ByteBuffer.allocateDirect(b.size - Short.SIZE_BYTES - Int.SIZE_BYTES * 2)
//        buf.put(b, 0, b.size - Short.SIZE_BYTES - Int.SIZE_BYTES * 2)
//        buf.flip()
//        image = Pixmap(buf, width, height)
//        val inp = ByteArrayInputStream(b, 2, b.size - 2)
//        image = Pixmap(reader.read(inp), reader.width, reader.height)
        image = decodeJPG(b, 2, b.size - 2)!!
    }

    override fun serialize(): ByteArray {
//        out.reset()
//        writer.write(out, image)
//        val b = ByteArray(out.size() + Short.SIZE_BYTES + Int.SIZE_BYTES * 2)
//        image.pixels.get(b, 0, image.pixels.remaining())
//        out.toByteArray().copyInto(b, 0, out.size())

//        val buf = ByteBuffer.wrap(b, b.size - 11, 10)
//        val buf = ByteBuffer.allocate(10)

//        buf.putShort(message)
//        buf.putInt(width)
//        buf.putInt(height)

//        b[b.size - 1] = (height ushr 24).toByte()
//        b[b.size - 2] = (height ushr 16).toByte()
//        b[b.size - 3] = (height ushr  8).toByte()
//        b[b.size - 4] = (height ushr  0).toByte()
//
//        b[b.size - 5] = (width ushr 24).toByte()
//        b[b.size - 6] = (width ushr 16).toByte()
//        b[b.size - 7] = (width ushr  8).toByte()
//        b[b.size - 8] = (width ushr  0).toByte()
//
//        b[b.size -  9] = (message.toInt() ushr 8).toByte()
//        b[b.size - 10] = (message.toInt() ushr 0).toByte()

//        return b//.compress()
//        return ByteBuffer.allocate(2).putShort(message).array() + out.toByteArray().apply { println("image size: ${size / 1024} KiB") }
        return ByteBuffer.allocate(2).putShort(message).array() + jpg(image)!!.apply { println("image size: ${size / 1024} KiB") }
    }
}
