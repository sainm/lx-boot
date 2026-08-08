package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.core.domain.UserStatus
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.scale.config.ScaleImportFeatureProperties
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets

@ExtendWith(MockitoExtension::class)
class ScaleImportServiceTest {

    @Mock private lateinit var scaleRepository: ScaleRepository
    @Mock private lateinit var scaleImportRepository: ScaleImportRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var securityAuditService: SecurityAuditService
    @Mock private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var scaleImportService: ScaleImportService

    private val currentUser = UserPrincipal(
        userId = 1L,
        username = "admin",
        displayName = "Admin",
        status = UserStatus.ENABLED,
        tenantId = 7L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        scaleImportService = ScaleImportService(
            scaleRepository = scaleRepository,
            scaleImportRepository = scaleImportRepository,
            currentUserFacade = currentUserFacade,
            securityAuditService = securityAuditService,
            messages = LocalizedMessages(messageSource),
            objectMapper = ObjectMapper(),
            transactionTemplate = transactionTemplate,
            featureProperties = ScaleImportFeatureProperties()
        )
        org.mockito.Mockito.lenient().`when`(currentUserFacade.requireCurrentUser()).thenReturn(currentUser)
    }

    @Test
    fun `parse rejects non-xlsx file`() {
        val file = MockMultipartFile(
            "file",
            "scale-import.csv",
            "text/csv",
            "scaleCode,scaleName".toByteArray(StandardCharsets.UTF_8)
        )

        val ex = assertThrows<BizException> {
            scaleImportService.parse(file, "CREATE_ONLY", true)
        }

        assertEquals("SCALE_IMPORT_INVALID_FILE", ex.code)
    }

    @Test
    fun `findDetail scopes import job to current tenant`() {
        `when`(scaleImportRepository.findJobById(99L, 7L)).thenReturn(null)

        val ex = assertThrows<BizException> { scaleImportService.findDetail(99L) }

        assertEquals("SCALE_IMPORT_JOB_NOT_FOUND", ex.code)
        verify(scaleImportRepository).findJobById(99L, 7L)
    }

    @Test
    fun `parse returns parsed summary for valid workbook`() {
        val file = MockMultipartFile(
            "file",
            "scale-import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            scaleImportService.downloadTemplate().body!!.byteArray
        )
        `when`(scaleImportRepository.createJob("scale-import.xlsx", "CREATE_ONLY", true, 1L, 7L)).thenReturn(99L)
        `when`(scaleRepository.existsByScaleCode("PHQ9", 7L)).thenReturn(false)

        val result = scaleImportService.parse(file, "CREATE_ONLY", true)

        assertEquals(99L, result.importId)
        assertEquals("PARSED", result.status)
        assertEquals(1, result.summary.dimensionCount)
        assertEquals(1, result.summary.questionCount)
        assertEquals(2, result.summary.optionCount)
        assertEquals(1, result.summary.resultRuleCount)
        assertEquals(0, result.errorCount)
        assertEquals(0, result.warningCount)
        verify(scaleImportRepository).createJob("scale-import.xlsx", "CREATE_ONLY", true, 1L, 7L)
    }

    @Test
    fun `parse returns validation issues when workbook has duplicate dimension code`() {
        val workbookBytes = scaleImportService.downloadTemplate().body!!.byteArray
        val brokenBytes = duplicateDimensionRow(workbookBytes)
        val file = MockMultipartFile(
            "file",
            "broken-scale.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            brokenBytes
        )
        `when`(scaleImportRepository.createJob("broken-scale.xlsx", "CREATE_ONLY", true, 1L, 7L)).thenReturn(100L)
        `when`(scaleRepository.existsByScaleCode("PHQ9", 7L)).thenReturn(false)

        val result = scaleImportService.parse(file, "CREATE_ONLY", true)

        assertEquals("PARSE_FAILED", result.status)
        assertTrue(result.errors.any { it.errorCode == "DIMENSION_CODE_DUPLICATE" })
        assertEquals(1, result.errorCount)
        verify(scaleImportRepository).createJob("broken-scale.xlsx", "CREATE_ONLY", true, 1L, 7L)
    }

