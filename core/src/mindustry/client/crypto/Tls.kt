package mindustry.client.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.tls.*
import org.bouncycastle.tls.Certificate
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.TlsECConfig
import org.bouncycastle.tls.crypto.impl.jcajce.JcaDefaultTlsCredentialedSigner
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCertificate
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCrypto
import org.bouncycastle.tls.crypto.impl.jcajce.JcaTlsCryptoProvider
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.util.*
import javax.security.auth.x500.X500Principal
import kotlin.random.Random

val provider = JcaTlsCryptoProvider()

private fun certChainToTlsCert(cert: X509Certificate, chain: List<X509Certificate>, crypto: JcaTlsCrypto, certificateRequestContext: ByteArray?) = Certificate(certificateRequestContext, listOf(cert).plus(chain).map { CertificateEntry(JcaTlsCertificate(crypto, it), null) }.toTypedArray())

private fun getAuth(expectedCert: X509Certificate, cert: X509Certificate, chain: List<X509Certificate>, crypto: JcaTlsCrypto, context: TlsContext, key: PrivateKey): TlsAuthentication = object : TlsAuthentication {
    override fun notifyServerCertificate(serverCertificate: TlsServerCertificate?) {
        val expected = expectedCert.encoded

        if (serverCertificate?.certificate?.certificateEntryList?.last()?.certificate?.encoded?.contentEquals(expected) != true) throw IOException(
            "Certificate is incorrect!"
        )
    }

    override fun getClientCredentials(certificateRequest: CertificateRequest): TlsCredentials {
        val params = TlsCryptoParameters(context)
        val convertedCert = certChainToTlsCert(cert, chain, crypto, byteArrayOf())
        val algo = SignatureAndHashAlgorithm.ed448
        return JcaDefaultTlsCredentialedSigner(params, crypto, key, convertedCert, algo)
    }
}

abstract class TlsPeerHolder(protected val peer: InternalTlsPeer, protected val protocol: TlsProtocol) : Closeable {
    abstract val expectedCert: X509Certificate
    val handshakeDone get() = peer.hanshakeDone
    val isClosed get() = protocol.isClosed
    var onHandshakeFinish: (() -> Unit)?
        get() = peer.onHandshakeFinish
        set(value) { peer.onHandshakeFinish = value }

    abstract fun start()

    fun read(): ByteArray = protocol.pollOutput()
    fun write(bytes: ByteArray) { protocol.offerInput(bytes) }

    fun readSecure(): ByteArray {
        val arr = ByteArray(protocol.applicationDataAvailable())
        protocol.readApplicationData(arr, 0, protocol.applicationDataAvailable())
        return arr
    }

    fun writeSecure(bytes: ByteArray) {
        protocol.writeApplicationData(bytes, 0, bytes.size)
    }

    override fun close() {
        protocol.close()
    }
}

class TlsClientHolder(cert: X509Certificate, chain: List<X509Certificate>, override val expectedCert: X509Certificate, key: PrivateKey) : TlsPeerHolder(TlsClientImpl(cert, chain, expectedCert, key), TlsClientProtocol()) {
    override fun start() {
        (protocol as TlsClientProtocol).connect(peer as TlsClientImpl)
    }
}

class TlsServerHolder(cert: X509Certificate, chain: List<X509Certificate>, override val expectedCert: X509Certificate, key: PrivateKey) : TlsPeerHolder(TlsServerImpl(cert, chain, expectedCert, key), TlsServerProtocol()) {
    override fun start() {
        (protocol as TlsServerProtocol).accept(peer as TlsServerImpl)
    }
}

interface InternalTlsPeer : TlsPeer {
    var onHandshakeFinish: (() -> Unit)?
    val hanshakeDone: Boolean
}

class TlsClientImpl(private val cert: X509Certificate, private val chain: List<X509Certificate>, private val expectedCert: X509Certificate, private val key: PrivateKey) : DefaultTlsClient(provider.create(SecureRandom.getInstanceStrong())), InternalTlsPeer {
    override var onHandshakeFinish: (() -> Unit)? = null
    override var hanshakeDone: Boolean = false
        private set

