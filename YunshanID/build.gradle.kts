import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.gradle.api.Project

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

version = 1

cloudstream {
    mainClass = "com.betbet.yunshanid.YunshanIDPlugin" 
    
    name = "YunshanID"
    [span_1](start_span)description = "Donghua & Anime provider dari YunshanID"[span_1](end_span)
    [span_2](start_span)authors = listOf("Betbet")[span_2](end_span)
    [span_3](start_span)language = "id"[span_3](end_span)
    
    [span_4](start_span)status = 1[span_4](end_span)

    tvTypes = listOf(
        "Anime",
        "TvSeries"
    [span_5](start_span))

    iconUrl = "https://yunshanid.site/favicon.ico"[span_5](end_span)
}
