package com.github.blahblahbloopster.crypto

import com.github.blahblahbloopster.Main

data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean) {
    private var cryptoClient: CryptoClient? = null
    val crypto get() = cryptoClient ?: run {
        Main.messageCrypto?.keyQuad ?: return@run null
        return@run CryptoClient(Main.messageCrypto!!.keyQuad!!)
    }
}
