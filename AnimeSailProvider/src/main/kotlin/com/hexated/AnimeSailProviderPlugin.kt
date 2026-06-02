
package com.hexated

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AnimeSailProviderPlugin: Plugin() {
    override fun load(context: Context) {
        AnimeSailLicenseClient.init(context, "AnimeSail")
        registerMainAPI(AnimeSailProvider())
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(Mp4UploadFix())
    }
}
