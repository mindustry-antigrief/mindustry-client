package client;

import com.github.blahblahbloopster.crypto.*;
import kotlin.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

        client1.setKeys(new DummyKeyList());
        client2.setKeys(new DummyKeyList());
    }

    /** Tests that signing messages works. */
    @Test
    void testSending() {
        KeyQuad client1pair = Crypto.INSTANCE.generateKeyQuad();
        KeyQuad client2pair = Crypto.INSTANCE.generateKeyQuad();

        KeyHolder client1holder = new KeyHolder(client1pair.publicPair(), "client1", false);
        KeyHolder client2holder = new KeyHolder(client2pair.publicPair(), "client2", false);

        client1.getKeys().add(client2holder);
        client2.getKeys().add(client1holder);

        client1.communicationSystem.getListeners().add(
                (inp, id) -> {
                    valid.set(
                            client1.verify(message, id, inp, new PublicKeyPair(client2pair))
                    );
                    return Unit.INSTANCE;
                }
        );
        client2.communicationSystem.getListeners().add(
                (inp, id) -> {
                    valid.set(
                            client2.verify(message, id, inp, new PublicKeyPair(client1pair))
                    );
                    return Unit.INSTANCE;
                }
        );

        message = "Hello world!";
        client1.sign(message, client1pair);
        Assertions.assertTrue(valid.get());

        message = "Test test blah";
        client2.sign(message, client2pair);
        Assertions.assertTrue(valid.get());

        message = "aaa";
        client2.sign(message, client2pair);
        Assertions.assertTrue(valid.get());

        message = "aaaa";
        client2.sign(message, client2pair);
        Assertions.assertTrue(valid.get());

        message = "oh no";
        client2.sign(message, client1pair);  // invalid, using wrong key to sign
        Assertions.assertFalse(valid.get());

        message = "hello world";
        client1.encrypt(message, client2holder);

        message = "testing";
        client2.encrypt(message, client1holder);
    }
}
