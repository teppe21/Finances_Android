package com.teppe21.finances.data.fx

import com.teppe21.finances.core.net.HttpClient
import com.teppe21.finances.core.net.UrlConnectionHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A set of EUR-based rates with the date the provider says they apply to. */
data class FetchedRates(
    val rates: Map<String, BigDecimal>,
    val asOf: Instant,
    val provider: String
)

interface ExchangeRateProvider {
    val name: String

    /**
     * Fetches current rates relative to EUR for [currencies]. The request contains only
     * currency codes; no transaction or account data is ever sent.
     */
    suspend fun getLatestRates(currencies: Set<String>): Result<FetchedRates>
}

/** ECB reference rates via the Frankfurter API (no API key). Published on ECB working days. */
class FrankfurterRateProvider(
    private val http: HttpClient = UrlConnectionHttpClient()
) : ExchangeRateProvider {
    override val name: String = "European Central Bank via Frankfurter"

    override suspend fun getLatestRates(currencies: Set<String>): Result<FetchedRates> = withContext(Dispatchers.IO) {
        runCatching {
            val symbols = currencies.filter { it != "EUR" }.sorted().joinToString(",")
            val response = http.get("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=$symbols", emptyMap())
            check(response.code in 200..299) { "HTTP ${response.code}" }
            parseFrankfurter(response.body, name)
        }
    }

    companion object {
        fun parseFrankfurter(body: String, provider: String): FetchedRates {
            val json = JSONObject(body)
            val date = LocalDate.parse(json.getString("date"))
            val rates = parseRates(json.getJSONObject("rates"))
            require(rates.isNotEmpty()) { "No rates in response" }
            return FetchedRates(rates, date.atStartOfDay().toInstant(ZoneOffset.UTC), provider)
        }
    }
}

/** Fallback: open.er-api.com (no API key), used only when Frankfurter is unreachable. */
class OpenErApiRateProvider(
    private val http: HttpClient = UrlConnectionHttpClient()
) : ExchangeRateProvider {
    override val name: String = "ExchangeRate-API (open.er-api.com)"

    override suspend fun getLatestRates(currencies: Set<String>): Result<FetchedRates> = withContext(Dispatchers.IO) {
        runCatching {
            val response = http.get("https://open.er-api.com/v6/latest/EUR", emptyMap())
            check(response.code in 200..299) { "HTTP ${response.code}" }
            parseOpenEr(response.body, name).let { fetched ->
                fetched.copy(rates = fetched.rates.filterKeys { it in currencies })
            }
        }
    }

    companion object {
        fun parseOpenEr(body: String, provider: String): FetchedRates {
            val json = JSONObject(body)
            require(json.optString("result") == "success") { "Provider returned an error" }
            val updated = json.optLong("time_last_update_unix", -1L)
            require(updated > 0) { "Missing update time" }
            val rates = parseRates(json.getJSONObject("rates"))
            require(rates.isNotEmpty()) { "No rates in response" }
            return FetchedRates(rates, Instant.ofEpochSecond(updated), provider)
        }
    }
}

private fun parseRates(obj: JSONObject): Map<String, BigDecimal> {
    val result = mutableMapOf<String, BigDecimal>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.opt(key)?.toString()?.toBigDecimalOrNull()
        if (value != null && value.signum() > 0) result[key.uppercase()] = value
    }
    return result
}

class CompositeRateProvider(
    private val primary: ExchangeRateProvider = FrankfurterRateProvider(),
    private val secondary: ExchangeRateProvider = OpenErApiRateProvider()
) : ExchangeRateProvider {
    override val name: String = primary.name

    override suspend fun getLatestRates(currencies: Set<String>): Result<FetchedRates> {
        val first = primary.getLatestRates(currencies)
        return if (first.isSuccess) first else secondary.getLatestRates(currencies)
    }
}
