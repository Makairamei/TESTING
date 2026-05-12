package com.Melolo

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element
import java.util.Base64
import java.net.URI
import kotlin.random.Random

/**
 * 🛠️ UTILS FOR BASE PROVIDER - ALIGNED WITH V2.2.0 (STABLE)
 */

object SmartThrottle {
    private val lastRequestMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val MIN_DELAY = 500L

    suspend fun wait(domain: String) {
        val now = System.currentTimeMillis()
        val lastRequest = lastRequestMap[domain] ?: 0L
        val diff = now - lastRequest
        if (diff < MIN_DELAY) {
            delay(MIN_DELAY - diff + Random.nextLong(100L))
        }
        lastRequestMap[domain] = System.currentTimeMillis()
    }
}

class ExpiringCache<T>(private val durationMs: Long) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, T>>()

    fun get(key: String): T? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.first > durationMs) {
            cache.remove(key)
            return null
        }
        return entry.second
    }

    fun put(key: String, value: T) {
        cache[key] = System.currentTimeMillis() to value
    }
}

val globalHtmlCache = ExpiringCache<org.jsoup.nodes.Document>(5 * 60 * 1000L)

suspend fun rateLimitDelay(url: String = "") {
    if (url.isBlank()) {
        try { delay(100L + Random.nextLong(200L)) } catch (_: Exception) {}
    } else {
        runCatching { SmartThrottle.wait(URI(url).host ?: "default") }
    }
}

suspend fun <T> executeWithRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try { return block() } catch (e: Exception) {
            lastException = e
            if (attempt < maxRetries - 1) delay(initialDelay * (attempt + 1))
        }
    }
    throw lastException ?: Exception("Max retries reached")
}

fun String.safeCleanBloat(original: String, regex: Regex): String {
    return try { val cleaned = regex.replace(this, "").trim(); cleaned.ifBlank { original } } catch (_: Exception) { original }
}

// safeDeduplicate removed to maintain behavior parity with v2.2.0

fun String?.safeExtractYear(): Int? {
    if (this == null) return null
    return try { Regex("\\d{4}").find(this)?.value?.toIntOrNull() } catch (_: Exception) { null }
}

fun String?.safeExtractEpNum(): Int? {
    if (this == null || this.isBlank()) return null
    return try {
        val keywordMatch = Regex("""(?i)(?:episode|ep|eps)\s*(\d+(?:\.\d+)?)""").find(this)
        if (keywordMatch != null) return keywordMatch.groupValues[1].toDoubleOrNull()?.toInt()
        Regex("""(\d+(?:\.\d+)?)""").find(this)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
    } catch (_: Exception) { null }
}

fun String.safeHttpsify(): String { return try { if (startsWith("//")) "https:$this" else this } catch (_: Exception) { this } }

fun fixUrlSmart(url: String?, baseUrl: String? = null): String {
    if (url.isNullOrBlank()) return ""
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"
    val base = baseUrl ?: ""; if (base.isBlank()) return url
    return try {
        val uri = URI(base); val root = "${uri.scheme}://${uri.host}"
        if (url.startsWith("/")) "$root$url" else { val path = if (base.endsWith("/")) base else "$base/"; "$path$url" }
    } catch (_: Exception) { url }
}

fun getBaseUrl(url: String?): String {
    if (url.isNullOrEmpty()) return ""
    return try { val uri = URI(url); "${uri.scheme}://${uri.host}" } catch (_: Exception) { "" }
}

fun String?.safeIsBase64(): Boolean {
    if (this.isNullOrBlank() || this.length < 8 || this.length % 4 != 0) return false
    return this.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))
}

fun String.safeDecode(): String { return try { String(Base64.getDecoder().decode(this)) } catch (_: Exception) { this } }

fun Element.safeExtractImage(attributes: List<String>): String {
    return try {
        attributes.asSequence()
            .map { if (it.contains(":::")) it.substringAfter(":::") else it }
            .map { absUrl(it) }.filter { it.isNotBlank() }.firstOrNull() ?: absUrl("src")
    } catch (_: Exception) { "" }
}

