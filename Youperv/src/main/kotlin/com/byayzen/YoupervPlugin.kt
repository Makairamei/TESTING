// ! Bu araÃ§ @ByAyzen tarafÄ±ndan | @Cs-GizliKeyif iÃ§in yazÄ±lmÄ±ÅŸtÄ±r.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YoupervPlugin: Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        registerMainAPI(Youperv())
    }
}
