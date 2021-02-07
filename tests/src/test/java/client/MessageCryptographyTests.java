package client;

import com.github.blahblahbloopster.crypto.Crypto;
import com.github.blahblahbloopster.crypto.MessageCrypto;
import kotlin.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageCryptographyTests {
    static MessageCrypto client1 = new MessageCrypto();
    static MessageCrypto client2 = new MessageCrypto();
    static String message = "";
    static AtomicBoolean valid = new AtomicBoolean();

    @BeforeAll
    static void init() {
        Crypto.INSTANCE.init();
        client1.init(new DummyCommunicationSystem());
        client2.init(new DummyCommunicationSystem());
    }

    /** Tests that signing messages works. */
    @Test
    void testSending() {
        KeyPair client1pair = Crypto.INSTANCE.generateKeyPair();
        KeyPair client2pair = Crypto.INSTANCE.generateKeyPair();

        client1.communicationSystem.getListeners().add(
                (inp, id) -> {
                    valid.set(
                            client1.verify(message, id, inp, client2pair.getPublic())
                    );
                    return Unit.INSTANCE;
                }
        );
        client2.communicationSystem.getListeners().add(
                (inp, id) -> {
                    valid.set(
                            client2.verify(message, id, inp, client1pair.getPublic())
                    );
                    return Unit.INSTANCE;
                }
        );

        message = "Hello world!";
        client1.sign(message, client1pair.getPrivate());
        Assertions.assertTrue(valid.get());

        message = "Test test blah";
        client2.sign(message, client2pair.getPrivate());
        Assertions.assertTrue(valid.get());

        message = "aaa";
        client2.sign(message, client2pair.getPrivate());
        Assertions.assertTrue(valid.get());

        message = "aaaa";
        client2.sign(message, client2pair.getPrivate());
        Assertions.assertTrue(valid.get());

        message = "oh no";
        client2.sign(message, client1pair.getPrivate());  // invalid, using wrong key to sign
        Assertions.assertFalse(valid.get());
    }
}
