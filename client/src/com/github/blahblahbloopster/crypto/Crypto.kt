package com.github.blahblahbloopster.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.security.cert.CertificateFactory

object Crypto {

    fun init() {
        Security.addProvider(BouncyCastleProvider())

        val factory = CertificateFactory.getInstance("X.509", "BC")
        factory.
    }
}

fun main() {
    println("Hello world")
}
