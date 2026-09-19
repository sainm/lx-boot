package org.sainm.psy.scale.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleDimension
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.visualization.domain.ScaleVisualizationConfig
import org.sainm.psy.visualization.service.VisualizationService
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * The content hash is the immutability anchor of a published scale: it is
 * stored at publication time and recomputed by every export, readiness check
 * and import verification. These tests pin the two properties that broke in
 * production-like data (visualization configs must be bound into the hash, and
 * the hash must not depend on collection order).
 */
@ExtendWith(MockitoExtension::class)
class ScaleContentFingerprintServiceTest {

    @Mock private lateinit var packageRepository: ScalePackageRepository
    @Mock private lateinit var visualizationService: VisualizationService

    private lateinit var service: ScaleContentFingerprintService

    @BeforeEach
    fun setUp() {
        service = ScaleContentFingerprintService(packageRepository, visualizationService)
    }

    @Test
    fun `content hash repeats and ignores collection order`() {
        whenever(visualizationService.findConfigs(SCALE_ID)).thenReturn(emptyList())
        whenever(packageRepository.canonicalValues(SCALE_ID)).thenReturn(listOf("""{"locale":"zh-CN"}"""))
        val ordered = scale(
            dimensions = listOf(dimension(11L, "D1", 1), dimension(12L, "D2", 2)),
            questions = listOf(question(21L, 1, 11L, 1), question(22L, 2, 12L, 2))
        )
        val shuffled = ordered.copy(
            dimensions = ordered.dimensions.reversed(),
            questions = ordered.questions.reversed()
        )

        val first = service.calculate(ordered)

        assertEquals(first, service.calculate(ordered), "repeated calculation must reproduce the hash")
        assertEquals(first, service.calculate(shuffled), "row order must not leak into the hash")
    }

    @Test
    fun `visualization configs participate in the content hash`() {
        whenever(packageRepository.canonicalValues(SCALE_ID)).thenReturn(emptyList())
        whenever(visualizationService.findConfigs(SCALE_ID)).thenReturn(emptyList())
        val withoutCharts = service.calculate(scale())

        whenever(visualizationService.findConfigs(SCALE_ID)).thenReturn(
            listOf(
                ScaleVisualizationConfig(
                    id = 31L,
                    scaleId = SCALE_ID,
                    chartType = "RADAR",
                    chartTitle = "雷达图",
                    viewScope = "REPORT_DETAIL",
                    dataSource = "DIMENSION_SCORE",
                    configJson = "{}",
                    enabled = true,
                    sortNo = 1
                )
            )
        )
        val withCharts = service.calculate(scale())

        assertNotEquals(withoutCharts, withCharts, "chart configuration must change the published hash")
    }

    @Test
    fun `canonical package rows participate in the content hash`() {
        whenever(visualizationService.findConfigs(SCALE_ID)).thenReturn(emptyList())
        whenever(packageRepository.canonicalValues(SCALE_ID)).thenReturn(listOf("""{"governanceStatus":"APPROVED"}"""))
        val approved = service.calculate(scale())

        whenever(packageRepository.canonicalValues(SCALE_ID)).thenReturn(listOf("""{"governanceStatus":"DRAFT"}"""))
        val draft = service.calculate(scale())

        assertNotEquals(approved, draft, "governance/translation rows must change the published hash")
    }

    @Test
    fun `numeric formatting does not change the content hash`() {
        whenever(visualizationService.findConfigs(SCALE_ID)).thenReturn(emptyList())
        whenever(packageRepository.canonicalValues(SCALE_ID)).thenReturn(emptyList())

        assertEquals(
            service.calculate(scale(coefficient = BigDecimal("1.0000"))),
            service.calculate(scale(coefficient = BigDecimal("1"))),
            "trailing zeros must not create a different hash for identical content"
        )
    }

    private fun dimension(id: Long, code: String, sortNo: Int) = ScaleDimension(
        id = id,
        scaleId = SCALE_ID,
        dimensionCode = code,
        dimensionName = "维度$code",
        description = null,
        sortNo = sortNo
    )

    private fun option(id: Long, questionId: Long, code: String, sortNo: Int) = ScaleQuestionOption(
        id = id,
        questionId = questionId,
        optionCode = code,
        optionLabel = "选项$code",
        scoreValue = BigDecimal.ZERO,
        exclusiveFlag = false,
        optionGroupCode = null,
        sortNo = sortNo
    )

    private fun question(id: Long, questionNo: Int, dimensionId: Long?, sortNo: Int) = ScaleQuestion(
        id = id,
        scaleId = SCALE_ID,
        dimensionId = dimensionId,
        questionNo = questionNo,
        questionTitle = "题目$questionNo",
        questionType = "SINGLE_CHOICE",
        requiredFlag = true,
        reverseScoreFlag = false,
        weightValue = BigDecimal.ONE,
        optionSelectionLimit = null,
        sliderMin = null,
        sliderMax = null,
        sliderStep = null,
        textInputEnabled = false,
        textInputPlaceholder = null,
        matrixGroupCode = null,
        rowCode = null,
        columnCode = null,
        sortNo = sortNo,
        options = listOf(option(id * 10, id, "A", 1), option(id * 10 + 1, id, "B", 2))
    )

    private fun scale(
        dimensions: List<ScaleDimension> = listOf(dimension(11L, "D1", 1)),
        questions: List<ScaleQuestion> = listOf(question(21L, 1, 11L, 1)),
        coefficient: BigDecimal = BigDecimal.ONE
    ) = ScaleDetail(
        id = SCALE_ID,
        scaleCode = "MT_FINGERPRINT_TEST",
        scaleName = "指纹测试量表",
        description = null,
        applicableTarget = null,
        versionNo = "v1",
        versionGroupId = SCALE_ID,
        currentVersionFlag = true,
        status = "DRAFT",
        scoreMethod = "SIMPLE_SUM",
        scoreCoefficient = coefficient,
        normStrategy = "RAW_SCORE",
        normDefaultGroup = null,
        highRiskWarningEnabled = false,
        anonymousSupported = false,
        reportTemplate = null,
        createdBy = 1L,
        createdAt = NOW,
        updatedBy = 1L,
        updatedAt = NOW,
        dimensions = dimensions,
        questions = questions,
        resultRules = emptyList(),
        norms = emptyList()
    )

    private companion object {
        const val SCALE_ID = 1L
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 19, 12, 0)
    }
}
