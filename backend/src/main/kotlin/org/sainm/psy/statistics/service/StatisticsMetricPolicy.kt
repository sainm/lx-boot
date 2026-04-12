package org.sainm.psy.statistics.service

import org.sainm.psy.statistics.domain.KeyValueCount
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class StatisticsMetricPolicy {

    fun completionRate(totalCount: Long, completedCount: Long): BigDecimal =
        if (totalCount <= 0) {
            BigDecimal.ZERO.setScale(2)
        } else {
            BigDecimal.valueOf(completedCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
        }

    fun scoreGapToAverage(totalScore: BigDecimal, averageScore: BigDecimal?): BigDecimal? =
        averageScore?.let { totalScore.subtract(it).setScale(4, RoundingMode.HALF_UP) }

    fun riskDistribution(vararg counts: Pair<String, Long>): List<KeyValueCount> =
        counts.map { (key, value) -> KeyValueCount(key, value) }
            .filter { it.value > 0 }
}
