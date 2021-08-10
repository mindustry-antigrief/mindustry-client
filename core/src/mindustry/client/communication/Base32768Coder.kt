package mindustry.client.communication

import mindustry.client.communication.Base32768Coder.BITS
import mindustry.client.utils.*
import java.io.*
import java.math.*

/** You've heard of base64, now get ready for... base32768.  Encodes 15 bits of data into each unicode character,
 * which so far has not caused any problems.  If it turns out to break stuff, the [BITS] constant can be changed
 * to a more sensible value.  Note that it is not just a base conversion, it also has a length prefix.
 * FINISHME: maybe move to arbitrary base?  It sucks that it can't be 16 bit just because it has to avoid a couple chars.
 */
object Base32768Coder {
    private const val BITS = 15

    fun availableBytes(length: Int) = ((length.toDouble() * BITS) / 8).floor()

    fun encodedLengthOf(bytes: Int) = ((bytes.toDouble() * 8) / BITS).ceil()

    fun encode(input: ByteArray): String {
        var inp = BigInteger(byteArrayOf(1).plus(input))
        val out = mutableListOf<Int>()
        val andValue = 2.toBigInteger().pow(BITS) - 1.toBigInteger()
        while (inp != BigInteger.ZERO) {
            out.add((inp and andValue).toInt() + 128)
            inp = inp shr BITS
        }
        return String(out.toIntArray(), 0, out.size) + String(intArrayOf(input.size + 128), 0, 1)
    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        try {
            val length = input.codePointAt(input.length - 1) - 128
            var out = BigInteger("0")

            for ((index, s) in input.dropLast(1).chunked(1).withIndex()) {
                out += (s.codePointAt(0) - 128).toBigInteger() shl (index * BITS)
            }
            val outp = out.toByteArray().plus(0)
            return outp.sliceArray(1..length)
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
