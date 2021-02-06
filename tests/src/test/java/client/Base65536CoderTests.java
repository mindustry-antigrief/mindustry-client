package client;

import com.github.blahblahbloopster.crypto.Base65536Coder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;

public class Base65536CoderTests {

    @Test
    static void testCoder() throws IOException {
        byte[] bytes = new byte[1_000_000];
        new Random().nextBytes(bytes);

        String encoded = Base65536Coder.INSTANCE.encode(bytes);
        Assertions.assertArrayEquals(bytes, Base65536Coder.INSTANCE.decode(encoded));
    }
}
