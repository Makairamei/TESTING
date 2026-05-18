// ! Bu araç @Kraptor123 tarafından | @Cs-GizliKeyif için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin
import android.content.Context

@CloudstreamPlugin
class EPornerPlugin : BasePlugin() {
    override fun load(context: Context) {
        registerMainAPI(EPorner())
    }
}