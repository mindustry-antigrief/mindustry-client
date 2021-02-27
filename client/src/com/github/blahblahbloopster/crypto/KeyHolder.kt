package com.github.blahblahbloopster.crypto

import com.beust.klaxon.Json

data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean = false, @Json(ignored = true) val messageCrypto: MessageCrypto) {
    @Json(ignored = true)
    val crypto = if (messageCrypto.keyQuad != null) CryptoClient(messageCrypto.keyQuad!!).apply { generate(keys) } else null
}
