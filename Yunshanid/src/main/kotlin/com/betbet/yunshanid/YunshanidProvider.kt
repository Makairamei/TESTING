package com.betbet.yunshanid

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YunshanidProvider : MainAPI() {

    override var mainUrl =
        "https://yunshanid.site"

    override var name =
        "Yunshanid"

    override val hasMainPage =
        true

    override var lang =
        "id"

    override val supportedTypes =
        setOf(
            TvType.Anime
        )

    override val mainPage =
        mainPageOf(
            "$mainUrl/" to "Latest",
            "$mainUrl/ongoing/" to "Ongoing",
            "$mainUrl/completed/" to "Completed"
        )

    // =========================
    // SEARCH RESULT
    // =========================

    private fun Element.toSearchResult(): SearchResponse? {

        val title =
            selectFirst(
                "h2, .entry-title"
            )?.text()?.trim()
                ?: return null

        val href =
            selectFirst("a")
                ?.attr("href")
                ?: return null

        val poster =
            selectFirst("img")
                ?.attr("data-src")
                ?.ifBlank {

                    selectFirst("img")
                        ?.attr("src")
                }

        return newAnimeSearchResponse(
            title,
            href,
            TvType.Anime
        ) {

            this.posterUrl =
                poster
        }
    }

    // =========================
    // MAIN PAGE
    // =========================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            if (page == 1)
                request.data
            else
                "${request.data}page/$page/"

        val document =
            app.get(url).document

        val home =
            document.select(
                "article, .bs, .bsx"
            ).mapNotNull {
                it.toSearchResult()
            }

        return newHomePageResponse(
            request.name,
            home.distinctBy {
                it.url
            }
        )
    }

    // =========================
    // SEARCH
    // =========================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val document =
            app.get(
                "$mainUrl/?s=$query"
            ).document

        return document.select(
            "article, .bs, .bsx"
        ).mapNotNull {
            it.toSearchResult()
        }
    }

    // =========================
    // LOAD DETAIL
    // =========================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val document =
            app.get(url).document

        val title =
            document.selectFirst(
                "h1.entry-title, h1"
            )?.text()?.trim()
                ?: "No Title"

        val poster =
            document.selectFirst(
                ".thumb img, img"
            )?.attr("src")

        val description =
            document.selectFirst(
                ".entry-content p, p"
            )?.text()

        val tags =
            document.select(
                ".genxed a, .mgen a"
            ).map {
                it.text()
            }

        val episodes =
            document.select(
                ".eplister li"
            ).mapIndexed { index, ep ->

                val epUrl =
                    ep.selectFirst("a")
                        ?.attr("href")
                        ?: ""

                val epName =
                    ep.selectFirst("a")
                        ?.text()

                newEpisode(epUrl) {

                    this.name =
                        epName

                    this.episode =
                        index + 1
                }
            }

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {

            posterUrl =
                poster

            plot =
                description

            this.tags =
                tags

            addEpisodes(
                DubStatus.Subbed,
                episodes.reversed()
            )
        }
    }

    // =========================
    // LOAD LINKS
    // =========================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document =
            app.get(data).document

        val servers =
            mutableListOf<String>()

        // iframe
        document.select("iframe")
            .forEach {

                val src =
                    it.attr("src")

                if (src.isNotBlank()) {
                    servers.add(src)
                }
            }

        // direct links
        document.select("a[href]")
            .forEach {

                val href =
                    it.attr("href")

                if (
                    href.contains("mp4upload", true)
                    || href.contains("dood", true)
                    || href.contains("stream", true)
                    || href.contains("filemoon", true)
                ) {

                    servers.add(href)
                }
            }

        servers.distinct()
            .forEach { server ->

                try {

                    loadExtractor(
                        server,
                        data,
                        subtitleCallback,
                        callback
                    )

                } catch (_: Exception) {
                }
            }

        return true
    }
}