package com.github.blahblahbloopster.crypto

import arc.Core
import arc.files.Fi
import arc.util.Log
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.Initializable
import java.io.File

object KeyFolder : Initializable {
    val keys = mutableListOf<KeyHolder>()
    var folder: Fi? = null

    override fun initializeAlways() {
//        keys.add(KeyHolder(PublicKeyPair(Base64Coder.decode(Core.files.internal("fooKey").readString())), "foo", true))
        val folderName = Core.settings.getString("keyfolder") ?: run {
            Log.info("No key folder, not initializing keys")
            return
        }
        folder = Core.files.absolute(folderName)
        if (folder == null || !folder!!.isDirectory || !folder!!.exists()) {
            Log.warn("Invalid key folder")
            return
        }
        val fldr = folder!!
        var loaded = 0
        for (file in fldr.list()) {
            try {
                val contents = Base64Coder.decode(file.readString())
                val name = file.nameWithoutExtension().replace(" ", "")
                keys.add(KeyHolder(PublicKeyPair(contents), name, false))
                loaded++
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
        Log.info("Loaded $loaded public keys")
    }
}
