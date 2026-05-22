package com.istarvin

import android.content.Context

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SulasokPlugin : BasePlugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        registerMainAPI(Sulasok())
    }
}

