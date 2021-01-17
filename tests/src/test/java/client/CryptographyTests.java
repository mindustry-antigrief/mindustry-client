package client;

import com.github.blahblahbloopster.crypto.Crypto;
import org.junit.jupiter.api.*;
import java.security.KeyPair;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** A few tests for {@link Crypto} */
public class CryptographyTests {

    @BeforeAll
    static void init(){
        Crypto.INSTANCE.init();
    }

    /** Tests signature creation and validation. */
    @Test
    static void testSigning() {
        KeyPair pair = Crypto.INSTANCE.generateKeyPair();

        byte[] input = new byte[128];
        new Random().nextBytes(input);

        byte[] signature = Crypto.INSTANCE.sign(input, pair.getPrivate());

        assertTrue(Crypto.INSTANCE.verify(input, signature, pair.getPublic()));
    }

    /** Tests key serialization and deserialization. */
    @Test
    static void testSerialization() {
        KeyPair pair = Crypto.INSTANCE.generateKeyPair();

        byte[] encodedPublic = Crypto.INSTANCE.encodePublic(pair.getPublic());
        byte[] encodedPrivate = Crypto.INSTANCE.encodePrivate(pair.getPrivate());

        assertEquals(Crypto.INSTANCE.decodePublic(encodedPublic), pair.getPublic());
        assertEquals(Crypto.INSTANCE.decodePrivate(encodedPrivate), pair.getPrivate());
    }
}
