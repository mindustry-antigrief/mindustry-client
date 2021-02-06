package com.github.blahblahbloopster.crypto

import arc.Core
import arc.util.Log
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.Initializable
import java.security.PublicKey

object KeyFolder : Initializable {
    val keys = mutableMapOf<String, PublicKey>()

    override fun initializeAlways() {
        val folder = Core.settings.getString("keyfolder") ?: run {
            Log.info("No key folder, not initializing keys")
            return
        }
        val dir = Core.files.absolute(folder)
        if (dir == null || !dir.isDirectory) {
            Log.warn("Invalid key folder")
            return
        }
        var loaded = 0
        for (file in dir.list()) {
            try {
                val contents = file.readString()
                val key = Crypto.deserializePublic(Base64Coder.decode(contents))
                val name = file.nameWithoutExtension().replace(" ", "")
                keys[name] = key
                loaded++
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
        Log.info("Loaded $loaded public keys")
    }
}
