package com.github.blahblahbloopster.crypto

import java.io.IOException
import java.nio.ByteBuffer
import kotlin.jvm.Throws

/** Encodes data by converting the ByteArray to a string with 2 bytes per character.  This may cause problems. */
object Base65536Coder {

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        val inp = ByteBuffer.wrap(input.toByteArray())
        val size = inp.int
        try {
            val bytes = ByteArray(size)
            inp.get(bytes)
            return bytes
        } catch (e: Exception) {
            throw IOException(e.stackTraceToString())
        }
    }

    fun encode(input: ByteArray): String {
        val out = ByteBuffer.allocate(input.size + Int.SIZE_BYTES)
        out.putInt(input.size)
        out.put(input)
        return String(out.array())
    }

    fun encode(string: String): String {
        return encode(string.toByteArray(Charsets.UTF_8))
    }

    @Throws(IOException::class)
    fun decodeString(input: String): String {
        val decoded = decode(input)
        return String(decoded, Charsets.UTF_8)
    }
}
