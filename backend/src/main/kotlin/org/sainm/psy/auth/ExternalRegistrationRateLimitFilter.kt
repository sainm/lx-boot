package org.sainm.psy.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ExternalRegistrationRateLimitFilter(
    private val objectMapper: ObjectMapper,
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    @Value("\${psy.external-registration.rate-limit.ip-max:20}") private val ipMax: Long,
    @Value("\${psy.external-registration.rate-limit.email-max:5}") private val emailMax: Long,
    @Value("\${psy.external-registration.rate-limit.window-seconds:600}") private val windowSeconds: Long,
    @Value("\${psy.external-registration.rate-limit.require-redis:false}") private val requireRedis: Boolean = false
) : OncePerRequestFilter() {

    private val localCounters = ConcurrentHashMap<String, LocalCounter>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI !in RATE_LIMITED_PATHS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val body = request.inputStream.readNBytes(MAX_BODY_BYTES + 1)
        if (body.size > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
            return
        }
        val wrapped = CachedBodyRequest(request, body)
        val email = parseEmail(body)
        val ipAllowed = incrementAndCheck("ip:${hash(request.remoteAddr.orEmpty())}", ipMax)
        val emailAllowed = email == null || incrementAndCheck("email:${hash(email)}", emailMax)
        if (!ipAllowed || !emailAllowed) {
            writeRateLimited(response, request.locale)
            return
        }
        filterChain.doFilter(wrapped, response)
    }

    private fun parseEmail(body: ByteArray): String? = runCatching {
        objectMapper.readTree(body).path("email").asText().trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun incrementAndCheck(keySuffix: String, limit: Long): Boolean {
        val key = "psy:external-registration:rate:$keySuffix"
        val redis = redisTemplateProvider.getIfAvailable()
        if (redis != null) {
            runCatching {
                val count = redis.execute(
                    REDIS_INCREMENT_SCRIPT,
                    listOf(key),
                    windowSeconds.toString()
                ) ?: Long.MAX_VALUE
                return count <= limit
            }
        }
        if (requireRedis) {
            return false
        }
        val now = Instant.now().epochSecond
        if (localCounters.size > MAX_LOCAL_KEYS) {
            localCounters.entries.removeIf { it.value.expiresAt <= now }
            if (localCounters.size > MAX_LOCAL_KEYS) {
                return false
            }
        }
        val count = localCounters.compute(key) { _, current ->
            if (current == null || current.expiresAt <= now) {
                LocalCounter(1, now + windowSeconds)
            } else {
                current.copy(count = current.count + 1)
            }
        }?.count ?: Long.MAX_VALUE
        return count <= limit
    }

    private fun writeRateLimited(response: HttpServletResponse, locale: Locale) {
        response.status = 429
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val message = when (locale.language.lowercase(Locale.ROOT)) {
            "zh" -> "请求过于频繁，请稍后再试。"
            "ja" -> "リクエストが多すぎます。しばらくしてから再試行してください。"
            else -> "Too many requests. Please try again later."
        }
        objectMapper.writeValue(response.writer, mapOf("code" to "RATE_LIMITED", "message" to message, "data" to null))
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class LocalCounter(val count: Long, val expiresAt: Long)

    private class CachedBodyRequest(
        request: HttpServletRequest,
        private val body: ByteArray
    ) : HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val input = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = input.read()
                override fun isFinished(): Boolean = input.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(readListener: ReadListener?) = Unit
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, characterEncoding ?: StandardCharsets.UTF_8.name()))
    }

    companion object {
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val MAX_LOCAL_KEYS = 20_000
        private val RATE_LIMITED_PATHS = setOf("/auth/external-register", "/auth/external-register/resend")
        private val REDIS_INCREMENT_SCRIPT = DefaultRedisScript<Long>().apply {
            resultType = Long::class.javaObjectType
            setScriptText(
                "local count = redis.call('INCR', KEYS[1]); " +
                    "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
                    "return count"
            )
        }
    }
}
