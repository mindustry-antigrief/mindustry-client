package mindustry.client.crypto

import com.beust.klaxon.*
import mindustry.client.utils.*
import java.io.*
import java.math.*
import java.security.*
import java.security.cert.*
import kotlin.Pair

open class KeyStorage(val directory: File) {
    private val store: KeyStore = KeyStore.getInstance("BKS")
    private val password = "password123".toCharArray() // FINISHME: probably don't bother fixing tbh
    private var aliases: Map<String, String> = emptyMap()
    private val klaxon = Klaxon()
    val builtInCerts: List<X509Certificate>

    init {
        val certs = listOf(
            // foo
            "MIIBRzCByKADAgECAiCdhMB3qS0218EgXi0asK7io931Jjo2lWjYlL18rmoKxzAFBgMrZXEwDjEMMAoGA1UEAxMDZm9vMB4XDTIxMDgyMDAxNDYzOFoXDTM2MDgyNzAxNDYzOFowDjEMMAoGA1UEAxMDZm9vMEMwBQYDK2VxAzoAshx1nVePa4FkbLczm2Osp+IHU0ikU/+JyCUbs43klnP49tSybg1WS0NMCRINnEh30DzwOBKUs9+AoxMwETAPBgNVHRMBAf8EBTADAQH/MAUGAytlcQNzAE/uMei8ryVSW5l0GipNTko396syxdvdCQeUrlmwks1l5MNRkhjXHfYxlDow0KMM41K8r+w5HThPAAQFrzCMlKPVccZDdJy+xfLUUkmmjuO8q2TtDSmQw8cdXydPjPiK7t/l5WvJJFacO7QWJHUNl9QbAA==",
            // buthed
            "MIIBTTCBzqADAgECAiAhOw3ewOX+VKGajeKaxJoau2z32vy9aVn+HDDUZw7bFzAFBgMrZXEwETEPMA0GA1UEAxMGYnV0aGVkMB4XDTIxMDgyMDAxNDAwOFoXDTM2MDgyNzAxNDAwOFowETEPMA0GA1UEAxMGYnV0aGVkMEMwBQYDK2VxAzoAN+6Uos1pkds1qqvRM3p+w7RZWsB57C6C4NOCq8qCld7rLobD2XzxqKHzZJZntDSRsHCOtwtcq8SAoxMwETAPBgNVHRMBAf8EBTADAQH/MAUGAytlcQNzAJwsk9icL+lXbfzneAkzcahnrPEGUknpMXXe+x30RMOIwUDRL7KbRAid7GWluOIGboDqFVhoJ5atgGQ2hLh+kEx7ijhjC/tcYhbXMefWko+i6GPuUUdoHY75aGpUiXyIbAq6ch3K7YOItV3MY9ad20sxAA=="
        )
        val factory = CertificateFactory.getInstance("X509")
        builtInCerts = certs.map { factory.generateCertificate(it.base64()!!.inputStream()) as X509Certificate }
    }

    init {
        val file = directory.resolve("keys")
        val aliasFile = directory.resolve("aliases")
        if (file.exists()) {
            try {
                store.load(file.inputStream(), password)
            } catch (e: Exception) {
                e.printStackTrace()
                file.copyTo(directory.resolve("keys.backup${System.currentTimeMillis()}"))
                save()
            }
        } else {
            store.load(null)
            save()
        }

        for (cert in builtInCerts) {
            store.setCertificateEntry("trusted${cert.serialNumber}", cert)
        }

        if (aliasFile.exists()) {
            aliases = klaxon.parseArray<Pair<String, String>>(aliasFile.readText())?.toMap() ?: emptyMap()
        }
    }

    fun aliases() = aliases.toList()

    fun alias(certificate: X509Certificate): String? = aliases[certificate.serialNumber.toString()]

    fun alias(certificate: X509Certificate, alias: String?) {
        aliases = if (alias == null) {
            aliases.minus(certificate.serialNumber.toString())
        } else {
            aliases.plus(Pair(certificate.serialNumber.toString(), alias))
        }
        save()
    }

    fun removeAlias(certificate: X509Certificate) = alias(certificate, null)

    fun save() {
        try {
            store.store(directory.resolve("keys").outputStream(), password)
            directory.resolve("aliases").writeText(klaxon.toJsonString(aliases.entries.map { Pair(it.key, it.value) }))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun key(): PrivateKey? {
//        val public  = store.getKey("public",  password) as? PublicKey
        return store.getKey("private", password) as? PrivateKey
    }

    fun cert(): X509Certificate? {
        return store.getCertificate("cert") as? X509Certificate
    }

    fun chain(): List<X509Certificate>? {
        return store.getCertificateChain("private")?.mapNotNull { it as? X509Certificate }
    }

    fun key(kp: KeyPair, chain: List<X509Certificate>) {
//        store.setKeyEntry("public",  kp.public,  password, chain.toTypedArray())
        store.setKeyEntry("private", kp.private, password, chain.toTypedArray())
        save()
    }

    fun cert(certificate: X509Certificate) {
        store.setCertificateEntry("cert", certificate)
        save()
    }

    fun trusted(): List<X509Certificate> {
        val output = mutableListOf<X509Certificate>()
        for (entry in store.aliases()) {
            if (!entry.startsWith("trusted")) continue
            if (store.isCertificateEntry(entry)) {
                val loaded = store.getCertificate(entry) as? X509Certificate
                if (loaded != null) output.add(loaded)
            }
        }
        return output
    }

    fun findTrusted(sn: BigInteger) = store.getCertificate("trusted$sn") as? X509Certificate

    fun trust(certificate: X509Certificate) {
        store.setCertificateEntry("trusted${certificate.serialNumber}", certificate)
        save()
    }

    fun untrust(certificate: X509Certificate) {
        store.deleteEntry("trusted${certificate.serialNumber}")
        removeAlias(certificate)
        save()
    }
}
