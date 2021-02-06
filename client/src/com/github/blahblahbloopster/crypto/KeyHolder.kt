package com.github.blahblahbloopster.crypto

import java.security.PublicKey

data class KeyHolder(val key: PublicKey, val name: String, val official: Boolean)
