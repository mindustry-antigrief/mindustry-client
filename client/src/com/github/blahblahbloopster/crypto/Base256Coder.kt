package com.github.blahblahbloopster.crypto

import java.io.IOException
import kotlin.jvm.Throws

object Base256Coder {

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        try {
            val output = ByteArray(input.length)
            var i = 0
            for (char in input.codePoints()) {
                output[i] = (char - 128).toByte()
                i++
            }
            return output
        } catch (e: Exception) {
            throw IOException(e.stackTraceToString())
        }
    }

    fun encode(input: ByteArray): String {
        return String(input.map { it + 128 }.toIntArray(), 0, 1)
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
