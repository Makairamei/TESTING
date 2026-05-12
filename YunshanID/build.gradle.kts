import com.lagradost.cloudstream3.gradle.CloudstreamExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    // Pastikan ini merujuk ke lokasi class Plugin kamu
    mainClass = "com.betbet.yunshanid.YunshanIDPlugin" 
    
    name = "YunshanID"
    description = "Donghua & Anime provider dari YunshanID"
    authors = listOf("Betbet")
    language = "id"
    
    status = 1

    tvTypes = listOf(
        "Anime",
        "TvSeries"
    )

    iconUrl = "https://yunshanid.site/favicon.ico"
}
