package com.github.blahblahbloopster

import com.github.blahblahbloopster.ui.FindDialog
import mindustry.client.ClientInterface

class ClientMapping : ClientInterface {

    override fun showFindDialog() {
        FindDialog.show()
    }
}
