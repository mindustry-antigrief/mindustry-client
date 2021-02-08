package com.github.blahblahbloopster.crypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters

data class KeyHolder(val key: Ed25519PublicKeyParameters, val name: String, val official: Boolean)
