package client

import mindustry.client.utils.compress
import mindustry.client.utils.inflate
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CompressionTests {

    @Test
    fun testCompression() {
        val input = Random.Default.nextBytes(1024)
        Assertions.assertArrayEquals(input, input.compress().inflate())
    }
}