    override fun notifyHandshakeComplete() {
        onHandshakeFinish?.invoke()
        hanshakeDone = true
    }

    override fun getAuthentication(): TlsAuthentication = getAuth(expectedCert, cert, chain, crypto as JcaTlsCrypto, context, key)

    override fun getProtocolVersions() = arrayOf(ProtocolVersion.TLSv13)

    override fun getCipherSuites(): IntArray {
        return intArrayOf(CipherSuite.TLS_AES_256_GCM_SHA384)
    }
}

class TlsServerImpl(private val cert: X509Certificate, private val chain: List<X509Certificate>, private val expectedCert: X509Certificate, private val key: PrivateKey) : DefaultTlsServer(provider.create(SecureRandom.getInstanceStrong())), InternalTlsPeer {
    override var onHandshakeFinish: (() -> Unit)? = null
    override var hanshakeDone: Boolean = false
        private set

    override fun notifyHandshakeComplete() {
        onHandshakeFinish?.invoke()
        hanshakeDone = true
    }

    override fun getCertificateRequest(): CertificateRequest {
        val certificateAuthorities = Vector(mutableListOf(X500Name(expectedCert.subjectX500Principal.name)))
        val serverSigAlgs = Vector(mutableListOf(SignatureAndHashAlgorithm.ed448))

        return CertificateRequest(byteArrayOf(), serverSigAlgs, serverSigAlgs, certificateAuthorities)
    }

    override fun getECDSASignerCredentials(): TlsCredentialedSigner {
        val params = TlsCryptoParameters(context)
        val convertedCert = certChainToTlsCert(cert, chain, crypto as JcaTlsCrypto, byteArrayOf())
        val algo = SignatureAndHashAlgorithm.ed448
        return JcaDefaultTlsCredentialedSigner(params, crypto as JcaTlsCrypto, key, convertedCert, algo)
    }

    override fun getCredentials(): TlsCredentials {
        return ecdsaSignerCredentials
    }

    override fun getECDHConfig(): TlsECConfig {
        return TlsECConfig(NamedGroup.x448)
    }

    override fun notifyClientCertificate(clientCertificate: Certificate?) {
        val expected = expectedCert.encoded

        val lastEntry = clientCertificate?.certificateEntryList?.last()?.certificate
        if (lastEntry?.encoded?.contentEquals(expected) != true) throw IOException("Certificate is incorrect!")
    }

    override fun getProtocolVersions() = arrayOf(ProtocolVersion.TLSv13)

    override fun getCipherSuites(): IntArray {
        return intArrayOf(CipherSuite.TLS_AES_256_GCM_SHA384)
    }
}

fun genKey(): KeyPair {
    val keyGen = org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi.Ed448()
    keyGen.initialize(ECNamedCurveGenParameterSpec("ed448"), SecureRandom.getInstanceStrong())

    return keyGen.generateKeyPair()
}

fun genCert(kp: KeyPair, authority: Pair<X509Certificate, PrivateKey>?, name: String, isCa: Boolean = authority == null): X509Certificate {
    val subject = X500Principal("CN=$name")

    val sn = BigInteger(Random.Default.nextBytes(20))

    var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.add(Calendar.DATE, -7)
    val notBefore = calendar.time
    calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.add(Calendar.YEAR, 15)
    val notAfter = calendar.time

    val builder = if (authority == null)
        JcaX509v3CertificateBuilder(subject, sn, notBefore, notAfter, subject, kp.public)
    else
        JcaX509v3CertificateBuilder(authority.first, sn, notBefore, notAfter, subject, kp.public)

    builder.addExtension(Extension.basicConstraints, true, BasicConstraints(isCa))

    val signer = JcaContentSignerBuilder("ed448").build(authority?.second ?: kp.private)

    return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
}

fun TlsProtocol.pollOutput(): ByteArray {
    val arr = ByteArray(availableOutputBytes)
    readOutput(arr, 0, availableOutputBytes)
    return arr
}
