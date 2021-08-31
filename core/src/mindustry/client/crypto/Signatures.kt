package mindustry.client.crypto

import mindustry.client.communication.SignatureTransmission
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class Signatures(private val store: KeyStorage, private val ntp: AtomicReference<Clock>) {
    companion object {
        private val signature = Signature.getInstance("ed448", "BC")
        const val SIGNATURE_LENGTH = 114

        fun rawVerify(original: ByteArray, signatureBytes: ByteArray, publicKey: PublicKey): Boolean {
            return synchronized(signature) {
                try {
                    signature.initVerify(publicKey)
                    signature.update(original)
                    signature.verify(signatureBytes)
                } catch (e: Exception) {
                    false
                }
            }
        }

        fun rawSign(byteArray: ByteArray, key: PrivateKey): ByteArray? {
            return synchronized(signature) {
                signature.initSign(key)
                signature.update(byteArray)
                signature.sign()
            }
        }
    }

    fun signatureTransmission(original: ByteArray, commsId: Int, messageId: Short): SignatureTransmission? {
        val cert = store.cert() ?: return null
        val key = store.key() ?: return null
        val time = ntp.get().instant().toEpochMilli()
        val baseTransmission = SignatureTransmission(ByteArray(SIGNATURE_LENGTH), cert.serialNumber, time, commsId, messageId)
        val signature = rawSign(baseTransmission.toSignable(original), key) ?: return null
        return SignatureTransmission(signature, cert.serialNumber, time, commsId, messageId)
    }

    enum class VerifyResult {
        INVALID, VALID, UNKNOWN_CERT
    }

    fun verifySignatureTransmission(original: ByteArray, transmission: SignatureTransmission): VerifyResult {
        // the time is synchronized to NTP on both sides so this is fine
        if (abs(transmission.time - ntp.get().instant().toEpochMilli()) > 10_000) return VerifyResult.INVALID
        val foundCert = store.findTrusted(transmission.sn) ?: return VerifyResult.UNKNOWN_CERT
        val signedValue = transmission.toSignable(original)
        val valid = rawVerify(signedValue, transmission.signature, foundCert.publicKey)
        return if (valid) VerifyResult.VALID else VerifyResult.INVALID
    }
}
