package org.sainm.psy.auth

import org.sainm.auth.core.spi.EmailVerificationClaim
import org.sainm.auth.core.spi.EmailVerificationService
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Persistent, single-use email verification tokens suitable for restarts and multiple instances. */
@Service
class JdbcEmailVerificationService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : EmailVerificationService {

    @Transactional
    override fun generate(userId: Long, email: String, ttlSeconds: Long): String {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        jdbcTemplate.update(
            "delete from psy_email_verification_token where expires_at < :now or user_id = :userId",
            mapOf("now" to Timestamp.valueOf(now), "userId" to userId)
        )
        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        jdbcTemplate.update(
            """
            insert into psy_email_verification_token(token_hash, user_id, email, expires_at, created_at)
            values (:tokenHash, :userId, :email, :expiresAt, :createdAt)
            """.trimIndent(),
            mapOf(
                "tokenHash" to hash(token),
                "userId" to userId,
                "email" to email,
                "expiresAt" to Timestamp.valueOf(now.plusSeconds(ttlSeconds)),
                "createdAt" to Timestamp.valueOf(now)
            )
        )
        return token
    }

    @Transactional
    override fun consume(token: String): EmailVerificationClaim? {
        val tokenHash = hash(token)
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val claim = jdbcTemplate.query(
            """
            delete from psy_email_verification_token
            where token_hash = :tokenHash and expires_at >= :now
            returning user_id, email, created_at
            """.trimIndent(),
            mapOf("tokenHash" to tokenHash, "now" to Timestamp.valueOf(now))
        ) { rs, _ ->
            EmailVerificationClaim(
                userId = rs.getLong("user_id"),
                email = rs.getString("email"),
                createdAtEpochSecond = rs.getTimestamp("created_at").toInstant().epochSecond
            )
        }.firstOrNull()
        jdbcTemplate.update(
            "delete from psy_email_verification_token where expires_at < :now",
            mapOf("now" to Timestamp.valueOf(now))
        )
        return claim
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
