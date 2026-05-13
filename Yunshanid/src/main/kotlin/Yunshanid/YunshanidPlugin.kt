package Yunshanid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context // Wajib diimport

@CloudstreamPlugin
class YunshanidPlugin : Plugin() {
    // WAJIB ada parameter (context: Context) agar aplikasi bisa nge-load
    override fun load(context: Context) {
        registerMainAPI(YunshanidProvider())
    }
}
