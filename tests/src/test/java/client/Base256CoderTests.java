package client;

import com.github.blahblahbloopster.crypto.Base256Coder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

public class Base256CoderTests {

    @Test
    static void testCoder() throws IOException {
        byte[] bytes = new byte[1_000_000];
        new Random().nextBytes(bytes);

        String encoded = Base256Coder.INSTANCE.encode(bytes);
        Assertions.assertArrayEquals(bytes, Base256Coder.INSTANCE.decode(encoded));
    }
}
