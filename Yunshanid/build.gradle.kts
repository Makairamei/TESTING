import com.lagradost.cloudstream3.gradle.CloudstreamExtension

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "com.github.recloudstream")

configure<CloudstreamExtension> {
    // Gunakan fungsi set agar tidak bentrok dengan properti bawaan Gradle
    setPluginId("Yunshanid")
    setPluginName("Yunshanid")
    setPluginClass("com.Yunshanid.YunshanidPlugin")
    setPluginDescription("Dibuat oleh BetbetMiro untuk Yunshanid")
    authors = listOf("BetbetMiro")
}

dependencies {
    val cloudstreamVersion = "latest-SNAPSHOT"
    compileOnly("com.github.recloudstream:cloudstream3:$cloudstreamVersion")
}
