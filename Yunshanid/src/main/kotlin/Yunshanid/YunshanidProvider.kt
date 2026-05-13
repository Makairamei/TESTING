package Yunshanid // Pakai huruf kecil 'package' agar tidak error

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl = "https://yunshanid.site"
    override var name = "Yunshanid"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    // Header stabil untuk nembus 403
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    // ---------------- MAIN PAGE ----------------

    override val mainPage = mainPageOf(
        "" to "Update",
        "category/movie/page/%d/" to "Movie",
        "category/tv-series/page/%d/" to "TV Series",
        "category/anime/page/%d/" to "Anime"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val path = if (page == 1)
            request.data.replace("/page/%d/", "/")
        else
            request.data.format(page)

        val doc = app.get("$mainUrl/$path", headers = headers).document

        val items = doc.select("article, .bs")
            .mapNotNull { it.toSearchItemSafe() }
            .distinctBy { it.url }

        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = items.isNotEmpty()
        )
    }

    // ---------------- SEARCH ----------------

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query", headers = headers).document

        return doc.select("article, .bs")
            .mapNotNull { it.toSearchItemSafe() }
    }

    // ---------------- SAFE PARSER ----------------

    private fun Element.toSearchItemSafe(): SearchResponse? {
        return try {
            val title = selectFirst(".tt, h2, .title")?.text()?.trim()
                ?: return null

            val url = selectFirst("a")?.attr("href")
                ?: return null

            val poster = selectFirst("img")?.attr("src")
                ?: selectFirst("img")?.attr("data-src")

            val typeText = select(".type").text().lowercase()

            val type = when {
                typeText.contains("tv") -> TvType.TvSeries
                typeText.contains("anime") || typeText.contains("donghua") -> TvType.Anime
                else -> TvType.Movie
            }

            // Gunakan this@YunshanidProvider.fixUrl agar tidak unresolved reference
            val finalUrl = this@YunshanidProvider.fixUrl(url)

            if (type == TvType.Movie) {
                newMovieSearchResponse(title, finalUrl) {
                    this.posterUrl = poster
                }
            } else {
                newAnimeSearchResponse(title, finalUrl, type) {
                    this.posterUrl = poster
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- LOAD DETAIL ----------------

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, headers = headers).document

        val title = doc.selectFirst("h1, .entry-title")?.text()
            ?: "Unknown"

        val poster = doc.selectFirst(".poster img, .thumb img")?.attr("src")
        val plot = doc.selectFirst(".entry-content p, .synopsis p")?.text()
        val tags = doc.select(".genre a, .genredesc a").map { it.text().trim() }

        val episodes = doc.select(".eplister li, .list-episode li")
            .mapIndexedNotNull { index, el ->

                val epUrl = el.selectFirst("a")?.attr("href")
                    ?: return@mapIndexedNotNull null

                val epName = el.select(".ep-num, .epl-num").text().ifBlank {
                    "Episode ${index + 1}"
                }

                // Gunakan this@YunshanidProvider.fixUrl
                newEpisode(this@YunshanidProvider.fixUrl(epUrl)) {
                    this.name = epName
                    this.episode = index + 1
                }
            }.reversed()

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        }
    }

    // ---------------- LOAD LINKS (STABLE CORE) ----------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        return try {
            val doc = app.get(data, headers = headers).document
            val seen = hashSetOf<String>()
            var found = false

            // Selector lebih luas untuk menangkap tombol server
            val elements = doc.select(
                "iframe, video source, a[href], .btn-download, .mirror-option option, .nav-tabs li a"
            )

            elements.forEach { el ->
                val src = when {
                    el.tagName() == "iframe" -> el.attr("src") ?: el.attr("data-src")
                    el.tagName() == "source" -> el.attr("src")
                    el.tagName() == "option" -> el.attr("value")
                    el.tagName() == "a" && el.hasAttr("data-embed") -> el.attr("data-embed")
                    else -> el.attr("href")
                }

                if (!src.isNullOrBlank() && src.startsWith("http") && seen.add(src)) {
                    runCatching {
                        loadExtractor(src, data, subtitleCallback, callback)
                        found = true
                    }
                }
            }
            found
        } catch (e: Exception) {
            false
        }
    }
}