    @Test
    fun `downloadTemplate includes reserved sheets and headers for advanced roadmap items`() {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(scaleImportService.downloadTemplate().body!!.byteArray.inputStream())
        workbook.use {
            val questionSheet = it.getSheet("questions")
            val normsSheet = it.getSheet("norms")
            val highRiskRulesSheet = it.getSheet("high_risk_rules")

            assertNotNull(questionSheet)
            assertNotNull(normsSheet)
            assertNotNull(highRiskRulesSheet)
            assertEquals("optionSelectionLimit", questionSheet.getRow(0).getCell(8).stringCellValue)
            assertEquals("sliderMin", questionSheet.getRow(0).getCell(9).stringCellValue)
            assertEquals("textInputEnabled", questionSheet.getRow(0).getCell(12).stringCellValue)
            assertEquals("normCode", normsSheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("warningLevel", highRiskRulesSheet.getRow(0).getCell(4).stringCellValue)
        }
    }

    @Test
    fun `parse accepts multi select question type without reserved warning`() {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(scaleImportService.downloadTemplate().body!!.byteArray.inputStream())
        val bytes = workbook.use {
            val sheet = it.getSheet("questions")
            sheet.getRow(1).getCell(2).setCellValue("MULTI_SELECT")
            val out = java.io.ByteArrayOutputStream()
            it.write(out)
            out.toByteArray()
        }
        val file = MockMultipartFile(
            "file",
            "multi-select-scale.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        )
        `when`(scaleImportRepository.createJob("multi-select-scale.xlsx", "CREATE_ONLY", true, 1L, 7L)).thenReturn(101L)
        `when`(scaleRepository.existsByScaleCode("PHQ9", 7L)).thenReturn(false)

        val result = scaleImportService.parse(file, "CREATE_ONLY", true)

        assertEquals("PARSED", result.status)
        assertEquals(0, result.errorCount)
        assertEquals(0, result.warningCount)
    }

    @Test
    fun `parse reads reserved scale scoring fields into preview`() {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(scaleImportService.downloadTemplate().body!!.byteArray.inputStream())
        val bytes = workbook.use {
            val scaleSheet = it.getSheet("scale")
            scaleSheet.getRow(1).getCell(9).setCellValue("Z_SCORE")
            scaleSheet.getRow(1).getCell(10).setCellValue("STUDENT_DEFAULT")
            scaleSheet.getRow(1).getCell(11).setCellValue("true")
            val out = java.io.ByteArrayOutputStream()
            it.write(out)
            out.toByteArray()
        }
        val file = MockMultipartFile(
            "file",
            "norm-scale.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        )
        `when`(scaleImportRepository.createJob("norm-scale.xlsx", "CREATE_ONLY", true, 1L, 7L)).thenReturn(102L)
        `when`(scaleRepository.existsByScaleCode("PHQ9", 7L)).thenReturn(false)
        var capturedStatus: String? = null
        var capturedPreviewJson: String? = null
        var capturedErrorCount: Int? = null
        var capturedWarningCount: Int? = null
        doAnswer { invocation ->
            capturedStatus = invocation.getArgument(1)
            capturedPreviewJson = invocation.getArgument(3)
            capturedErrorCount = invocation.getArgument(4)
            capturedWarningCount = invocation.getArgument(5)
            null
        }.`when`(scaleImportRepository).updateParsedResult(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()
        )

        val result = scaleImportService.parse(file, "CREATE_ONLY", true)

        assertEquals("PARSED", result.status)
        assertEquals(0, result.errorCount)
        assertEquals(0, result.warningCount)
        assertFalse(result.summary.scaleCode.isNullOrBlank())
        assertEquals("PARSED", capturedStatus)
        assertEquals(0, capturedErrorCount)
        assertEquals(0, capturedWarningCount)
        assertNotNull(capturedPreviewJson)
        assertTrue(capturedPreviewJson!!.contains("\"normStrategy\":\"Z_SCORE\""))
        assertTrue(capturedPreviewJson!!.contains("\"normDefaultGroup\":\"STUDENT_DEFAULT\""))
        assertTrue(capturedPreviewJson!!.contains("\"highRiskWarningEnabled\":true"))
    }

    @Test
    fun `parse reads norms and high risk rules into preview`() {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(scaleImportService.downloadTemplate().body!!.byteArray.inputStream())
        val bytes = workbook.use {
            val normsSheet = it.getSheet("norms")
            normsSheet.getRow(1).getCell(0).setCellValue("NORM_X")
            val highRiskSheet = it.getSheet("high_risk_rules")
            highRiskSheet.getRow(1).getCell(1).setCellValue("1")
            highRiskSheet.getRow(1).getCell(2).setCellValue("A")
            val out = java.io.ByteArrayOutputStream()
            it.write(out)
            out.toByteArray()
        }
        val file = MockMultipartFile(
            "file",
            "advanced-scale.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        )
        `when`(scaleImportRepository.createJob("advanced-scale.xlsx", "CREATE_ONLY", true, 1L, 7L)).thenReturn(103L)
        `when`(scaleRepository.existsByScaleCode("PHQ9", 7L)).thenReturn(false)
        var previewJson: String? = null
        doAnswer { invocation ->
            previewJson = invocation.getArgument(3)
            null
        }.`when`(scaleImportRepository).updateParsedResult(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt()
        )

        val result = scaleImportService.parse(file, "CREATE_ONLY", true)

        assertEquals("PARSED", result.status)
        assertEquals(0, result.errorCount)
        assertNotNull(previewJson)
        assertTrue(previewJson!!.contains("\"norms\""))
        assertTrue(previewJson!!.contains("\"normCode\":\"NORM_X\""))
        assertTrue(previewJson!!.contains("\"highRiskRules\""))
        assertTrue(previewJson!!.contains("\"ruleCode\":\"SELF_HARM_1\""))
    }

    private fun duplicateDimensionRow(source: ByteArray): ByteArray {
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(source.inputStream())
        workbook.use {
            val sheet = it.getSheet("dimensions")
            val row = sheet.createRow(sheet.lastRowNum + 1)
            row.createCell(0).setCellValue("MOOD")
            row.createCell(1).setCellValue("Mood Copy")
            row.createCell(2).setCellValue("Duplicate dimension")
            row.createCell(3).setCellValue("2")
            val out = java.io.ByteArrayOutputStream()
            it.write(out)
            return out.toByteArray()
        }
    }
}
