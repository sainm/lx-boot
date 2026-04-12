package org.sainm.psy.statistics.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StatisticsMetricPolicyTest {

    private val policy = StatisticsMetricPolicy()

    @Test
    fun `completionRate returns percentage with two decimals`() {
        assertEquals(BigDecimal("80.00"), policy.completionRate(totalCount = 50, completedCount = 40))
    }

    @Test
    fun `completionRate returns zero when total is zero`() {
        assertEquals(BigDecimal("0.00"), policy.completionRate(totalCount = 0, completedCount = 40))
    }

    @Test
    fun `scoreGapToAverage returns four-decimal gap`() {
        assertEquals(BigDecimal("4.0000"), policy.scoreGapToAverage(BigDecimal("16"), BigDecimal("12")))
    }

    @Test
    fun `scoreGapToAverage returns null when average is null`() {
        assertNull(policy.scoreGapToAverage(BigDecimal("16"), null))
    }

    @Test
    fun `riskDistribution filters zero counts`() {
        val result = policy.riskDistribution("NORMAL" to 8L, "ATTENTION" to 0L, "HIGH" to 2L)

        assertEquals(2, result.size)
        assertEquals("NORMAL", result[0].key)
        assertEquals("HIGH", result[1].key)
    }
}
