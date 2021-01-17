package com.github.blahblahbloopster

import arc.ApplicationListener
import com.github.blahblahbloopster.crypto.Crypto

object Main : ApplicationListener {

    /** Run on client load. */
    override fun init() {
        Crypto.init()
    }

    /** Run once per frame. */
    override fun update() {}

    /** Run when the object is disposed. */
    override fun dispose() {}
}
