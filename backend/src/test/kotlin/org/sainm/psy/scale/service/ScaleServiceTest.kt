package org.sainm.psy.scale.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.scale.api.BatchCreateScaleDimensionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleNormsRequest
import org.sainm.psy.scale.api.BatchCreateScaleQuestionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleResultRulesRequest
import org.sainm.psy.scale.api.CreateScaleDimensionRequest
import org.sainm.psy.scale.api.CreateScaleNormRequest
import org.sainm.psy.scale.api.CreateScaleQuestionOptionRequest
import org.sainm.psy.scale.api.CreateScaleQuestionRequest
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.CreateScaleResultRuleRequest
import org.sainm.psy.scale.api.CreateScaleVersionRequest
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleDimension
import org.sainm.psy.scale.domain.ScaleNorm
import org.sainm.psy.scale.domain.ScaleQuestion
import org.sainm.psy.scale.domain.ScaleQuestionOption
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import java.math.BigDecimal
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ScaleServiceTest {

    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade

    private lateinit var scaleService: ScaleService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        scaleService = ScaleService(
            scaleRepository = scaleRepository,
            currentUserFacade = currentUserFacade,
            messages = LocalizedMessages(messageSource)
        )
    }

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
        val summaries = listOf(
            ScaleSummary(
                id = 1L,
                scaleCode = "PHQ9",
                scaleName = "PHQ-9",
                applicableTarget = null,
                versionNo = "v1",
                versionGroupId = 1L,
                currentVersionFlag = true,
                status = "PUBLISHED",
                scoreMethod = "SIMPLE_SUM",
                scoreCoefficient = BigDecimal.ONE,
                normStrategy = "RAW_SCORE",
                normDefaultGroup = null,
                highRiskWarningEnabled = false,
                anonymousSupported = false,
                createdAt = LocalDateTime.now()
            )
        )
        `when`(scaleRepository.findPage(ScaleListQuery(page = 1, size = 20))).thenReturn(summaries to 1L)

        val result = scaleService.findPage(ScaleListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
        assertEquals(1, result.page)
    }

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

    @Test
    fun `createVersion throws when target version already exists`() {
        val source = scaleDetail()
        `when`(scaleRepository.findDetailById(1L)).thenReturn(source)
        `when`(scaleRepository.existsByVersionGroupAndVersion(1L, "v2")).thenReturn(true)

        val ex = assertThrows<BizException> {
            scaleService.createVersion(1L, CreateScaleVersionRequest(versionNo = "v2"))
        }

        assertEquals("SCALE_VERSION_EXISTS", ex.code)
    }

    @Test
    fun `createVersion copies source scale into draft version`() {
        val source = scaleDetail()
        val request = CreateScaleVersionRequest(versionNo = "v2", scaleName = "PHQ-9 2026")
        `when`(scaleRepository.findDetailById(1L)).thenReturn(source)
        `when`(scaleRepository.existsByVersionGroupAndVersion(1L, "v2")).thenReturn(false)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(9L)
        `when`(scaleRepository.createVersionFrom(1L, request, 9L)).thenReturn(2L)

        val result = scaleService.createVersion(1L, request)

        assertEquals(2L, result.id)
        assertEquals(1L, result.versionGroupId)
        assertEquals("v2", result.versionNo)
        assertEquals("DRAFT", result.status)
    }

    @Test
    fun `publishVersion throws when scale not found`() {
        `when`(scaleRepository.findDetailById(404L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            scaleService.publishVersion(404L)
        }

        assertEquals("SCALE_NOT_FOUND", ex.code)
    }

    @Test
    fun `publishVersion marks version as current published version`() {
        val draftVersion = scaleDetail(id = 2L, versionNo = "v2", versionGroupId = 1L, currentVersionFlag = false, status = "DRAFT")
        `when`(scaleRepository.findDetailById(2L)).thenReturn(draftVersion)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(9L)
        `when`(scaleRepository.publishVersion(2L, 1L, 9L)).thenReturn(true)

        val result = scaleService.publishVersion(2L)

        assertEquals(2L, result.id)
        assertEquals(1L, result.versionGroupId)
        assertEquals("v2", result.versionNo)
        assertEquals("PUBLISHED", result.status)
        assertEquals(true, result.currentVersionFlag)
    }

    @Test
    fun `compareVersions throws when version groups differ`() {
        `when`(scaleRepository.findDetailById(1L)).thenReturn(scaleDetail(id = 1L, versionGroupId = 1L))
        `when`(scaleRepository.findDetailById(2L)).thenReturn(scaleDetail(id = 2L, versionGroupId = 2L))

        val ex = assertThrows<BizException> {
            scaleService.compareVersions(1L, 2L)
        }

        assertEquals("SCALE_VERSION_GROUP_MISMATCH", ex.code)
    }

    @Test
    fun `compareVersions returns added and modified changes`() {
        val from = scaleDetail(
            id = 1L,
            versionNo = "v1",
            dimensions = listOf(ScaleDimension(10L, 1L, "D1", "Mood", null, 1)),
            questions = listOf(
                ScaleQuestion(
                    id = 100L,
                    scaleId = 1L,
                    dimensionId = 10L,
                    questionNo = 1,
                    questionTitle = "Little interest",
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
                    sortNo = 1,
                    options = listOf(ScaleQuestionOption(1000L, 100L, "A", "Never", BigDecimal.ZERO, false, null, 1))
                )
            )
        )
        val to = scaleDetail(
            id = 2L,
            versionNo = "v2",
            dimensions = listOf(ScaleDimension(20L, 2L, "D1", "Mood Updated", null, 1)),
            questions = listOf(
                ScaleQuestion(
                    id = 200L,
                    scaleId = 2L,
                    dimensionId = 20L,
                    questionNo = 1,
                    questionTitle = "Little interest or pleasure",
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
                    sortNo = 1,
                    options = listOf(
                        ScaleQuestionOption(2000L, 200L, "A", "Never", BigDecimal.ZERO, false, null, 1),
                        ScaleQuestionOption(2001L, 200L, "B", "Several days", BigDecimal.ONE, false, null, 2)
                    )
                )
            )
        )
        `when`(scaleRepository.findDetailById(1L)).thenReturn(from)
        `when`(scaleRepository.findDetailById(2L)).thenReturn(to)

        val result = scaleService.compareVersions(1L, 2L)

        assertEquals("v1", result.from.versionNo)
        assertEquals("v2", result.to.versionNo)
        assertEquals(1, result.summary.addedCount)
        assertTrue(result.summary.modifiedCount >= 2)
        assertTrue(result.changes.any { it.section == "OPTION" && it.key == "1:B" && it.changeType == "ADDED" })
        assertTrue(result.changes.any { it.section == "QUESTION" && it.key == "1" && it.changeType == "MODIFIED" })
    }

    @Test
    fun `findDetail throws BizException when scale not found`() {
        `when`(scaleRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            scaleService.findDetail(99L)
        }
        assertEquals("SCALE_NOT_FOUND", ex.code)
    }

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

    @Test
    fun `batchCreateResultRules throws when scoreMin is greater than scoreMax`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(emptySet())

        val request = BatchCreateScaleResultRulesRequest(
            resultRules = listOf(resultRule(scoreMin = BigDecimal("80"), scoreMax = BigDecimal("20")))
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateResultRules(1L, request)
        }
        assertEquals("RESULT_RULE_RANGE_INVALID", ex.code)
    }

    @Test
    fun `batchCreateResultRules throws when dimension does not belong to scale`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(setOf(10L))

        val request = BatchCreateScaleResultRulesRequest(
            resultRules = listOf(resultRule(dimensionId = 999L, scoreMin = BigDecimal("0"), scoreMax = BigDecimal("50")))
        )
        val ex = assertThrows<BizException> {
            scaleService.batchCreateResultRules(1L, request)
        }
        assertEquals("DIMENSION_NOT_FOUND", ex.code)
    }

    @Test
    fun `batchCreateQuestions throws when slider question range is invalid`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(emptySet())

        val request = BatchCreateScaleQuestionsRequest(
            questions = listOf(
                CreateScaleQuestionRequest(
                    questionNo = 1,
                    questionTitle = "Stress score",
                    questionType = "SLIDER",
                    sliderMin = BigDecimal("10"),
                    sliderMax = BigDecimal("5"),
                    options = emptyList()
                )
            )
        )

        val ex = assertThrows<BizException> {
            scaleService.batchCreateQuestions(1L, request)
        }

        assertEquals("QUESTION_SLIDER_INVALID", ex.code)
    }

    @Test
    fun `batchCreateQuestions throws when matrix config is missing`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(emptySet())

        val request = BatchCreateScaleQuestionsRequest(
            questions = listOf(
                CreateScaleQuestionRequest(
                    questionNo = 1,
                    questionTitle = "Matrix item",
                    questionType = "MATRIX",
                    options = listOf(
                        CreateScaleQuestionOptionRequest("A", "Never", BigDecimal.ZERO),
                        CreateScaleQuestionOptionRequest("B", "Often", BigDecimal.ONE)
                    )
                )
            )
        )

        val ex = assertThrows<BizException> {
            scaleService.batchCreateQuestions(1L, request)
        }

        assertEquals("QUESTION_MATRIX_CONFIG_REQUIRED", ex.code)
    }

    @Test
    fun `batchCreateNorms throws when age range is invalid`() {
        `when`(scaleRepository.existsById(1L)).thenReturn(true)
        `when`(scaleRepository.findDimensionIdsByScaleId(1L)).thenReturn(emptySet())

        val request = BatchCreateScaleNormsRequest(
            norms = listOf(
                CreateScaleNormRequest(
                    normCode = "STUDENT_A",
                    ageMin = 25,
                    ageMax = 18
                )
            )
        )

        val ex = assertThrows<BizException> {
            scaleService.batchCreateNorms(1L, request)
        }

        assertEquals("NORM_AGE_RANGE_INVALID", ex.code)
    }

    @Test
    fun `getNormCoverage summarizes covered and uncovered dimensions`() {
        `when`(
            scaleRepository.findDetailById(1L)
        ).thenReturn(
            scaleDetail(
                dimensions = listOf(
                    ScaleDimension(10L, 1L, "D1", "Mood", null, 1),
                    ScaleDimension(11L, 1L, "D2", "Stress", null, 2)
                ),
                norms = listOf(
                    ScaleNorm(
                        id = 100L,
                        scaleId = 1L,
                        normCode = "GLOBAL_A",
                        normName = "Global",
                        dimensionId = null,
                        applicableTarget = null,
                        ageMin = null,
                        ageMax = null,
                        gender = null,
                        orgType = null,
                        meanScore = BigDecimal("10"),
                        stdDeviation = BigDecimal("2"),
                        tScoreMean = BigDecimal("50"),
                        tScoreStdDeviation = BigDecimal("10"),
                        sortNo = 1
                    ),
                    ScaleNorm(
                        id = 101L,
                        scaleId = 1L,
                        normCode = "D1_A",
                        normName = "Mood",
                        dimensionId = 10L,
                        applicableTarget = null,
                        ageMin = null,
                        ageMax = null,
                        gender = null,
                        orgType = null,
                        meanScore = BigDecimal("8"),
                        stdDeviation = BigDecimal("1.5"),
                        tScoreMean = BigDecimal("50"),
                        tScoreStdDeviation = BigDecimal("10"),
                        sortNo = 2
                    )
                )
            )
        )

        val result = scaleService.getNormCoverage(1L)

        assertEquals(2, result.totalNormCount)
        assertEquals(1, result.coveredDimensionCount)
        assertEquals(1, result.uncoveredDimensionCount)
        assertTrue(result.items.any { it.dimensionCode == "D1" && it.normCount == 1 })
        assertTrue(result.items.any { it.dimensionCode == "D2" && it.normCount == 0 })
    }

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

    private fun scaleDetail(
        id: Long = 1L,
        versionNo: String = "v1",
        versionGroupId: Long = 1L,
        currentVersionFlag: Boolean = true,
        status: String = "PUBLISHED",
        dimensions: List<ScaleDimension> = emptyList(),
        questions: List<ScaleQuestion> = emptyList(),
        norms: List<ScaleNorm> = emptyList()
    ) = ScaleDetail(
        id = id,
        scaleCode = "PHQ9",
        scaleName = "PHQ-9",
        description = null,
        applicableTarget = null,
        versionNo = versionNo,
        versionGroupId = versionGroupId,
        currentVersionFlag = currentVersionFlag,
        status = status,
        scoreMethod = "SIMPLE_SUM",
        scoreCoefficient = BigDecimal.ONE,
        normStrategy = "RAW_SCORE",
        normDefaultGroup = null,
        highRiskWarningEnabled = false,
        anonymousSupported = false,
        reportTemplate = null,
        createdBy = 1L,
        createdAt = LocalDateTime.now(),
        updatedBy = 1L,
        updatedAt = LocalDateTime.now(),
        dimensions = dimensions,
        questions = questions,
        resultRules = emptyList(),
        norms = norms
    )
}


