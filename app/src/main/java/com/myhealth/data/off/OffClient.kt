package com.myhealth.data.off

import com.myhealth.domain.repository.SettingsRepository
import com.myhealth.domain.util.AppError
import com.myhealth.domain.util.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Open Food Facts product lookup (PLAN P4.10).
 *
 * Contract, verbatim from §5/P4.10: one `GET` against the v2 product endpoint with an explicit
 * `fields` list, the **required** `User-Agent: MyHealth/0.1 (personal app; <contact>)` (the
 * contact is a setting, so it is editable without a rebuild), 10 s timeouts, exactly one retry on
 * an `IOException`, and a client-side token bucket of 15 requests per minute — OFF's documented
 * limit for product reads. A `status != 1` body (OFF answers "unknown product" with HTTP 200) maps
 * to `Outcome.Err(AppError.Network(404, null))`, which the UI shows as "Product not found".
 */
class OffClient(
    private val settings: SettingsRepository,
    private val throttle: OffThrottle = OffThrottle(),
    private val client: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = BASE_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Looks [barcode] up; see the class KDoc for the error mapping. */
    suspend fun fetch(barcode: String): Outcome<OffProduct> = withContext(io) {
        val code = barcode.trim()
        if (code.isBlank()) {
            return@withContext Outcome.Err(AppError.Validation("barcode", "Enter a barcode first."))
        }
        if (!throttle.tryAcquire()) {
            return@withContext Outcome.Err(AppError.Network(THROTTLED, null))
        }
        val request = Request.Builder()
            .url("$baseUrl/api/v2/product/$code.json?fields=$FIELDS")
            .header("User-Agent", userAgent(contact()))
            .get()
            .build()
        when (val body = execute(request)) {
            is Outcome.Err -> body
            is Outcome.Ok -> parse(body.value, code)
        }
    }

    private suspend fun contact(): String =
        settings.settings.first().offUserAgentContact.trim().ifBlank { NO_CONTACT }

    /** One call, retried once on an `IOException` (a flaky mobile connection, not a 4xx/5xx). */
    private fun execute(request: Request): Outcome<String> {
        var last: IOException? = null
        repeat(ATTEMPTS) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return Outcome.Err(AppError.Network(response.code, null))
                    }
                    return Outcome.Ok(response.body.string())
                }
            } catch (e: IOException) {
                last = e
            }
        }
        return Outcome.Err(AppError.Network(null, last))
    }

    private fun parse(body: String, barcode: String): Outcome<OffProduct> {
        val decoded = try {
            OFF_JSON.decodeFromString<OffResponse>(body)
        } catch (e: SerializationException) {
            return Outcome.Err(AppError.Parse("off-product", e.message ?: "Unreadable response"))
        }
        val product = OffMapper.toProduct(decoded, barcode)
            ?: return Outcome.Err(AppError.Network(NOT_FOUND, null))
        return Outcome.Ok(product)
    }

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val FIELDS = "product_name,brands,quantity,serving_size,image_url,nutriments"

        /** `AppError.Network` codes this client produces on top of real HTTP status codes. */
        const val NOT_FOUND = 404
        const val THROTTLED = 429

        private const val NO_CONTACT = "no-contact"
        private const val ATTEMPTS = 2
        private const val TIMEOUT_SECONDS = 10L

        /** The exact header OFF requires of every API client. */
        fun userAgent(contact: String): String = "MyHealth/0.1 (personal app; $contact)"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Token bucket limiting OFF product reads to [permitsPerMinute] (P4.10). Refills continuously, so
 * 15 requests in a burst are allowed and the 16th has to wait 4 s for the next token. The [clock]
 * is injected so `OffThrottleTest` can advance time instead of sleeping.
 */
class OffThrottle(
    private val clock: Clock = Clock.systemUTC(),
    private val permitsPerMinute: Int = 15,
) {

    private var tokens: Double = permitsPerMinute.toDouble()
    private var refilledAtMillis: Long = clock.millis()

    /** `true` when a request may go out now, consuming one token. */
    @Synchronized
    fun tryAcquire(): Boolean {
        refill()
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }

    /** Tokens currently available (for diagnostics and tests). */
    @Synchronized
    fun available(): Double {
        refill()
        return tokens
    }

    private fun refill() {
        val now = clock.millis()
        val elapsed = now - refilledAtMillis
        if (elapsed <= 0L) return
        val gained = elapsed * permitsPerMinute / 60_000.0
        tokens = (tokens + gained).coerceAtMost(permitsPerMinute.toDouble())
        refilledAtMillis = now
    }
}
