package com.XSmoviebox

import android.content.Context

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class XSmovieboxProvider: BasePlugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        registerMainAPI(XSmoviebox())
    }
}

