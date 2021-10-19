package client

import mindustry.client.*
import mindustry.client.crypto.*
import org.bouncycastle.jce.provider.*
import org.junit.jupiter.api.*
import java.nio.file.*
import java.security.*
import java.time.*
import java.util.concurrent.atomic.*
import kotlin.random.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignatureTest {
    @BeforeAll
    fun initialize() {
        // finishme add a shared initialization thing so we don't get more than one bouncycastle provider registered when multiple tests are run
        Security.addProvider(BouncyCastleProvider())
        Main.keyStorage = KeyStorage(Files.createTempDirectory("keystorage").toFile())
    }

    @Test
    fun testRawSignatures() {
        val keyPair = genKey()

        val data = Random.nextBytes(123)
        val signature = Signatures.rawSign(data, keyPair.private)!!
        Assertions.assertTrue(Signatures.rawVerify(data, signature, keyPair.public))
    }

    @Test
    fun testSignatureTransmission() {
        val keyPair = genKey()
        val cert = genCert(keyPair, null, "testCert")

        val tmpDir = Files.createTempDirectory("signatureTest")
        val tmpDir2 = Files.createTempDirectory("signatureTest2")
        val store = KeyStorage(tmpDir.toFile())

        store.cert(cert)
        store.key(keyPair, listOf(cert))

        val store2 = KeyStorage(tmpDir2.toFile())
        store2.trust(cert)

        val signatures  = Signatures(store,  AtomicReference(Clock.fixed(Instant.now(), ZoneId.of("UTC"))))
        val signatures2 = Signatures(store2, AtomicReference(Clock.fixed(Instant.now(), ZoneId.of("UTC"))))

        val msg = "Hello, world!"
        val signatureTransmission = signatures.signatureTransmission(msg.encodeToByteArray(), 0, 5)!!

        val validity = signatures2.verifySignatureTransmission(msg.encodeToByteArray(), signatureTransmission)
        Assertions.assertEquals(validity.first, Signatures.VerifyResult.VALID)
    }
}
