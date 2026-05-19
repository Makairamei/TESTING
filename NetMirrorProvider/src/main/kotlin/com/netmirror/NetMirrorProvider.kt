package com.netmirror

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

class NetMirrorProvider : MainAPI() {
    override var mainUrl = "https://net22.cc"
    private val streamUrl = "https://net52.cc"
    private val tmdbApi = "https://api.themoviedb.org/3"
    private val tmdbApiKey = "b030404650f279792a8d3287232358e3"

    override var name = "NetMirror"
    override var lang = "id"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    private val ajaxHeaders = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "X-Requested-With" to "XMLHttpRequest",
        "User-Agent" to browserUserAgent
    )

    override val mainPage = listOf(
        MainPageData("Top Searches", "top"),
        MainPageData("Trending Movies", "tmdb_trending_movie"),
        MainPageData("Trending Series", "tmdb_trending_tv"),
        MainPageData("Popular Movies", "tmdb_popular_movie"),
        MainPageData("Popular Series", "tmdb_popular_tv")
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        if (page != 1) {
            return newHomePageResponse(emptyList(), false)
        }

        val lists = when (request.data) {
            "top" -> listOf(
                HomePageList(
                    request.name,
                    fetchTopSearches()
                )
            )

            "tmdb_trending_movie" -> listOf(
                HomePageList(
                    request.name,
                    fetchTmdbSection(
                        "trending/movie/week",
                        TvType.Movie
                    )
                )
            )

            "tmdb_trending_tv" -> listOf(
                HomePageList(
                    request.name,
                    fetchTmdbSection(
                        "trending/tv/week",
                        TvType.TvSeries
                    )
                )
            )

            "tmdb_popular_movie" -> listOf(
                HomePageList(
                    request.name,
                    fetchTmdbSection(
                        "movie/popular",
                        TvType.Movie
                    )
                )
            )

            "tmdb_popular_tv" -> listOf(
                HomePageList(
                    request.name,
                    fetchTmdbSection(
                        "tv/popular",
                        TvType.TvSeries
                    )
                )
            )

            else -> emptyList()
        }.filter { it.list.isNotEmpty() }

        return newHomePageResponse(
            lists,
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {

        val payload = app.get(
            "$mainUrl/search.php?s=${query.urlEncoded()}&t=$unixTime",
            headers = ajaxHeaders,
            referer = "$mainUrl/home"
        ).parsedSafe<SearchData>() ?: return emptyList()

        return payload.searchResult.map { item ->
            newMovieSearchResponse(
                item.t,
                LoadPayload(item.id, item.t).toJson(),
                TvType.Movie
            ) {
                this.posterUrl = posterUrl(item.id)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {

        val payload = tryParseJson<LoadPayload>(url)
            ?: throw ErrorLoadingException("Invalid NetMirror payload")

        val resolvedId = payload.id ?: resolveNetMirrorId(payload.title)

        val postData = resolvedId?.let {
            fetchPostData(it)
        }

        if (postData != null) {
            return buildDetailedLoad(
                url,
                payload.copy(id = resolvedId),
                postData
            )
        }

        val modalData = resolvedId?.let {
            fetchMiniModal(it)
        }

        val tmdb = fetchTmdbMetadata(
            payload.title,
            payload.tmdbType,
            payload.year
        )

        val fallbackId = resolvedId ?: payload.id

        val rawScore =
            modalData?.match.toRatingOrNull()
                ?: tmdb?.voteAverage?.let {
                    (it * 100).toInt()
                }

        return newMovieLoadResponse(
            payload.title,
            url,
            TvType.Movie,
            payload.copy(id = resolvedId).toJson()
        ) {

            this.posterUrl =
                fallbackId?.let(::posterUrl)
                    ?: tmdb?.posterPath.toTmdbPosterUrl()

            this.backgroundPosterUrl =
                fallbackId?.let(::backgroundPosterUrl)
                    ?: tmdb?.backdropPath.toTmdbBackdropUrl()

            this.plot =
                tmdb?.overview
                    ?: "Plot tidak ditemukan dari endpoint publik NetMirror."

            this.tags =
                modalData?.genre
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: tmdb?.genres
                        ?.mapNotNull { it.name?.trim() }
                        ?.filter { it.isNotBlank() }

            this.contentRating = modalData?.ua
            this.year = tmdb?.yearOrNull() ?: payload.year
            this.score = rawScore
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val payload =
            tryParseJson<LoadPayload>(data)
                ?: return false

        val resolvedId =
            payload.id
                ?: resolveNetMirrorId(payload.title)
                ?: return false

        val playlist =
            fetchPlaylist(resolvedId, payload.title)

        if (playlist.isNullOrEmpty()) {
            return false
        }

        playlist.forEach { item ->

            item.tracks.orEmpty()
                .filter {
                    it.kind.equals("captions", true)
                }
                .forEach { track ->

                    val file =
                        track.file?.cleanJsonUrl()
                            ?: return@forEach

                    val label =
                        track.label?.takeIf {
                            it.isNotBlank()
                        } ?: "Subtitle"

                    subtitleCallback(
                        newSubtitleFile(label, file) {
                            this.headers = mapOf(
                                "Referer" to "$streamUrl/"
                            )
                        }
                    )
                }

            item.sources.forEach { source ->

                val path =
                    source.file ?: return@forEach

                callback(
                    newExtractorLink(
                        name = source.label ?: "Stream",
                        source = name,
                        url = path.toAbsoluteStreamUrl()
                    ) {

                        this.referer = "$streamUrl/"

                        this.quality =
                            source.label.toNetMirrorQuality()

                        this.headers = mapOf(
                            "Referer" to "$streamUrl/",
                            "User-Agent" to exoPlayerUserAgent,
                            "Accept" to "*/*",
                            "Accept-Encoding" to "identity",
                            "Connection" to "keep-alive"
                        )
                    }
                )
            }
        }

        return true
    }
}