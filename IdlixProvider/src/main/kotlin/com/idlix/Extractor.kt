package com.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class Jeniusplay : ExtractorApi() {
    override var name = "Jeniusplay"
    override var mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val cleanUrl = url.replace(" ", "%20")
        try {
            val responseText = app.get(cleanUrl, referer = referer).text

            // LAPIS 1: Cari langsung link m3u8 di dalam teks halaman pemutar
            val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val directM3u8 = m3u8Regex.find(responseText)?.groupValues?.get(1)
            if (directM3u8 != null) {
                generateM3u8(name, directM3u8, cleanUrl).forEach(callback)
                return
            }

            // LAPIS 2: Request ke Player API (Mendukung index.php atau ajax.php secara otomatis)
            val hash = cleanUrl.split("/").last().substringAfter("data=")
            val endpoint = if (responseText.contains("ajax.php")) "$mainUrl/player/ajax.php?data=$hash&do=getVideo" else "$mainUrl/player/index.php?data=$hash&do=getVideo"

            val postResponse = app.post(
                url = endpoint,
                data = mapOf("hash" to hash, "r" to "$referer"),
                referer = cleanUrl,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
            ).text

            val videoUrl = Regex("""["']videoSource["']\s*:\s*["']([^"']+)["']""").find(postResponse)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(postResponse)?.groupValues?.get(1)?.replace("\\/", "/")

            if (videoUrl != null) {
                val finalUrl = videoUrl.replace(".txt", ".m3u8")
                generateM3u8(name, finalUrl, cleanUrl).forEach(callback)
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed for $cleanUrl: ${e.message}")
        }
    }

    data class ResponseSource(
        @JsonProperty("videoSource") val videoSource: String,
    )
}

class Majorplay : ExtractorApi() {
    override var name = "Majorplay"
    override var mainUrl = "https://majorplay.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val domain = "https://" + java.net.URI(url).host
            val document = app.get(url, referer = domain).document
            val htmlContent = document.html()
            
            var m3uLink = document.select("source").attr("src").trim()
            if (m3uLink.isEmpty()) {
                val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
                m3uLink = m3u8Regex.find(htmlContent)?.groupValues?.get(1) ?: ""
            }

            if (m3uLink.isNotEmpty()) {
                generateM3u8(name, m3uLink, domain).forEach(callback)
            }

            // Ambil Subtitle Indonesia / Internasional jika tersedia
            val scripts = document.selectFirst("script:containsData(subtitles)")?.data() ?: return
            val subRegex = Regex("""\\"label\\":\\"([^\\"]*?)\\"[^}]*?\\"path\\":\\"([^\\"]*?)\\"""")

            subRegex.findAll(scripts).forEach { match ->
                val label = match.groupValues[1]
                var vttUrl = match.groupValues[2].replace("\\/", "/")

                if (!vttUrl.startsWith("http")) {
                    vttUrl = domain.trimEnd('/') + "/" + vttUrl.trimStart('/')
                }
                subtitleCallback.invoke(
                    SubtitleFile(label, vttUrl)
                )
            }
        } catch (e: Exception) {
            Log.e(name, "Extraction failed: ${e.message}")
        }
    }
}