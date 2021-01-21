package com.github.blahblahbloopster.crypto

import arc.util.serialization.Base64Coder

object Base256Coder {

    fun encode(input: ByteArray): String {
        val bytes = mutableListOf<Byte>()
        for (b in input) {
            bytes.add(b)
        }
        val builder = StringBuilder()
        for (i in input.indices) {
            builder.append(CharMapping.chars[bytes.removeLast() + 128])
        }
        return builder.reverse().toString()
    }

    fun decode(input: String): ByteArray? {
        val bytes = ByteArray(input.length)
        for (i in input.indices) {
            val letter = input.substring(i, i + 1)
            val pos = CharMapping.chars.indexOf(letter)
            if (pos == -1) {
                return null
            }
            bytes[i] = (pos - 128.toByte()).toByte()
        }
        return bytes
    }

    fun encode(string: String): String {
        return encode(string.toByteArray(Charsets.UTF_8))
    }

    fun decodeString(input: String): String {
        val decoded = decode(input) ?: return ""
        return String(decoded, Charsets.UTF_8)
    }

    internal object CharMapping {
        val chars = mutableListOf<String>()

        init {
            for (c in Base64Coder.urlsafeMap.encodingMap) {
                chars.add(c.toString())
            }
            val max = 0xF8FF
            val min = 0xF83F
            var i = min
            while (i <= max) {
                chars.add(String(intArrayOf(i), 0, 1))
                i += 1
            }
        }
    }
}