// ============================================
// REGION: CENTRALIZED ERROR REPORTER
// ============================================

object ErrorReporter {
    private const val BOT_TOKEN = "8989495909:AAF8o8MhVa2o0T3X21N0bC3pJnMMqnvL628"
    private const val CHAT_ID = "832658254"
    private val lastErrorMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 60000L // 1 Minute per unique error

    fun extractErrorInfo(throwable: Throwable): Map<String, String> {
        val stack = throwable.stackTrace.firstOrNull { 
            it.className.contains("com.Melolo") || it.className.contains("com.Anichin") || it.className.contains("com.Idlix") 
        } ?: throwable.stackTrace.firstOrNull()
        
        return mapOf(
            "file" to (stack?.fileName ?: "Unknown"),
            "line" to (stack?.lineNumber?.toString() ?: "0"),
            "function" to (stack?.methodName ?: "Unknown"),
            "exception" to throwable.javaClass.simpleName,
            "message" to (throwable.message ?: "No message"),
            "stacktrace" to throwable.stackTrace.take(5).joinToString("\n") { "at ${it.fileName}:${it.lineNumber}" }
        )
    }

    suspend fun report(provider: String, throwable: Throwable) {
        runCatching {
            val info = extractErrorInfo(throwable)
            val errorKey = "$provider:${info["file"]}:${info["line"]}:${info["exception"]}"
            
            val now = System.currentTimeMillis()
            if (now - (lastErrorMap[errorKey] ?: 0L) < COOLDOWN_MS) return
            lastErrorMap[errorKey] = now

            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val message = """
                🚨 *OCE Runtime Error*

                📦 *Provider:*
                $provider

                📄 *File:*
                ${info["file"]}

                📍 *Line:*
                ${info["line"]}

                ⚙️ *Function:*
                ${info["function"]}

                ❌ *Message:*
                ${info["message"]}

                🧠 *Exception:*
                ${info["exception"]}

                🕒 *Time:*
                $timestamp

                📚 *Stacktrace:*
                ${info["stacktrace"]}
            """.trimIndent()

            app.post(
                "https://api.telegram.org/bot$BOT_TOKEN/sendMessage",
                params = mapOf("chat_id" to CHAT_ID, "text" to message, "parse_mode" to "Markdown")
            )
        }
    }
}

fun safeReportError(provider: String, throwable: Throwable) {
    // Non-blocking report
    val scope = kotlinx.coroutines.MainScope()
    scope.kotlinx.coroutines.launch {
        ErrorReporter.report(provider, throwable)
    }
}

suspend fun <T> safeApiCall(provider: String, block: suspend () -> T): T? {
    return runCatching { block() }.onFailure { 
        logError(provider, "API Call Failed", it)
        safeReportError(provider, it)
    }.getOrNull()
}

fun logDebug(tag: String, message: String) = Log.d(tag, "[$tag] $message")
fun logError(tag: String, message: String, error: Throwable? = null) {
    Log.e(tag, "[$tag] ERROR: $message ${error?.message ?: ""}")
}

fun String.unpackPacked(): String {
    return try {
        if (!this.contains("p,a,c,k,e,d")) return this
        val p = Regex("""\}\('(.*)',\s*(.*),\s*(\d+),\s*'(.*)'\.split\('\|'\)""").find(this) ?: return this
        val (payload, base, count, map) = p.destructured
        val mapList = map.split("|")
        fun getString(n: Int, b: Int): String {
            val res = if (n < b) "" else getString(n / b, b)
            val rem = n % b
            return res + (if (rem > 35) (rem + 29).toChar() else rem.toString(36))
        }
        var result = payload
        mapList.forEachIndexed { i, s -> if (s.isNotBlank()) result = result.replace(Regex("\\b${getString(i, base.toInt())}\\b"), s) }
        result
    } catch (_: Exception) { this }
}
