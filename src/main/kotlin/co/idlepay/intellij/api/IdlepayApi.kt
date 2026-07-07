package co.idlepay.intellij.api

import co.idlepay.intellij.IdlepayConstants
import co.idlepay.intellij.model.Ad
import co.idlepay.intellij.model.AdStyle
import co.idlepay.intellij.model.DeveloperEarnings
import co.idlepay.intellij.model.DeveloperProfile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Thin HTTP client over the idlepay backend. Mirrors src/api.ts: same endpoints, headers,
 * timeouts, and the SSRF origin guard (a resolved URL whose origin differs from the configured
 * one is refused).
 *
 * All calls run off the EDT (callers use background coroutines). Read paths return null on any
 * error; the credited beacon is fire-and-forget and never throws.
 */
object IdlepayApi {
    private val log = logger<IdlepayApi>()

    @Volatile
    var origin: String = IdlepayConstants.DEFAULT_ORIGIN
        set(value) {
            field = value.trimEnd('/')
        }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(IdlepayConstants.REQUEST_TIMEOUT_MS))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /** Build a URL under the configured origin, refusing any resolved origin that differs. */
    private fun endpoint(path: String): URI {
        val base = URI.create(origin)
        val resolved = base.resolve(path)
        val sameOrigin = resolved.scheme == base.scheme &&
            resolved.host == base.host &&
            resolved.port == base.port
        require(sameOrigin) { "blocked outbound request to ${resolved.scheme}://${resolved.authority}" }
        return resolved
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    private fun getString(uri: URI, timeoutMs: Long, headers: Map<String, String>): String? {
        return try {
            val builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
            headers.forEach { (k, v) -> builder.header(k, v) }
            val res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (res.statusCode() !in 200..299) {
                log.debug("GET $uri -> ${res.statusCode()}")
                null
            } else {
                res.body()
            }
        } catch (t: Throwable) {
            log.debug("GET $uri failed: ${t.message}")
            null
        }
    }

    // --- ads ------------------------------------------------------------------

    /** Anonymous display-only ad for the status bar (records no impression). */
    fun fetchDisplayAd(): Ad? {
        val body = getString(
            endpoint("/ad"),
            IdlepayConstants.REQUEST_TIMEOUT_MS,
            mapOf("accept" to "application/json"),
        ) ?: return null
        return parseAd(body)
    }

    /**
     * Credited impression beacon. Sent only when the caller has already verified real Claude
     * activity (see ClaudeActivity). Fire-and-forget: any failure is swallowed.
     */
    fun pingImpression(developerId: String, token: String) {
        try {
            getString(
                endpoint("/ad/${enc(developerId)}"),
                IdlepayConstants.REQUEST_TIMEOUT_MS,
                mapOf(
                    "accept" to "application/json",
                    "x-idlepay-token" to token,
                    "x-idlepay-active" to "1",
                    "x-idlepay-surface" to IdlepayConstants.SURFACE,
                ),
            )
        } catch (t: Throwable) {
            log.debug("pingImpression failed: ${t.message}")
        }
    }

    // --- developer ------------------------------------------------------------

    fun fetchEarnings(developerId: String): DeveloperEarnings? {
        val body = getString(
            endpoint("/developer/${enc(developerId)}/earnings"),
            IdlepayConstants.REQUEST_TIMEOUT_MS,
            mapOf("accept" to "application/json"),
        ) ?: return null
        return try {
            val o = JsonParser.parseString(body).asJsonObject
            DeveloperEarnings(
                developerId = o.str("developerId") ?: developerId,
                todayMicroUsd = o.long("todayMicroUsd"),
                monthMicroUsd = o.long("monthMicroUsd"),
                lifetimeMicroUsd = o.long("lifetimeMicroUsd"),
                impressionCount = o.long("impressionCount"),
            )
        } catch (t: Throwable) {
            null
        }
    }

    fun fetchProfile(developerId: String): DeveloperProfile? {
        val body = getString(
            endpoint("/developer/${enc(developerId)}/profile"),
            IdlepayConstants.REQUEST_TIMEOUT_MS,
            mapOf("accept" to "application/json"),
        ) ?: return null
        return try {
            val o = JsonParser.parseString(body).asJsonObject
            DeveloperProfile(
                developerId = o.str("developerId") ?: developerId,
                connected = o.get("connected")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                login = o.str("login"),
            )
        } catch (t: Throwable) {
            null
        }
    }

    // --- clicks ---------------------------------------------------------------

    /**
     * Tracked click URL for an ad, routing through /r/{campaignId} so the click can be counted.
     * Falls back to the raw landing URL for the fallback ad. Returns null if nothing to open.
     */
    fun clickThroughUrl(ad: Ad, developerId: String?): String? {
        val cid = ad.campaignId
        if (cid.isNotEmpty() && cid != "fallback") {
            val params = StringBuilder()
            if (!developerId.isNullOrEmpty()) params.append("d=").append(enc(developerId))
            params.append(if (params.isEmpty()) "s=" else "&s=").append(enc(IdlepayConstants.SURFACE))
            ad.variantId?.let { params.append("&v=").append(enc(it)) }
            return try {
                endpoint("/r/${enc(cid)}?$params").toString()
            } catch (t: Throwable) {
                ad.url
            }
        }
        return ad.url
    }

    /** Best-effort click report (never bills/credits). */
    fun postClick(campaignId: String, developerId: String?) {
        try {
            val payload = JsonObject().apply {
                addProperty("campaign_id", campaignId)
                if (!developerId.isNullOrEmpty()) addProperty("developer_id", developerId)
            }
            val req = HttpRequest.newBuilder(endpoint("/click"))
                .timeout(Duration.ofMillis(IdlepayConstants.REQUEST_TIMEOUT_MS))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build()
            http.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (t: Throwable) {
            log.debug("postClick failed: ${t.message}")
        }
    }

    // --- parsing --------------------------------------------------------------

    /** isAd guard from upstream: object with string id and string text. */
    private fun parseAd(body: String): Ad? {
        return try {
            val o = JsonParser.parseString(body).asJsonObject
            val id = o.str("id") ?: return null
            val text = o.str("text") ?: return null
            val style = o.get("style")?.takeIf { it.isJsonObject }?.asJsonObject?.let { s ->
                AdStyle(
                    textColorHex = s.str("textColorHex"),
                    badgeColorHex = s.str("badgeColorHex"),
                    bold = s.get("bold")?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                )
            }
            Ad(
                id = id,
                campaignId = o.str("campaignId") ?: "",
                variantId = o.str("variantId"),
                text = text,
                url = o.str("url"),
                cpmMicroUsd = o.long("cpmMicroUsd"),
                style = style,
                logoUrl = o.str("logoUrl"),
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.long(key: String): Long =
        get(key)?.takeIf { !it.isJsonNull }?.asLong ?: 0L
}
