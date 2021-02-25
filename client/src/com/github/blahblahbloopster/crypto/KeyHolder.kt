package com.github.blahblahbloopster.crypto

import com.beust.klaxon.Json
import com.github.blahblahbloopster.Main

data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean = false) {
    @Json(ignored = true)
    val crypto get() = cryptoClient ?: run {
        Main.messageCrypto.keyQuad ?: return@run null
        return@run CryptoClient(Main.messageCrypto.keyQuad!!)
    }
    @Json(ignored = true)
    private var cryptoClient: CryptoClient? = null
}
