package Yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.concurrent.ConcurrentHashMap

class YunshanidProvider : MainAPI() {

    override var name = "Yunshanid"
    override var mainUrl = "https://yunshanid.site"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10)",
        "Referer" to "$mainUrl/"
    )

    // -----------------------------
    // MIRROR SYSTEM (ASCENDED)
    // -----------------------------
    private val mirrors = listOf(
        "https://yunshanid.site",
        "https://yunshanid.my.id",
        "https://yunshanid.vip"
    )

    private fun tryMirrors(path: String): String? {
        for (m in mirrors) {
            try {
                val doc = app.get("$m$path", headers = headers).document
                if (doc.select("article, .post, .bs").isNotEmpty()) {
                    return "$m$path"
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // -----------------------------
    // SMART TEXT RESOLVER
    // -----------------------------
    private fun Element.smartText(vararg sel: String): String? {
        return sel.firstNotNullOfOrNull { selectFirst(it)?.text()?.takeIf { t -> t.isNotBlank() } }
    }

    private fun Element.smartAttr(attr: String, vararg sel: String): String? {
        return sel.firstNotNullOfOrNull { selectFirst(it)?.attr(attr)?.takeIf { v -> v.isNotBlank() } }
    }

    // -----------------------------
    // MAIN PAGE
    // -----------------------------
    override val mainPage = mainPageOf(
        "" to "Update",
        "category/movie/page/%d/" to "Movie",
        "category/tv-series/page/%d/" to "TV",
        "category/anime/page/%d/" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val path = if (page == 1)
            request.data.replace("/page/%d/", "/")
        else request.data.format(page)

        val realUrl = tryMirrors(path) ?: "$mainUrl/$path"

        val doc = app.get(realUrl, headers = headers).document

        val items = doc.select("article, .bs, .post, .item, .grid-item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = items.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val title = smartText(".tt", "h2", ".title", ".post-title", ".entry-title")
            ?: return null

        val url = fixUrl(selectFirst("a")?.attr("href") ?: return null)

        val poster = smartAttr("src", "img", ".thumb img", ".poster img")
            ?: selectFirst("img")?.attr("data-src")

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    // -----------------------------
    // LOAD
    // -----------------------------
    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, headers = headers).document

        val title =
            doc.selectFirst("h1, .entry-title, .post-title")?.text()
                ?: "No Title"

        val poster =
            doc.selectFirst(".poster img, .thumb img, img")?.attr("src")

        val plot =
            doc.selectFirst(".entry-content p, .synopsis, .desc")?.text()

        val episodes = doc.select(".eplister li a, .list-episode li a")
            .mapIndexedNotNull { i, e ->
                val epUrl = fixUrl(e.attr("href"))
                val epName = e.text().ifBlank { "Episode ${i + 1}" }

                newEpisode(epUrl) {
                    this.name = epName
                    this.episode = i + 1
                }
            }

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    // -----------------------------
    // ASCENDED LOAD LINKS ENGINE
    // -----------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data, headers = headers).document

        val seen = hashSetOf<String>()
        var found = false

        // -------------------------
        // URL CLEANER (ASCENDED)
        // -------------------------
        fun clean(url: String): String {
            return url
                .replace("\\s".toRegex(), "")
                .replace(" ", "")
        }

        fun valid(url: String): Boolean {
            return url.startsWith("http") &&
                    !url.contains("javascript") &&
                    !url.contains("#")
        }

        fun extract(url: String, weight: Int = 1) {
            val u = clean(url)
            if (!valid(u)) return
            if (!seen.add(u)) return

            runCatching {
                loadExtractor(u, data, subtitleCallback) {
                    found = true
                    callback(it)
                }
            }
        }

        val sources = doc.select(
            "iframe[src], iframe[data-src], option[value], a[href], source[src]"
        )

        // -------------------------
        // PASS 1: HIGH CONFIDENCE
        // -------------------------
        sources.forEach {
            val url = it.attr("src")
                .ifBlank { it.attr("data-src") }
                .ifBlank { it.attr("value") }
                .ifBlank { it.attr("href") }

            val score = when {
                url.contains("filemoon") -> 3
                url.contains("streamwish") -> 3
                url.contains("voe") -> 2
                url.contains("gofile") -> 2
                url.contains("mp4upload") -> 2
                else -> 1
            }

            if (score >= 2) extract(url, score)
        }

        // -------------------------
        // PASS 2: FULL FALLBACK
        // -------------------------
        sources.forEach {
            val url = it.attr("src")
                .ifBlank { it.attr("data-src") }
                .ifBlank { it.attr("value") }
                .ifBlank { it.attr("href") }

            extract(url)
        }

        return found
    }
}