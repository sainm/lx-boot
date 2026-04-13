package org.sainm.psy.common.scheduler

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class SchedulerLockService(
    private val redisTemplateProvider: ObjectProvider<StringRedisTemplate>,
    @Value("\${psy.scheduler.lock.enabled:true}")
    private val enabled: Boolean = true,
    @Value("\${psy.scheduler.lock.key-prefix:psy:scheduler:lock}")
    private val keyPrefix: String = "psy:scheduler:lock"
) {

    fun <T> withLock(lockName: String, lockTtl: Duration, block: () -> T): T? {
        if (!enabled) {
            return block()
        }
        val redisTemplate = redisTemplateProvider.getIfAvailable() ?: return block()
        val key = "${keyPrefix.trimEnd(':')}:$lockName"
        val token = UUID.randomUUID().toString()
        val locked = runCatching {
            redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl) == true
        }.getOrElse {
            // Redis should improve multi-instance behavior, not block local work if unavailable.
            return block()
        }
        if (!locked) {
            return null
        }
        return try {
            block()
        } finally {
            runCatching {
                if (redisTemplate.opsForValue().get(key) == token) {
                    redisTemplate.delete(key)
                }
            }
        }
    }
}
