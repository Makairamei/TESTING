package com.betbet.yunshanid

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YunshanidPlugin: Plugin() {
    override fun load() {
        registerMainAPI(YunshanidProvider())
    }
}