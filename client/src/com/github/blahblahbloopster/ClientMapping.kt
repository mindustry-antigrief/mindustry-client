package com.github.blahblahbloopster

import com.github.blahblahbloopster.ui.ChangelogDialog
import com.github.blahblahbloopster.ui.FeaturesDialog
import com.github.blahblahbloopster.ui.FindDialog
import mindustry.client.ClientInterface

class ClientMapping : ClientInterface {

    override fun showFindDialog() {
        FindDialog.show()
    }

    override fun showChangelogDialog() {
        ChangelogDialog.show()
    }

    override fun showFeaturesDialog() {
        FeaturesDialog.show()
    }
}
