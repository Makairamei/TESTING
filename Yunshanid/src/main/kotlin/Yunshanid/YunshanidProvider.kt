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
        "Referer" to mainUrl
    )

    // -----------------------------
    // OMEGA MEMORY GRAPH
    // -----------------------------
    private val successGraph = ConcurrentHashMap<String, Int>()
    private val urlCache = ConcurrentHashMap<String, List<String>>()

    // -----------------------------
    // PATTERN ENGINE (OMEGA CORE)
    // -----------------------------
    private fun extractUrlsFromText(text: String): List<String> {
        val regex = Regex("https?://[^\"]+")
        return regex.findAll(text).map { it.value }.toList()
    }

    private fun score(url: String): Int {
        return successGraph[url] ?: when {
            url.contains("filemoon") -> 5
            url.contains("streamwish") -> 5
            url.contains("voe") -> 4
            url.contains("gofile") -> 4
            url.contains("mp4upload") -> 4
            else -> 1
        }
    }

    // -----------------------------
    // SAFE FETCH (OMEGA RETRY)
    // -----------------------------
    private fun safeFetch(url: String): String? {
        return try {
            app.get(url, headers = headers).text
        } catch (_: Exception) {
            null
        }
    }

    // -----------------------------
    // MAIN PAGE (NO DOM DEPENDENCY)
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

        val doc = app.get("$mainUrl/$path", headers = headers).document

        val items = doc.select("article, .post, .bs, *")
            .mapNotNull { el ->
                val text = el.text()
                val urls = extractUrlsFromText(text)

                val url = urls.firstOrNull() ?: el.selectFirst("a")?.attr("href")
                val title = el.selectFirst("h1,h2,h3,.title,.tt")?.text()

                if (url == null || title == null) return@mapNotNull null

                newMovieSearchResponse(title, fixUrl(url), TvType.Movie)
            }.distinctBy { it.url }

        return newHomePageResponse(
            listOf(HomePageList(request.name, items)),
            hasNext = items.isNotEmpty()
        )
    }

    // -----------------------------
    // LOAD (SELF RECONSTRUCTION)
    // -----------------------------
    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, headers = headers).document

        val title =
            doc.selectFirst("h1,.entry-title,.post-title")?.text()
                ?: "No Title"

        val poster =
            doc.selectFirst("img")?.attr("src")

        val plot =
            doc.text().takeIf { it.length < 5000 }?.substring(0, minOf(300, doc.text().length))

        val episodes = doc.select("a[href]")
            .mapNotNull {
                val u = it.attr("href")
                if (!u.contains("episode") && !u.contains(url)) null else u
            }
            .distinct()
            .mapIndexed { i, ep ->
                newEpisode(fixUrl(ep)) {
                    this.name = "Episode ${i + 1}"
                    this.episode = i + 1
                }
            }

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes)
        }
    }

    // -----------------------------
    // OMEGA LOAD LINKS ENGINE
    // -----------------------------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data, headers = headers).document

        val rawText = doc.html()
        val urls = extractUrlsFromText(rawText).toMutableList()

        val seen = hashSetOf<String>()
        var found = false

        fun tryExtract(url: String) {
            if (!url.startsWith("http")) return
            if (!seen.add(url)) return

            runCatching {
                loadExtractor(url, data, subtitleCallback) {
                    found = true
                    successGraph[url] = (successGraph[url] ?: 0) + 1
                    callback(it)
                }
            }
        }

        // PRIORITY BY SUCCESS HISTORY
        urls.sortedByDescending { score(it) }.forEach {
            tryExtract(it)
        }

        // DOM fallback (only if regex fails)
        doc.select("iframe[src], source[src], a[href]")
            .forEach {
                val u = it.attr("src")
                    .ifBlank { it.attr("href") }

                tryExtract(u)
            }

        return found
    }
}