package mindustry.client.communication

object InvisibleCharCoder {
    private fun mapToChar(inp: Byte) = inp.toInt().toChar() + 0x1000

    private fun unMap(inp: Char) = (inp - 0x1000).code.toByte()

    fun encode(bytes: ByteArray): String = String(CharArray(bytes.size) { i -> mapToChar(bytes[i]) })

    fun decode(inp: String): ByteArray = ByteArray(inp.length) { i -> unMap(inp[i]) }

    fun strip(inp: String): String = if (inp.takeLast(2).all { (0xF80 until 0x107F).contains(it.code) }) inp.dropLast(2) else inp
}
