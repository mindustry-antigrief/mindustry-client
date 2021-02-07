package com.github.blahblahbloopster.crypto

import java.io.IOException
import java.math.BigInteger
import kotlin.jvm.Throws

/** You've heard of base64, now get ready for... base65536.  Encodes two bytes of data into each unicode character,
 * which so far has not caused any problems.  If it turns out to break stuff, the [BITS] constant can be changed
 * to a more sensible value.
 */
object Base65536Coder {
    private const val BITS = 16

    fun encode(input: ByteArray): String {
        var inp = BigInteger(byteArrayOf(1).plus(input))
        val out = mutableListOf<Int>()
        val andValue = 2.toBigInteger().pow(BITS) - 1.toBigInteger()
        while (inp != BigInteger.ZERO) {
            out.add((inp and andValue).toInt() + 128)
            inp = inp shr BITS
        }
        return String(out.toIntArray(), 0, out.size)
    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        try {
            var out = BigInteger("0")

            for ((index, s) in input.chunked(1).withIndex()) {
                out += (s.codePointAt(0) - 128).toBigInteger() shl (index * BITS)
            }
            val outp = out.toByteArray()
            return outp.sliceArray(1 until outp.size)
        } catch (e: Exception) {
            throw IOException(e)
        }
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
