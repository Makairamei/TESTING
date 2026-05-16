package com.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class YunshanProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "YunshanID"
    override var lang = "id"
    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "$mainUrl" to "Home"
    )

    // ================= HOME =================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val doc = app.get(request.data).document

        val items = doc.select("article, .item, .grid-item").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null

            val title = a.attr("title").ifBlank { a.text() }
            val url = fixUrl(a.attr("href"))
            val poster = fixUrlNull(it.selectFirst("img")?.attr("src"))

            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, items)
    }

    // ================= SEARCH =================
    override suspend fun search(query: String): List<SearchResponse> {

        val doc = app.get("$mainUrl/?s=$query").document

        return doc.select("article, .item, .grid-item").mapNotNull {
            val a = it.selectFirst("a") ?: return@mapNotNull null

            val title = a.attr("title").ifBlank { a.text() }
            val url = fixUrl(a.attr("href"))

            newAnimeSearchResponse(title, url, TvType.Anime)
        }
    }

    // ================= LOAD DETAIL =================
    override suspend fun load(url: String): LoadResponse? {

        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: return null
        val poster = doc.selectFirst("img")?.attr("src")

        val episodes = doc.select("a[href*='episode'], .episode a").mapNotNull {
            val epUrl = fixUrl(it.attr("href"))
            val epName = it.text()

            newEpisode(epUrl) {
                this.name = epName
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.episodes = episodes
        }
    }

    // ================= STREAM =================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        doc.select("div#downloadb a").forEach { a ->

            val url = fixUrl(a.attr("href"))

            loadExtractor(url, subtitleCallback) { link ->

                callback.invoke(
                    ExtractorLink(
                        source = "YunshanID",
                        name = link.name,
                        url = link.url,
                        referer = link.referer ?: mainUrl,
                        quality = Qualities.P1080.value,
                        isM3u8 = link.url.contains("m3u8")
                    )
                )
            }
        }

        return true
    }
}