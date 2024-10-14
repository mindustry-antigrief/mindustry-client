package mindustry.client.crypto

import arc.*
import arc.util.*
import arc.util.serialization.*
import mindustry.*
import mindustry.client.*
import mindustry.client.communication.*
import mindustry.client.utils.*
import mindustry.gen.*
import java.io.*
import java.math.*
import java.security.*
import java.security.cert.*
import kotlin.system.*

open class KeyStorage(val directory: File) {
    private val store by lazy { KeyStore.getInstance("BKS") }
    private val password = "password123".toCharArray()
    private val aliases: HashMap<String, String> = hashMapOf()
    @Volatile private var loaded = false
    val builtInCerts by lazy {
        var certs = emptyList<X509Certificate>()
        Log.debug("Loaded developer certs in ${ measureTimeMillis {
            val factory = CertificateFactory.getInstance("X509")
            certs = arrayOf( // FINISHME: There has to be some way to cache these, right?
                // foo
                "MIIBRzCByKADAgECAiCdhMB3qS0218EgXi0asK7io931Jjo2lWjYlL18rmoKxzAFBgMrZXEwDjEMMAoGA1UEAxMDZm9vMB4XDTIxMDgyMDAxNDYzOFoXDTM2MDgyNzAxNDYzOFowDjEMMAoGA1UEAxMDZm9vMEMwBQYDK2VxAzoAshx1nVePa4FkbLczm2Osp+IHU0ikU/+JyCUbs43klnP49tSybg1WS0NMCRINnEh30DzwOBKUs9+AoxMwETAPBgNVHRMBAf8EBTADAQH/MAUGAytlcQNzAE/uMei8ryVSW5l0GipNTko396syxdvdCQeUrlmwks1l5MNRkhjXHfYxlDow0KMM41K8r+w5HThPAAQFrzCMlKPVccZDdJy+xfLUUkmmjuO8q2TtDSmQw8cdXydPjPiK7t/l5WvJJFacO7QWJHUNl9QbAA==",
                // buthed
                "MIIBTTCBzqADAgECAiAhOw3ewOX+VKGajeKaxJoau2z32vy9aVn+HDDUZw7bFzAFBgMrZXEwETEPMA0GA1UEAxMGYnV0aGVkMB4XDTIxMDgyMDAxNDAwOFoXDTM2MDgyNzAxNDAwOFowETEPMA0GA1UEAxMGYnV0aGVkMEMwBQYDK2VxAzoAN+6Uos1pkds1qqvRM3p+w7RZWsB57C6C4NOCq8qCld7rLobD2XzxqKHzZJZntDSRsHCOtwtcq8SAoxMwETAPBgNVHRMBAf8EBTADAQH/MAUGAytlcQNzAJwsk9icL+lXbfzneAkzcahnrPEGUknpMXXe+x30RMOIwUDRL7KbRAid7GWluOIGboDqFVhoJ5atgGQ2hLh+kEx7ijhjC/tcYhbXMefWko+i6GPuUUdoHY75aGpUiXyIbAq6ch3K7YOItV3MY9ad20sxAA==",
                // zxtej
                "MIIBdTCB9qADAgECAhTfCrIPbhvsEK1N5k65WKw/ocG4MDAFBgMrZXEwKzEpMCcGA1UEAxMgMzMzMzMzMzMzMzMzNDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQwHhcNMjEwODIwMTYxNzE0WhcNMzYwODI3MTYxNzE0WjArMSkwJwYDVQQDEyAzMzMzMzMzMzMzMzM0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDBDMAUGAytlcQM6AMovhskudhxJmw3MUGZB4jiLuMWjYNeA6LofNEBC0KLFW/flTaMs7fq6bsDR0MdvBjSQiebqk+xmAKMTMBEwDwYDVR0TAQH/BAUwAwEB/zAFBgMrZXEDcwAFsDdzP8Wc9Fe4JiC56cIL2TWOc6/fXKyJNu1dI+kmGk7UCuvUwGmAMZYwxif7xYWlkv6C42AWIoBQHWNMIyirVJXsWtZ1aWr3cS6G5pJtp/0y5Ow5utoeVlOAR1mIZNsjzIjRs3Rv+4mFhnD57kZTAAA=",
                // sbyte
                "MIIBPzCBwKADAgECAhRjpBHVpYocCsBspqpCG3Z0MNuRejAFBgMrZXEwEDEOMAwGA1UEAxMFU0J5dGUwHhcNMjMxMjIzMTUzNzAwWhcNMzgxMjMwMTUzNzAwWjAQMQ4wDAYDVQQDEwVTQnl0ZTBDMAUGAytlcQM6AOkvuvocpWgctfAOIoB2V3Ygkbgl6vNkdE7s5nwX8ohn+5ek0X0hnire8o8E1Jm9QFXCo+vMW7SqAKMTMBEwDwYDVR0TAQH/BAUwAwEB/zAFBgMrZXEDcwCLeeueP097rj8XBpsVzG3bcbf6edMpKUB7+YZNg9sPp2X0fupl6yJH5fxsEIz4P7fKTZlgtiLpUoD3aeqfka0WJCWDrn59SzHK2FjuG1iaLVQFGoCxo61AYc+KJnOIqaPDqPktd5vJ66dxKANZWgxdKgA=",
                // bala
                "MIIBRTCBxqADAgECAhTeD2MCcBrVQbkK5fxVGvtHBwMMyDAFBgMrZXEwEzERMA8GA1UEAxMIQmFsYU0zMTQwHhcNMjIwMjA4MTEzODI4WhcNMzcwMjE1MTEzODI4WjATMREwDwYDVQQDEwhCYWxhTTMxNDBDMAUGAytlcQM6AI5t3i1wE5UjzYMVYNfWMgrEycgfhCIwpENrMJr9EVBJSPdtxN7Agq+vv7xGnfTSD+pDnVsvtLDKgKMTMBEwDwYDVR0TAQH/BAUwAwEB/zAFBgMrZXEDcwCNmgB1xz7vM3a2EKfdgQgh5IOYqVqi+KYYnYjRUtB0sEoRRhN1qWc332+TwSqeP1rdljfNMdn9iwBr/LfxXaqrp+ud3IeFFFAtMkHrmdiy2TesLz3X9KfIWrRxP+m/uY/l5TXewUq74RG3eVD9xfA9CwA="
            ).map { factory.generateCertificate(it.base64()!!.inputStream()) as X509Certificate }
        } }")
        certs
    }

    init {
        Vars.mainExecutor.submit {
            val file = directory.resolve("keys")
            val aliasFile = directory.resolve("aliases")
            if (file.exists()) {
                try {
                    store.load(file.inputStream(), password)
                } catch (e: Exception) {
                    e.printStackTrace()
                    file.copyTo(directory.resolve("keys.backup${System.currentTimeMillis()}"))
                    save(true)
                }
            } else {
                store.load(null)
                save(true)
            }

            for (cert in builtInCerts) store.setCertificateEntry("trusted${cert.serialNumber}", cert)

            if (aliasFile.exists()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    aliases.putAll(Json().fromJson(HashMap::class.java, String::class.java, aliasFile.reader()) as HashMap<String, String>)
                } catch (_: SerializationException) { // Compatibility with old klaxon format FINISHME: Remove eventually
                    JsonReader().parse(aliasFile.bufferedReader()).forEach {
                        aliases[it.get("first").asString()] = it.get("second").asString()
                    }
                    save(true) // Overwrite the klaxon json
                }
            }
            loaded = true

            Core.app.post {
                if (isDeveloper()) {
                    register("update <name/id...>") { args, _ ->
                        val name = args.joinToString(" ")
                        val player = Groups.player.find { it.id == Strings.parseInt(name) } ?: Groups.player.minByOrNull { biasedLevenshtein(Strings.stripColors(it.name), name) }!!
                        Main.send(CommandTransmission(CommandTransmission.Commands.UPDATE, Main.keyStorage.cert() ?: return@register, player))
                    }
                }
            }
        }
    }


    fun loaded() = loaded

    fun aliases() = aliases.toList()

    fun alias(certificate: X509Certificate): String? = aliases[certificate.serialNumber.toString()]

    fun alias(certificate: X509Certificate, alias: String?) {
        when (alias) {
            null -> aliases -= certificate.serialNumber.toString()
            else -> aliases[certificate.serialNumber.toString()] = alias
        }
        save()
    }

    fun aliasOrName(certificate: X509Certificate) = alias(certificate) ?: certificate.readableName

    fun removeAlias(certificate: X509Certificate) = alias(certificate, null)

    fun save(force: Boolean = false) {
        if (!loaded && !force) return
        try {
            store.store(directory.resolve("keys").outputStream(), password)
            Json().toJson(aliases, HashMap::class.java, String::class.java, directory.resolve("aliases").writer())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun key(): PrivateKey? {
        return if (!loaded) null else try {
            store.getKey("private", password) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            directory.resolve("keys").copyTo(directory.resolve("keys.backup${System.currentTimeMillis()}"))
            store.load(null)
            null
        }
    }

    fun cert(): X509Certificate? {
        return if (!loaded) null else store.getCertificate("cert") as? X509Certificate
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
