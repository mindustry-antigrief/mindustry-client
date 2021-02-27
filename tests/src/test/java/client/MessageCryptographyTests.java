package client;

import com.github.blahblahbloopster.crypto.*;
import kotlin.Unit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void testSending() throws InterruptedException {
        KeyQuad client1pair = Crypto.INSTANCE.generateKeyQuad();
        KeyQuad client2pair = Crypto.INSTANCE.generateKeyQuad();

        KeyHolder client1holder = new KeyHolder(client1pair.publicPair(), "client1", false, client2);
        KeyHolder client2holder = new KeyHolder(client2pair.publicPair(), "client2", false, client1);

        client1.getKeys().add(client2holder);
        client2.getKeys().add(client1holder);

        client1.getListeners().add(event -> {
            if (event instanceof MessageCrypto.Companion.SignatureEvent) {
                valid.set(((MessageCrypto.Companion.SignatureEvent) event).getValid());
            }
            return null;
        });
        client2.getListeners().add(event -> {
            if (event instanceof MessageCrypto.Companion.SignatureEvent) {
                valid.set(((MessageCrypto.Companion.SignatureEvent) event).getValid());
            }
            return null;
        });

        message = "Hello world!";
        client2.setPlayer(new MessageCrypto.PlayerTriple(client1.communicationSystem.getId(), Instant.now().getEpochSecond(), message));
        client1.sign(message, client1pair);
        Thread.sleep(100L);
        Assertions.assertTrue(valid.get());

        message = "Test test blah";
        client1.setPlayer(new MessageCrypto.PlayerTriple(client2.communicationSystem.getId(), Instant.now().getEpochSecond(), message));
        client2.sign(message, client2pair);
        Thread.sleep(100L);
        Assertions.assertTrue(valid.get());

        message = "aaa";
        client1.setPlayer(new MessageCrypto.PlayerTriple(client2.communicationSystem.getId(), Instant.now().getEpochSecond(), message));
        client2.sign(message, client2pair);
        Thread.sleep(100L);
        Assertions.assertTrue(valid.get());

        message = "aaaa";
        client1.setPlayer(new MessageCrypto.PlayerTriple(client2.communicationSystem.getId(), Instant.now().getEpochSecond(), message));
        client2.sign(message, client2pair);
        Thread.sleep(100L);
        Assertions.assertTrue(valid.get());

        message = "oh no";
        client1.setPlayer(new MessageCrypto.PlayerTriple(client2.communicationSystem.getId(), Instant.now().getEpochSecond(), message));
        client2.sign(message, client1pair);  // invalid, using wrong key to sign
        Thread.sleep(100L);
        Assertions.assertFalse(valid.get());

        message = "hello world";
        client1.encrypt(message, client2holder);

        message = "testing";
        client2.encrypt(message, client1holder);
    }
}
