package com.github.blahblahbloopster.crypto

import com.beust.klaxon.*
import com.github.blahblahbloopster.*
import java.nio.*

/** Holds an ED25519 and X25519 keypair. */
class KeyQuad {
    val xPrivateKey: XPrivateKey
    val xPublicKey: XPublicKey
    val edPrivateKey: EdPrivateKey
    val edPublicKey: EdPublicKey

    constructor(xPrivateKey: XPrivateKey, xPublicKey: XPublicKey, edPrivateKey: EdPrivateKey, edPublicKey: EdPublicKey) {
        this.xPrivateKey = xPrivateKey
        this.xPublicKey = xPublicKey
        this.edPrivateKey = edPrivateKey
        this.edPublicKey = edPublicKey
    }

    constructor(input: ByteArray) {
        val buff = ByteBuffer.wrap(input)
        xPrivateKey = XPrivateKey(buff.bytes(XPrivateKey.SECRET_SIZE), 0)
        xPublicKey = XPublicKey(buff.bytes(XPublicKey.KEY_SIZE), 0)
        edPrivateKey = EdPrivateKey(buff.bytes(EdPrivateKey.KEY_SIZE), 0)
        edPublicKey = EdPublicKey(buff.bytes(EdPublicKey.KEY_SIZE), 0)
    }

    fun publicPair() = PublicKeyPair(xPublicKey, edPublicKey)

    fun serialize(): ByteArray {
        return xPrivateKey.encoded.plus(xPublicKey.encoded).plus(edPrivateKey.encoded).plus(edPublicKey.encoded)
    }

    override operator fun equals(other: Any?): Boolean {
        if (other is KeyQuad) {
            return other.xPrivateKey.encoded.contentEquals(xPrivateKey.encoded) &&
                    other.xPublicKey.encoded.contentEquals(xPublicKey.encoded) &&
                    other.edPrivateKey.encoded.contentEquals(edPrivateKey.encoded) &&
                    other.edPublicKey.encoded.contentEquals(edPublicKey.encoded)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = xPrivateKey.encoded.contentHashCode()
        result = 31 * result + xPublicKey.encoded.contentHashCode()
        result = 31 * result + edPrivateKey.encoded.contentHashCode()
        result = 31 * result + edPublicKey.encoded.contentHashCode()
        return result
    }
}

/** Internal object for encoding [KeyHolder]s to json */
object KeyHolderJson : Converter {
    override fun canConvert(cls: Class<*>) = cls == KeyHolder::class.java

    override fun fromJson(jv: JsonValue): KeyHolder {
        return try {
            KeyHolder(
                PublicKeyPair(jv.objString("keys").base64()!!),
                jv.objString("name"),
                jv.objInt("official") == 1,
                Main.messageCrypto
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            throw KlaxonException("Could not deserialize KeyHolder")
        }
    }

    override fun toJson(value: Any): String {
        if (value !is KeyHolder) throw KlaxonException("Not a public key pair")
        return """{"keys": "${value.keys.serialize().base64()}", "name": "${Render.escapeString(value.name)}", "official": ${if (value.official) 1 else 0}}"""
    }
}

/** Contains the public ED25519 and X25519 keys. */
class PublicKeyPair {

    @Json(ignored = true)
    val edPublicKey: EdPublicKey
    @Json(ignored = true)
    val xPublicKey: XPublicKey

    constructor(xPublicKey: XPublicKey, edPublicKey: EdPublicKey) {
        this.xPublicKey = xPublicKey
        this.edPublicKey = edPublicKey
    }

    /** Deserializes the keys from a [ByteArray].  Compatible with [serialize]. */
    constructor(input: ByteArray) {
        val buf = ByteBuffer.wrap(input)
        xPublicKey = XPublicKey(buf.bytes(XPublicKey.KEY_SIZE), 0)
        edPublicKey = EdPublicKey(buf.bytes(EdPublicKey.KEY_SIZE), 0)
    }

    /** Takes the public components of the given [KeyQuad] and creates a new PublicKeyPair. */
    constructor(key: KeyQuad) {
        xPublicKey = key.xPublicKey
        edPublicKey = key.edPublicKey
    }

    /** Serializes this keypair to a [ByteArray].  Compatible with the ByteArray constructor. */
    fun serialize(): ByteArray {
        return xPublicKey.encoded.plus(edPublicKey.encoded)
    }

    override operator fun equals(other: Any?): Boolean {
        if (other is PublicKeyPair) {
            return other.xPublicKey.encoded.contentEquals(xPublicKey.encoded) &&
                    other.edPublicKey.encoded.contentEquals(edPublicKey.encoded)
        }
        return false
    }

    override fun hashCode(): Int {
        var result = xPublicKey.encoded.contentHashCode()
        result = 31 * result + edPublicKey.encoded.contentHashCode()
        return result
    }
}
