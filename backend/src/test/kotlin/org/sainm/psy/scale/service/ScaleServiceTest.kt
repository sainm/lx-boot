package org.sainm.psy.scale.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.scale.api.BatchCreateScaleDimensionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleResultRulesRequest
import org.sainm.psy.scale.api.CreateScaleDimensionRequest
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.CreateScaleResultRuleRequest
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.repository.ScaleRepository
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScaleServiceTest {

    @Mock
    private lateinit var scaleRepository: ScaleRepository

    @Mock
    private lateinit var currentUserFacade: CurrentUserFacade

    @InjectMocks
    private lateinit var scaleService: ScaleService

    // ── findPage ────────────────────────────────────────────────────────────

    @Test
    fun `findPage throws when page is zero`() {
        val ex = assertThrows<IllegalArgumentException> {
            scaleService.findPage(ScaleListQuery(page = 0, size = 20))
        }
        assertTrue(ex.message!!.contains("page"))
    }

    @Test
    fun `findPage throws when size is zero`() {
        assertThrows<IllegalArgumentException> {
            scaleService.findPage(ScaleListQuery(page = 1, size = 0))
        }
    }

    @Test
    fun `findPage throws when size exceeds 200`() {
        assertThrows<IllegalArgumentException> {
            scaleService.findPage(ScaleListQuery(page = 1, size = 201))
        }
    }

    @Test
    fun `findPage returns page response from repository`() {
        val summaries = listOf(ScaleSummary(1L, "PHQ9", "PHQ-9", null, "v1", "PUBLISHED", false, LocalDateTime.now()))
        `when`(scaleRepository.findPage(ScaleListQuery(page = 1, size = 20))).thenReturn(Pair(summaries, 1L))

        val result = scaleService.findPage(ScaleListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
        assertEquals(1, result.page)
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    fun `create throws BizException when scale code already exists`() {
        val request = CreateScaleRequest(scaleCode = "PHQ9", scaleName = "PHQ-9")
        `when`(scaleRepository.existsByScaleCode("PHQ9")).thenReturn(true)

        val ex = assertThrows<BizException> {
            scaleService.create(request)
        }
        assertEquals("SCALE_CODE_EXISTS", ex.code)
    }

    @Test
    fun `create succeeds and returns DRAFT status`() {
        val request = CreateScaleRequest(scaleCode = "PHQ9", scaleName = "PHQ-9")
        `when`(scaleRepository.existsByScaleCode("PHQ9")).thenReturn(false)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)
        `when`(scaleRepository.create(request, 1L)).thenReturn(42L)

        val result = scaleService.create(request)

        assertEquals(42L, result.id)
        assertEquals("DRAFT", result.status)
    }

    // ── findDetail ──────────────────────────────────────────────────────────

    @Test
    fun `findDetail throws BizException when scale not found`() {
        `when`(scaleRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            scaleService.findDetail(99L)
        }
        assertEquals("SCALE_NOT_FOUND", ex.code)
    }

    // ── batchCreateDimensions ────────────────────────────────────────────────

    @Test
    fun `batchCreateDimensions throws when scale does not exist`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(false)

        val request = BatchCreateScaleDimensionsRequest(
            dimensions = listOf(dim("D1", "Dimension 1"))
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateDimensions(1L, request)
        }
        assertEquals("SCALE_NOT_FOUND", ex.code)
    }

    @Test
    fun `batchCreateDimensions throws on duplicate dimension codes`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)

        val request = BatchCreateScaleDimensionsRequest(
            dimensions = listOf(dim("D1", "Dim 1"), dim("D1", "Dim 1 copy"))
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateDimensions(1L, request)
        }
        assertEquals("DIMENSION_CODE_DUPLICATED", ex.code)
        assertTrue(ex.message.contains("D1"))
    }

    // ── batchCreateResultRules ───────────────────────────────────────────────

    @Test
    fun `batchCreateResultRules throws when scoreMin is greater than scoreMax`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(emptyList())

        val request = BatchCreateScaleResultRulesRequest(
            resultRules = listOf(
                resultRule(scoreMin = BigDecimal("80"), scoreMax = BigDecimal("20"))
            )
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateResultRules(1L, request)
        }
        assertEquals("RESULT_RULE_RANGE_INVALID", ex.code)
    }

    @Test
    fun `batchCreateResultRules throws when dimension does not belong to scale`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(listOf(10L))

        val request = BatchCreateScaleResultRulesRequest(
            resultRules = listOf(
                resultRule(dimensionId = 999L, scoreMin = BigDecimal("0"), scoreMax = BigDecimal("50"))
            )
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateResultRules(1L, request)
        }
        assertEquals("DIMENSION_NOT_FOUND", ex.code)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun dim(code: String, name: String) = CreateScaleDimensionRequest(
        dimensionCode = code,
        dimensionName = name
    )

    private fun resultRule(
        dimensionId: Long? = null,
        scoreMin: BigDecimal = BigDecimal.ZERO,
        scoreMax: BigDecimal = BigDecimal("100"),
        riskLevel: String = "LOW"
    ) = CreateScaleResultRuleRequest(
        dimensionId = dimensionId,
        riskLevel = riskLevel,
        scoreMin = scoreMin,
        scoreMax = scoreMax,
        resultTitle = "Low risk",
        resultDescription = null,
        suggestionText = null
    )
}
