package com.autoembed

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import java.util.Base64
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AutoEmbedProvider : MainAPI() {
    override var mainUrl = "https://www.themoviedb.org"
    override var name = "AutoEmbed😒"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val tmdbApi = "https://api.themoviedb.org/3"
    private val tmdbApiKey = "b030404650f279792a8d3287232358e3"

    override val mainPage = mainPageOf(
        "$tmdbApi/trending/movie/day?api_key=$tmdbApiKey" to "Trending Movies",
        "$tmdbApi/movie/popular?api_key=$tmdbApiKey" to "Popular Movies",
        "$tmdbApi/movie/now_playing?api_key=$tmdbApiKey" to "Now Playing Movies",
        "$tmdbApi/movie/top_rated?api_key=$tmdbApiKey" to "Top Rated Movies",
        "$tmdbApi/movie/upcoming?api_key=$tmdbApiKey" to "Upcoming Movies",
        "$tmdbApi/trending/tv/day?api_key=$tmdbApiKey" to "Trending TV",
        "$tmdbApi/tv/popular?api_key=$tmdbApiKey" to "Popular TV",
        "$tmdbApi/tv/airing_today?api_key=$tmdbApiKey" to "Airing Today TV",
        "$tmdbApi/tv/top_rated?api_key=$tmdbApiKey" to "Top Rated TV",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(buildPagedUrl(request.data, page)).parsedSafe<TmdbResults>()
            ?: throw ErrorLoadingException("Invalid TMDB response")
        val items = response.results.orEmpty().mapNotNull { media ->
            media.toSearchResponse(inferMediaType(request.name))
        }
        val hasNext = response.page != null && response.totalPages != null && response.page < response.totalPages
        return newHomePageResponse(
            HomePageList(request.name, items),
            hasNext = hasNext
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$tmdbApi/search/multi?api_key=$tmdbApiKey&query=${query.urlEncoded()}&page=1"
        return app.get(url).parsedSafe<TmdbResults>()?.results.orEmpty()
            .mapNotNull { media -> media.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val media = parseSiteMediaUrl(url)
        val detailUrl = when (media.type) {
            "movie" -> "$tmdbApi/movie/${media.id}?api_key=$tmdbApiKey&append_to_response=external_ids,credits,recommendations,videos"
            else -> "$tmdbApi/tv/${media.id}?api_key=$tmdbApiKey&append_to_response=external_ids,credits,recommendations,videos"
        }
        val detail = app.get(detailUrl).parsedSafe<TmdbDetail>()
            ?: throw ErrorLoadingException("Invalid TMDB response")

        val title = detail.title ?: detail.name ?: throw ErrorLoadingException("Missing title")
        val poster = detail.posterPath.toPosterUrl()
        val background = detail.backdropPath.toBackdropUrl()
        val year = (detail.releaseDate ?: detail.firstAirDate)?.substringBefore("-")?.toIntOrNull()
        val tags = detail.genres.orEmpty().mapNotNull { it.name }.takeIf { it.isNotEmpty() }
        val actors = detail.credits?.cast.orEmpty().mapNotNull { cast ->
            val actorName = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                actor = Actor(actorName, cast.profilePath.toPosterUrl()),
                roleString = cast.character
            )
        }.takeIf { it.isNotEmpty() }
        val recommendations = detail.recommendations?.results.orEmpty().mapNotNull { it.toRecommendation() }
            .takeIf { it.isNotEmpty() }
        val trailers = detail.videos?.results.orEmpty()
            .mapNotNull { video ->
                if (video.site.equals("YouTube", true) && !video.key.isNullOrBlank()) {
                    "https://www.youtube.com/watch?v=${video.key}"
                } else {
                    null
                }
            }

        return if (media.type == "movie") {
            newMovieLoadResponse(
                title,
                "$mainUrl/movie/${media.id}",
                TvType.Movie,
                LinkData(
                    tmdbId = media.id,
                    imdbId = detail.externalIds?.imdbId,
                    type = media.type
                ).toJson()
            ) {
                posterUrl = poster
                backgroundPosterUrl = background
                plot = detail.overview
                this.year = year
                duration = detail.runtime
                tags?.let { this.tags = it }
                detail.voteAverage?.let { score = Score.from10(it) }
                actors?.let { this.actors = it }
                recommendations?.let { this.recommendations = it }
                if (trailers.isNotEmpty()) addTrailer(trailers)
            }
        } else {
            val episodes = buildEpisodes(media.id, detail.externalIds?.imdbId)
            newTvSeriesLoadResponse(
                title,
                "$mainUrl/tv/${media.id}",
                TvType.TvSeries,
                episodes
            ) {
                posterUrl = poster
                backgroundPosterUrl = background
                plot = detail.overview
                this.year = year
                tags?.let { this.tags = it }
                detail.voteAverage?.let { score = Score.from10(it) }
                actors?.let { this.actors = it }
                recommendations?.let { this.recommendations = it }
                showStatus = detail.status.toShowStatus()
                if (trailers.isNotEmpty()) addTrailer(trailers)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val emitted = mutableSetOf<String>()
        val wrappedCallback: (ExtractorLink) -> Unit = { link ->
            val key = "${link.name}|${link.url}"
            if (emitted.add(key)) callback(link)
        }

        invokeWebsiteExtractors(
            linkData = linkData,
            subtitleCallback = subtitleCallback,
            callback = wrappedCallback
        )

        return emitted.isNotEmpty()
    }

    private suspend fun buildEpisodes(tmdbId: Int, imdbId: String?): List<Episode> {
        val detail = app.get("$tmdbApi/tv/$tmdbId?api_key=$tmdbApiKey").parsedSafe<TmdbDetail>()
            ?: return emptyList()
        val allEpisodes = mutableListOf<Episode>()
        detail.seasons.orEmpty()
            .filter { it.seasonNumber != null }
            .forEach { season ->
                val seasonNumber = season.seasonNumber ?: return@forEach
                val seasonData = app.get(
                    "$tmdbApi/tv/$tmdbId/season/$seasonNumber?api_key=$tmdbApiKey"
                ).parsedSafe<TmdbSeasonDetail>() ?: return@forEach

                seasonData.episodes.orEmpty().forEach { episode ->
                    val episodeNumber = episode.episodeNumber ?: return@forEach
                    allEpisodes += newEpisode(
                        LinkData(
                            tmdbId = tmdbId,
                            imdbId = imdbId,
                            type = "tv",
                            season = seasonNumber,
                            episode = episodeNumber
                        ).toJson()
                    ) {
                        this.name = episode.name ?: "Episode $episodeNumber"
                        this.season = seasonNumber
                        this.episode = episodeNumber
                        this.posterUrl = episode.stillPath.toPosterUrl()
                        this.description = episode.overview
                        episode.voteAverage?.let { score = Score.from10(it) }
                    }.apply {
                        addDate(episode.airDate)
                    }
                }
            }
        return allEpisodes
    }

    private suspend fun invokeWebsiteExtractors(
        linkData: LinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        for (server in buildWebsiteServers(linkData)) {
            try {
                loadExtractor(server.url, server.referer ?: server.url, subtitleCallback) { link ->
                    runBlocking {
                        callback.invoke(
                            newExtractorLink(
                                source = server.name,
                                name = if (link.name.equals(server.name, ignoreCase = true)) {
                                    server.name
                                } else {
                                    "${server.name} - ${link.name}"
                                },
                                url = link.url,
                                type = link.type
                            ) {
                                quality = link.quality
                                headers = link.headers
                                extractorData = link.extractorData
                            }
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildWebsiteServers(linkData: LinkData): List<WebsiteServer> {
        val tmdbId = linkData.tmdbId ?: return emptyList()
        val imdbId = linkData.imdbId
        val season = linkData.season
        val episode = linkData.episode

        return if (season == null || episode == null) {
            buildList {
                add(WebsiteServer("VidSrc XYZ", "https://vidsrc.xyz/embed/movie/$tmdbId"))
                add(WebsiteServer("VidSrc TO", "https://vidsrc.to/embed/movie/$tmdbId"))
                add(WebsiteServer("VidSrc ME", "https://vidsrc.me/embed/movie?tmdb=$tmdbId"))
                add(WebsiteServer("Embed SU", "https://embed.su/embed/movie/$tmdbId"))
                add(WebsiteServer("Vidsrc CC", "https://vidsrc.cc/v2/embed/movie/$tmdbId"))
                add(WebsiteServer("2Embed", "https://www.2embed.cc/embed/$tmdbId"))
                add(WebsiteServer("Vidlink", "https://vidlink.pro/movie/$tmdbId"))
                add(WebsiteServer("RiveStream", "https://rivestream.net/embed?type=movie&id=$tmdbId"))
                add(WebsiteServer("Flicky", "https://flicky.host/embed/movie/?id=$tmdbId"))
                add(WebsiteServer("Vidsrc RIP", "https://vidsrc.rip/embed/movie/$tmdbId"))
                add(WebsiteServer("Cinemaos", "https://cinemaos.tech/player/$tmdbId"))
                add(WebsiteServer("Vidnest", "https://vidnest.fun/movie/$tmdbId"))
                add(WebsiteServer("Bravo", "https://moviesapi.club/movie/$tmdbId"))
                imdbId?.let {
                    add(WebsiteServer("DrivePlayer", "https://godriveplayer.com/player.php?imdb=$it"))
                }
            }
        } else {
            buildList {
                add(WebsiteServer("VidSrc XYZ", "https://vidsrc.xyz/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("VidSrc TO", "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("VidSrc ME", "https://vidsrc.me/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode"))
                add(WebsiteServer("Embed SU", "https://embed.su/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidsrc CC", "https://vidsrc.cc/v2/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("2Embed", "https://www.2embed.cc/embedtv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidlink", "https://vidlink.pro/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("RiveStream", "https://rivestream.net/embed?type=tv&id=$tmdbId&season=$season&episode=$episode"))
                add(WebsiteServer("Flicky", "https://flicky.host/embed/tv/?id=$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidsrc RIP", "https://vidsrc.rip/embed/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Cinemaos", "https://cinemaos.tech/player/$tmdbId/$season/$episode"))
                add(WebsiteServer("Vidnest", "https://vidnest.fun/tv/$tmdbId/$season/$episode"))
                add(WebsiteServer("Bravo", "https://moviesapi.club/tv/$tmdbId/$season/$episode"))
            }
        }
    }

    private fun buildPagedUrl(url: String, page: Int): String {
        if (page <= 1) return url
        val joiner = if (url.contains("?")) "&" else "?"
        return "$url${joiner}page=$page"
    }

    private fun parseSiteMediaUrl(url: String): SiteMedia {
        val clean = url.substringBefore("?")
        val parts = clean.trimEnd('/').split("/")
        val type = parts.getOrNull(parts.lastIndex - 1) ?: throw ErrorLoadingException("Invalid type")
        val id = parts.lastOrNull()?.toIntOrNull() ?: throw ErrorLoadingException("Invalid ID")
        return SiteMedia(type = type, id = id)
    }

    private fun inferMediaType(name: String): String? {
        return when {
            name.contains("movie", ignoreCase = true) -> "movie"
            name.contains("tv", ignoreCase = true) -> "tv"
            else -> null
        }
    }

    private fun TmdbMedia.toSearchResponse(defaultMediaType: String? = null): SearchResponse? {
        val resolvedMediaType = mediaType ?: defaultMediaType ?: when {
            !title.isNullOrBlank() -> "movie"
            !name.isNullOrBlank() -> "tv"
            else -> null
        } ?: return null
        val resolvedTitle = title ?: name ?: return null
        val resolvedId = id ?: return null
        val year = (releaseDate ?: firstAirDate)?.substringBefore("-")?.toIntOrNull()

        return if (resolvedMediaType == "movie") {
            newMovieSearchResponse(resolvedTitle, "$mainUrl/movie/$resolvedId", TvType.Movie) {
                posterUrl = posterPath.toPosterUrl()
                this.year = year
                voteAverage?.let { score = Score.from10(it) }
            }
        } else if (resolvedMediaType == "tv") {
            newTvSeriesSearchResponse(resolvedTitle, "$mainUrl/tv/$resolvedId", TvType.TvSeries) {
                posterUrl = posterPath.toPosterUrl()
                this.year = year
                voteAverage?.let { score = Score.from10(it) }
            }
        } else {
            null
        }
    }

    private fun TmdbMedia.toRecommendation(): SearchResponse? {
        val title = this.title ?: this.name ?: return null
        return if ((this.mediaType ?: "movie") == "movie") {
            newMovieSearchResponse(title, "$mainUrl/movie/${id ?: return null}", TvType.Movie) {
                posterUrl = posterPath.toPosterUrl()
                voteAverage?.let { score = Score.from10(it) }
            }
        } else {
            newTvSeriesSearchResponse(title, "$mainUrl/tv/${id ?: return null}", TvType.TvSeries) {
                posterUrl = posterPath.toPosterUrl()
                voteAverage?.let { score = Score.from10(it) }
            }
        }
    }

    private fun String?.toPosterUrl(): String? {
        if (this.isNullOrBlank()) return null
        return if (startsWith("/")) "https://image.tmdb.org/t/p/w500/$this" else this
    }

    private fun String?.toBackdropUrl(): String? {
        if (this.isNullOrBlank()) return null
        return if (startsWith("/")) "https://image.tmdb.org/t/p/original/$this" else this
    }

    private fun String?.toShowStatus(): ShowStatus? {
        return when (this) {
            "Returning Series", "In Production", "Planned" -> ShowStatus.Ongoing
            "Ended", "Canceled" -> ShowStatus.Completed
            else -> null
        }
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    data class SiteMedia(
        val type: String,
        val id: Int,
    )

    data class LinkData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val type: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    data class WebsiteServer(
        val name: String,
        val url: String,
        val referer: String? = null,
    )

    data class TmdbGenres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class TmdbVideo(
        @JsonProperty("key") val key: String? = null,
        @JsonProperty("site") val site: String? = null,
    )

    data class TmdbVideos(
        @JsonProperty("results") val results: List<TmdbVideo>? = null,
    )

    data class TmdbExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null,
    )

    data class TmdbCast(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class TmdbCredits(
        @JsonProperty("cast") val cast: List<TmdbCast>? = null,
    )

    data class TmdbResults(
        @JsonProperty("page") val page: Int? = null,
        @JsonProperty("results") val results: List<TmdbMedia>? = null,
        @JsonProperty("total_pages") val totalPages: Int? = null,
    )

    data class TmdbMedia(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
    )

    data class TmdbSeason(
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class TmdbEpisode(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
    )

    data class TmdbSeasonDetail(
        @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null,
    )

    data class TmdbDetail(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: List<TmdbGenres>? = null,
        @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
        @JsonProperty("credits") val credits: TmdbCredits? = null,
        @JsonProperty("recommendations") val recommendations: TmdbResults? = null,
        @JsonProperty("videos") val videos: TmdbVideos? = null,
        @JsonProperty("seasons") val seasons: List<TmdbSeason>? = null,
    )
}