package client

import mindustry.client.crypto.KeyStorage
import mindustry.client.crypto.Signatures
import mindustry.client.crypto.genCert
import mindustry.client.crypto.genKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.security.Security
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SignatureTest {
    @BeforeAll
    fun initialize() {
        // finishme add a shared initialization thing so we don't get more than one bouncycastle provider registered when multiple tests are run
        Security.addProvider(BouncyCastleProvider())
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
