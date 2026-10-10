package com.teppe21.finances.core.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class HttpResponse(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

sealed class HttpFailure(message: String) : IOException(message) {
    class Timeout : HttpFailure("Request timed out")
    class Offline(cause: Throwable) : HttpFailure("Network unavailable: ${cause.javaClass.simpleName}")
}

/** Minimal GET client so providers can be tested with canned responses. */
fun interface HttpClient {
    /** Performs a GET request. Throws [HttpFailure] for timeouts and connectivity problems. */
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/**
 * HttpURLConnection-based client. URLs and headers may contain API keys, so this class
 * never logs them and never includes them in exception messages.
 */
class UrlConnectionHttpClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 10_000,
    private val maxBodyBytes: Int = 2_000_000
) : HttpClient {

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw HttpFailure.Offline(e)
        }
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { input -> readLimited(input) }.orEmpty()
            val responseHeaders = conn.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.joinToString(",") }
            return HttpResponse(code, body, responseHeaders)
        } catch (e: SocketTimeoutException) {
            throw HttpFailure.Timeout()
        } catch (e: HttpFailure) {
            throw e
        } catch (e: IOException) {
            throw HttpFailure.Offline(e)
        } finally {
            conn.disconnect()
        }
    }

    // InputStream.readNBytes needs API 33; minSdk is 26.
    private fun readLimited(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < maxBodyBytes) {
            val n = input.read(buffer, 0, minOf(buffer.size, maxBodyBytes - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
