package com.github.blahblahbloopster.crypto

import com.beust.klaxon.Json

data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean = false, @Json(ignored = true) val messageCrypto: MessageCrypto) {
    @Json(ignored = true)
    val crypto get() = cryptoClient ?: run {
        messageCrypto.keyQuad ?: return@run null
        return@run CryptoClient(messageCrypto.keyQuad!!)
    }
    @Json(ignored = true)
    private var cryptoClient: CryptoClient? = null
}
