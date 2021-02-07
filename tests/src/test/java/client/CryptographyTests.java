package client;

import com.github.blahblahbloopster.crypto.Crypto;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
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
    void testSigning() {
        KeyPair pair = Crypto.INSTANCE.generateKeyPair();

        byte[] input = new byte[128];
        new Random().nextBytes(input);

        byte[] signature = Crypto.INSTANCE.sign(input, pair.getPrivate());

        assertTrue(Crypto.INSTANCE.verify(input, signature, (EdDSAPublicKey) pair.getPublic()));
    }

    /** Tests key serialization and deserialization. */
    @Test
    void testSerialization() {
        KeyPair pair = Crypto.INSTANCE.generateKeyPair();

        byte[] encodedPublic = Crypto.INSTANCE.serializePublic((EdDSAPublicKey) pair.getPublic());
        byte[] encodedPrivate = Crypto.INSTANCE.serializePrivate((EdDSAPrivateKey) pair.getPrivate());

        assertEquals(Crypto.INSTANCE.deserializePublic(encodedPublic), pair.getPublic());
        assertEquals(Crypto.INSTANCE.deserializePrivate(encodedPrivate), pair.getPrivate());
    }
}
