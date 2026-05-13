package com.betbet.yunshanid

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

suspend fun extractSafe(
    url: String,
    referer: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {

    try {

        loadExtractor(
            url,
            referer,
            subtitleCallback,
            callback
        )

    } catch (_: Exception) {
    }

    try {

        val document: Document =
            app.get(url, referer = referer).document

        document.select("iframe").forEach {

            val iframe = it.attr("src")

            if (iframe.isNotBlank()) {

                try {

                    loadExtractor(
                        iframe,
                        url,
                        subtitleCallback,
                        callback
                    )

                } catch (_: Exception) {
                }
            }
        }

    } catch (_: Exception) {
    }
}