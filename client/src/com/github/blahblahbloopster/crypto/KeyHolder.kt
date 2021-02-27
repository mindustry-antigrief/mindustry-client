package com.github.blahblahbloopster.crypto

import com.beust.klaxon.Json

data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean = false, @Json(ignored = true) val messageCrypto: MessageCrypto) {
    @Json(ignored = true)
    val crypto get() = internalCryptoClient ?: run {
        messageCrypto.keyQuad ?: return@run null
        return@run CryptoClient(messageCrypto.keyQuad!!).apply { internalCryptoClient = this }
    }
    @Json(ignored = true)
    /** DO NOT USE */
    var internalCryptoClient: CryptoClient? = null
}
