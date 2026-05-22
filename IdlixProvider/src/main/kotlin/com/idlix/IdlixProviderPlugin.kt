package com.idlix

import android.content.Context

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class IdlixProviderPlugin: BasePlugin() {
    override fun load(context: Context) {
        LicenseClient.init(context) // FIX MUTLAK: Hapus parameter context karena BasePlugin butuh fungsi kosongan!
        registerMainAPI(IdlixProvider())
        registerExtractorAPI(Jeniusplay())
        registerExtractorAPI(Majorplay())
    }
}
