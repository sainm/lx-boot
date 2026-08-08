package org.sainm.psy.common.scheduler

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class SchedulerLockService(
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    @Value("\${psy.scheduler.lock.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${psy.scheduler.lock.fail-open:false}")
    private val failOpen: Boolean = false,
    @Value("\${psy.scheduler.lock.key-prefix:psy:scheduler:lock}")
    private val keyPrefix: String = "psy:scheduler:lock"
) {

    fun <T> withLock(lockName: String, lockTtl: Duration, block: () -> T): T? {
        if (!enabled) {
            return block()
        }
        val redisTemplate = redisTemplateProvider.getIfAvailable() ?: return if (failOpen) block() else null
        val key = "${keyPrefix.trimEnd(':')}:$lockName"
        val token = UUID.randomUUID().toString()
        val locked = runCatching {
            redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl) == true
        }.getOrElse {
            return if (failOpen) block() else null
        }
        if (!locked) {
            return null
        }
        return try {
            block()
        } finally {
            runCatching {
                redisTemplate.execute(RELEASE_LOCK_SCRIPT, listOf(key), token)
            }
        }
    }

    private companion object {
        val RELEASE_LOCK_SCRIPT = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """.trimIndent()
            )
            resultType = Long::class.javaObjectType
        }
    }
}
