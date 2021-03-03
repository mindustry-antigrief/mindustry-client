package client

import com.github.blahblahbloopster.*
import org.junit.jupiter.api.*
import kotlin.random.Random

class CompressionTests {

    @Test
    fun testCompression() {
        val input = Random.Default.nextBytes(1024)
        Assertions.assertArrayEquals(input, input.compress().inflate())
    }
}
