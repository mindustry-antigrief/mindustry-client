package client

import mindustry.client.utils.*
import org.junit.jupiter.api.*
import kotlin.random.*

class CompressionTests {

    @Test
    fun testCompression() {
        val input = Random.Default.nextBytes(1024)
        Assertions.assertArrayEquals(input, input.compress().inflate())
    }
}
