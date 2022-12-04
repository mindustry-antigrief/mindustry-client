package mindustry.client.communication

import mindustry.client.utils.*
import java.io.*
import java.math.*

/**
 * You've heard of base64, now get ready for... base32768.  Encodes 15 bits of data into each unicode character.
 * FINISHME: 16 bit with an escape character to avoid ascii control chars?  Encode more data in the escape char?
 */
object Base32768Coder {
    private const val BITS = 15

    fun availableBytes(length: Int) = ((length.toDouble() * BITS) / 8).floor()

    fun encodedLengthOf(bytes: Int) = ((bytes.toDouble() * 8) / BITS).ceil()

//    private const val MASK = 0b111111111111111U

    fun encode(input: ByteArray): String {
        var inp = BigInteger(byteArrayOf(1).plus(input))
        val out = mutableListOf<Int>()
        val andValue = 2.toBigInteger().pow(BITS) - 1.toBigInteger()
        while (inp != BigInteger.ZERO) {
            out.add((inp and andValue).toInt() + 128)
            inp = inp shr BITS
        }
        return String(out.toIntArray(), 0, out.size) + String(intArrayOf(input.size + 128), 0, 1)

//        val buffer = IntArray(encodedLengthOf(input.size) + 1)
//        for (i in buffer.indices) {
//            val bit = i * 15
//            val num = bit % 8
//            val n = bit / 8
//            val res = when (num) {
//                // 3 byte case
//                in 2..7 -> {
//                    var secondZeroed = false
//                    var thirdZeroed = false
//
//                    val firstByte  = if (n < input.size) input[n].toUByte().toUInt() else 0U
//                    val secondByte = if (n + 1 < input.size) input[n + 1].toUByte().toUInt() else { secondZeroed = true; 0U }
//                    val thirdByte  = if (n + 2 < input.size) input[n + 2].toUByte().toUInt() else { thirdZeroed = true; 0U }
//
//                    // note: problem when i == 7
//                            /*                    ((firstByte shl (num - 1)).shl(7).and(MASK) or
//                            (secondByte shl (num - 1)) or
//                            (thirdByte shr (9 - num)))*/
//
//                    ((firstByte shl (num + 6)).and(MASK) or
//                            (secondByte shl (num - 1)) or
//                            (thirdByte shr (9 - num)))/*.run {
//                        // 11111111 11111111 00000000
//                        //    11111 11111111 11
//
//                        // 00111111 11111111
//                        // we want 16383 (00111111 11111111)
//                        if (secondZeroed) this shr (15 - (9 - num)) else if (thirdZeroed) this shr (8 - (9 - num)) else this
//                            }*/
//                }
//                // 2 byte cases
//                0 -> {
//                    val firstByte   = if (n < input.size) input[n].toUByte().toUInt() else 0U
//                    val secondByte  = if (n + 1 < input.size) input[n + 1].toUByte().toUInt() else 0U
//                    ((firstByte shl 7) or (secondByte shr 1)) and MASK
//                }
//                else /* 1 */ -> {
//                    val firstByte   = if (n < input.size) input[n].toUByte().toUInt() else 0U
//                    val secondByte  = if (n + 1 < input.size) input[n + 1].toUByte().toUInt() else 0U
//                    (firstByte.shl(1).shr(1) or secondByte) and MASK
//                }
//            }
//            buffer[i] = res.toInt() + 128
//        }
//
////        println(input.joinToString())
//        println(out.joinToString { it.minus(128).toString(2).padStart(32, '0') })
//        println(buffer.joinToString { it.minus(128).toString(2).padStart(32, '0') })
//        return String(buffer, 0, buffer.size) + String(intArrayOf(input.size + 128), 0, 1)
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
