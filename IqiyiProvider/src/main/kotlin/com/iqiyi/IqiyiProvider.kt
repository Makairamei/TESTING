package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.Extensions.toQueryParams

class IqiyiProvider : MainAPI() {
    override var mainUrl = "https://www.iq.com"
    override var name = "iQIYI Internasional"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    companion object {
        private const val API_BASE = "https://api.iq.com"
        
        // Headers emulasi berdasarkan hasil bongkar Webpack Bundle JS kemarin
        private val baseHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
            "Referer" to "https://www.iq.com/",
            "Accept" to "application/json, text/plain, */*"
        )

        // Helper otomatis menyisipkan parameter platformId=3 dari hasil reverse engineering
        private fun getIqiyiParams(extra: Map<String, String> = emptyMap()): String {
            val default = mapOf(
                "platformId" to "3",
                "lang" to "id_id",
                "mod" to "id"
            )
            return (default + extra).toQueryParams()
        }
    }

    // ==================== 1. SEARCH / PENCARIAN ====================
    override suspend fun search(query: String): List<SearchResponse> {
        // ⚠️ YANG KURANG: Jalur URL API asli untuk search
        val searchUrl = "$API_BASE/JALUR_API_SEARCH_ASLI${getIqiyiParams(mapOf("k_word" to query))}"
        val rawResponse = app.get(searchUrl, headers = baseHeaders).text
        
        val searchData = parseJson<IqiyiSearchResponse>(rawResponse)
        return searchData.data?.list?.map { item ->
            newTvSeriesSearchResponse(
                name = item.name ?: "",
                url = item.albumIdStr ?: "", 
                tvType = TvType.TvSeries // ✅ FIX: diubah dari 'type' ke 'tvType'
            ) {
                this.posterUrl = item.pic
            }
        } ?: emptyList()
    }

    // ==================== 2. LOAD / DETAIL HALAMAN ====================
    override suspend fun load(url: String): LoadResponse {
        // 'url' di sini berisi albumIdStr (Parent ID)
        
        // ⚠️ YANG KURANG: URL API untuk menarik daftar semua episode di dalam satu Album/Series
        val episodeListUrl = "$API_BASE/JALUR_API_LIST_EPISODE_ASLI${getIqiyiParams(mapOf("albumId" to url))}"
        val rawEpisodes = app.get(episodeListUrl, headers = baseHeaders).text
        
        val albumDetail = parseJson<IqiyiDetailResponse>(rawEpisodes).data
        
        val episodes = listOf(
            Episode(
                data = albumDetail?.qipuIdStr ?: "", // Mengoper qipuIdStr spesifik episode ke loadLinks()
                name = albumDetail?.name ?: "Episode 1",
                episode = albumDetail?.order ?: 1
            )
        )

        return newTvSeriesLoadResponse(
            name = albumDetail?.albumName ?: "",
            url = url,
            tvType = TvType.TvSeries, // ✅ FIX: diubah dari 'type' ke 'tvType'
            episodes = episodes
        ) {
            this.posterUrl = albumDetail?.posterPic
            this.plot = albumDetail?.albumDesc
        }
    }

    // ==================== 3. EXTRACTOR / PLAYER LINKS ====================
    override suspend fun loadLinks(
        data: String, // Berisi qipuIdStr / tvid per episode
        isCaster: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // ⚠️ YANG KURANG: URL API VMS / Stream Dispatcher asli milik iQIYI
        val vmsUrl = "$API_BASE/JALUR_API_VMS_PLAYER_ASLI${getIqiyiParams(mapOf("tvid" to data))}"
        val rawPlayerResponse = app.get(vmsUrl, headers = baseHeaders).text
        
        val playerResult = parseJson<IqiyiPlayerResponse>(rawPlayerResponse)
        
        playerResult.data?.streamList?.forEach { stream ->
            val videoUrl = stream.secureUrl ?: return@forEach
            val cdnName = stream.cdnProvider ?: "iqiyi_edge"
            val bid = stream.bitrateId ?: 200
            
            val quality = when (bid) {
                100, 200 -> Qualities.P360.value
                300 -> Qualities.P720.value
                500, 600 -> Qualities.P1080.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                ExtractorLink(
                    source = "iQIYI - ${cdnName.uppercase()}",
                    name = "iQIYI Stream",
                    url = videoUrl,
                    referer = "https://www.iq.com/",
                    quality = quality,
                    isM3u8 = videoUrl.contains(".m3u8")
                )
            )
        }
        return true
    }
}

// ==================== JSON DATA MODELS (JACKSON) ====================
data class IqiyiSearchResponse(@JsonProperty("data") val data: IqiyiSearchData? = null)
data class IqiyiSearchData(@JsonProperty("list") val list: List<IqiyiSearchItem>? = null)
data class IqiyiSearchItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("albumIdStr") val albumIdStr: String? = null,
    @JsonProperty("pic") val pic: String? = null
)

data class IqiyiDetailResponse(@JsonProperty("data") val data: IqiyiDetailData? = null)
data class IqiyiDetailData(
    @JsonProperty("qipuIdStr") val qipuIdStr: String? = null,
    @JsonProperty("albumName") val albumName: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("albumDesc") val albumDesc: String? = null,
    @JsonProperty("posterPic") val posterPic: String? = null,
    @JsonProperty("order") val order: Int? = null
)

data class IqiyiPlayerResponse(@JsonProperty("data") val data: IqiyiPlayerData? = null)
data class IqiyiPlayerData(@JsonProperty("d") val streamList: List<IqiyiCdnStream>? = null)
data class IqiyiCdnStream(
    @JsonProperty("URL") val secureUrl: String? = null,
    @JsonProperty("sp") val cdnProvider: String? = null,
    @JsonProperty("bid") val bitrateId: Int? = null
)
