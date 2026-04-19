package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.scale.api.ConfirmScaleImportRequest
import org.sainm.psy.scale.api.ConfirmScaleImportResponse
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.ParseScaleImportResponse
import org.sainm.psy.scale.api.ScaleImportDetailResponse
import org.sainm.psy.scale.api.ScaleImportIssueResponse
import org.sainm.psy.scale.api.ScaleImportListItemResponse
import org.sainm.psy.scale.api.ScaleImportListQuery
import org.sainm.psy.scale.api.ScaleImportSummaryResponse
import org.sainm.psy.scale.domain.ScaleImportDimensionPreview
import org.sainm.psy.scale.domain.ScaleImportIssue
import org.sainm.psy.scale.domain.ScaleImportJobRecord
import org.sainm.psy.scale.domain.ScaleImportNormPreview
import org.sainm.psy.scale.domain.ScaleImportOptionPreview
import org.sainm.psy.scale.domain.ScaleImportPreview
import org.sainm.psy.scale.domain.ScaleImportQuestionPreview
import org.sainm.psy.scale.domain.ScaleImportResultRulePreview
import org.sainm.psy.scale.domain.ScaleImportScalePreview
import org.sainm.psy.scale.domain.ScaleImportSummary
import org.sainm.psy.scale.domain.ScaleImportHighRiskRulePreview
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleHighRiskRuleDraft
import org.sainm.psy.scale.domain.ScaleNormDraft
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleQuestionOptionDraft
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.config.ScaleImportFeatureProperties
import org.sainm.psy.scale.repository.ScaleImportRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

@Service
class ScaleImportService(
    private val scaleRepository: ScaleRepository,
    private val scaleImportRepository: ScaleImportRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val securityAuditService: SecurityAuditService,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val featureProperties: ScaleImportFeatureProperties
) {

    fun downloadTemplate(): ResponseEntity<ByteArrayResource> {
        val workbook = XSSFWorkbook()
        try {
            createSheet(
                workbook,
                "scale",
                listOf(
                    "scaleCode", "scaleName", "description", "applicableTarget", "versionNo",
                    "scoreMethod", "scoreCoefficient", "anonymousSupported", "reportTemplate",
                    "normStrategy", "normDefaultGroup", "highRiskWarningEnabled"
                )
            ).also {
                appendRow(
                    it,
                    listOf(
                        "PHQ9", "PHQ-9 Depression Scale", "Sample scale", "student", "v1",
                        "SIMPLE_SUM", "1.0", "false", "Sample report template",
                        "", "", "false"
                    )
                )
            }
            createSheet(workbook, "dimensions", listOf("dimensionCode", "dimensionName", "description", "sortNo"))
                .also { appendRow(it, listOf("MOOD", "Mood", "Sample dimension", "1")) }
            createSheet(
                workbook,
                "questions",
                listOf(
                    "questionNo", "questionTitle", "questionType", "dimensionCode", "requiredFlag",
                    "reverseScoreFlag", "weightValue", "sortNo", "optionSelectionLimit",
                    "sliderMin", "sliderMax", "sliderStep", "textInputEnabled",
                    "textInputPlaceholder", "matrixGroupCode", "rowCode", "columnCode"
                )
            ).also {
                appendRow(
                    it,
                    listOf(
                        "1", "Little interest or pleasure in doing things", "SINGLE_CHOICE", "MOOD",
                        "true", "false", "1.0", "1", "", "", "", "", "false", "", "", "", ""
                    )
                )
            }
            createSheet(
                workbook,
                "options",
                listOf("questionNo", "optionCode", "optionLabel", "scoreValue", "sortNo", "exclusiveFlag", "optionGroupCode")
            )
                .also {
                    appendRow(it, listOf("1", "A", "Not at all", "0", "1", "false", ""))
                    appendRow(it, listOf("1", "B", "Several days", "1", "2", "false", ""))
                }
            createSheet(
                workbook,
                "result_rules",
                listOf(
                    "dimensionCode", "riskLevel", "scoreMin", "scoreMax", "resultTitle",
                    "resultDescription", "suggestionText", "sortNo", "scoreSource", "normCode"
                )
            ).also {
                appendRow(it, listOf("", "LOW", "0", "4", "Minimal", "Sample description", "Sample suggestion", "1", "RAW_SCORE", ""))
            }
            createSheet(
                workbook,
                "norms",
                listOf(
                    "normCode", "normName", "dimensionCode", "applicableTarget", "ageMin", "ageMax",
                    "gender", "orgType", "meanScore", "stdDeviation", "tScoreMean", "tScoreStdDeviation", "sortNo"
                )
            ).also {
                appendRow(it, listOf("STUDENT_DEFAULT", "Student default norm", "", "student", "18", "25", "", "school", "10", "3", "50", "10", "1"))
            }
            createSheet(
                workbook,
                "high_risk_rules",
                listOf(
                    "ruleCode", "questionNo", "optionCode", "scoreThreshold", "warningLevel",
                    "resultTitle", "resultDescription", "suggestionText", "sortNo"
                )
            ).also {
                appendRow(it, listOf("SELF_HARM_1", "1", "B", "", "HIGH", "High risk item triggered", "Sample high risk description", "Sample intervention suggestion", "1"))
            }

            val bytes = ByteArrayOutputStream().use {
                workbook.write(it)
                it.toByteArray()
            }
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=scale-import-template.xlsx")
                .body(ByteArrayResource(bytes))
        } finally {
            workbook.close()
        }
    }

    fun parse(file: MultipartFile, importMode: String, draftFlag: Boolean): ParseScaleImportResponse {
        if (file.isEmpty) {
            throw BizException("VALIDATION_ERROR", messages.get("validation.file_required"))
        }
        if (!file.originalFilename.orEmpty().lowercase().endsWith(".xlsx")) {
            throw BizException("SCALE_IMPORT_INVALID_FILE", messages.get("scale.import.invalid_file_type"))
        }
        val normalizedMode = importMode.trim().uppercase()
        if (normalizedMode != "CREATE_ONLY") {
            throw BizException("SCALE_IMPORT_INVALID_MODE", messages.get("scale.import.invalid_mode", normalizedMode))
        }

        val currentUserId = currentUserFacade.requireCurrentUserId()
        val jobId = scaleImportRepository.createJob(file.originalFilename ?: "scale-import.xlsx", normalizedMode, draftFlag, currentUserId)
        val result = file.inputStream.use { input ->
            XSSFWorkbook(input).use { workbook ->
                parseWorkbook(workbook)
            }
        }
        scaleImportRepository.updateParsedResult(
            jobId = jobId,
            status = if (result.errors.isEmpty()) "PARSED" else "PARSE_FAILED",
            summaryJson = objectMapper.writeValueAsString(result.summary),
            previewJson = result.preview?.let(objectMapper::writeValueAsString),
            errorCount = result.errors.size,
            warningCount = result.warnings.size
        )
        scaleImportRepository.replaceIssues(jobId, result.errors + result.warnings)
        securityAuditService.runCatchingAudit(
            type = "PSY_SCALE_IMPORT_PARSED",
            detail = mapOf("importJobId" to jobId, "fileName" to (file.originalFilename ?: "scale-import.xlsx"))
        )
        return ParseScaleImportResponse(
            importId = jobId,
            fileName = file.originalFilename ?: "scale-import.xlsx",
            status = if (result.errors.isEmpty()) "PARSED" else "PARSE_FAILED",
            summary = result.summary.toResponse(),
            errorCount = result.errors.size,
            warningCount = result.warnings.size,
            errors = result.errors.map { it.toResponse() },
            warnings = result.warnings.map { it.toResponse() }
        )
    }

    fun confirm(importId: Long, request: ConfirmScaleImportRequest): ConfirmScaleImportResponse {
        val job = scaleImportRepository.findJobById(importId)
            ?: throw BizException("SCALE_IMPORT_JOB_NOT_FOUND", messages.get("scale.import.job_not_found"))
        if (job.status != "PARSED") {
            throw BizException("SCALE_IMPORT_NOT_CONFIRMABLE", messages.get("scale.import.not_confirmable"))
        }
        if (job.errorCount > 0) {
            throw BizException("SCALE_IMPORT_HAS_ERRORS", messages.get("scale.import.has_errors"))
        }
        val preview = job.previewJson?.let { objectMapper.readValue(it, ScaleImportPreview::class.java) }
            ?: throw BizException("SCALE_IMPORT_NOT_CONFIRMABLE", messages.get("scale.import.not_confirmable"))
        validateFeatureFlags(preview)

        return try {
            scaleImportRepository.markConfirmed(importId)
            val currentUserId = currentUserFacade.requireCurrentUserId()
            val response = transactionTemplate.execute {
                val scaleId = scaleRepository.create(
                    CreateScaleRequest(
                        scaleCode = preview.scale.scaleCode,
                        scaleName = preview.scale.scaleName,
                        description = preview.scale.description,
                        applicableTarget = preview.scale.applicableTarget,
                        versionNo = preview.scale.versionNo,
                        scoreMethod = preview.scale.scoreMethod,
                        scoreCoefficient = preview.scale.scoreCoefficient,
                        anonymousSupported = preview.scale.anonymousSupported,
                        reportTemplate = preview.scale.reportTemplate
                    ),
                    currentUserId
                )
                scaleRepository.updateScaleAdvancedConfig(
                    scaleId = scaleId,
                    normStrategy = preview.scale.normStrategy,
                    normDefaultGroup = preview.scale.normDefaultGroup,
                    highRiskWarningEnabled = preview.scale.highRiskWarningEnabled
                )
                val createdDimensions = scaleRepository.createDimensions(
                    scaleId,
                    preview.dimensions.map {
                        ScaleDimensionDraft(
                            dimensionCode = it.dimensionCode,
                            dimensionName = it.dimensionName,
                            description = it.description,
                            sortNo = it.sortNo
                        )
                    }
                )
                val dimensionMap = scaleRepository.findDimensionCodeIdMapByScaleId(scaleId)
                val createdQuestions = scaleRepository.createQuestions(
                    scaleId,
                    preview.questions.map { question ->
                        ScaleQuestionDraft(
                            questionNo = question.questionNo,
                            questionTitle = question.questionTitle,
                            questionType = question.questionType,
                            dimensionId = question.dimensionCode?.let(dimensionMap::get),
                            requiredFlag = question.requiredFlag,
                            reverseScoreFlag = question.reverseScoreFlag,
                            weightValue = question.weightValue,
                            optionSelectionLimit = question.optionSelectionLimit,
                            sliderMin = question.sliderMin,
                            sliderMax = question.sliderMax,
                            sliderStep = question.sliderStep,
                            textInputEnabled = question.textInputEnabled,
                            textInputPlaceholder = question.textInputPlaceholder,
                            matrixGroupCode = question.matrixGroupCode,
                            rowCode = question.rowCode,
                            columnCode = question.columnCode,
                            sortNo = question.sortNo,
                            options = question.options.map { option ->
                                ScaleQuestionOptionDraft(
                                    optionCode = option.optionCode,
                                    optionLabel = option.optionLabel,
                                    scoreValue = option.scoreValue,
                                    exclusiveFlag = option.exclusiveFlag,
                                    optionGroupCode = option.optionGroupCode,
                                    sortNo = option.sortNo
                                )
                            }
                        )
                    }
                )
                val questionNoIdMap = scaleRepository.findQuestionNoIdMapByScaleId(scaleId)
                val optionIdMap = scaleRepository.findOptionIdMapByScaleId(scaleId)
                val createdRules = scaleRepository.createResultRules(
                    scaleId,
                    preview.resultRules.map { rule ->
                        ScaleResultRuleDraft(
                            dimensionId = rule.dimensionCode?.let(dimensionMap::get),
                            riskLevel = rule.riskLevel,
                            scoreMin = rule.scoreMin,
                            scoreMax = rule.scoreMax,
                            scoreSource = rule.scoreSource,
                            normCode = rule.normCode,
                            resultTitle = rule.resultTitle,
                            resultDescription = rule.resultDescription,
                            suggestionText = rule.suggestionText
                        )
                    }
                )
                scaleRepository.createNorms(
                    scaleId,
                    preview.norms.map { norm ->
                        ScaleNormDraft(
                            normCode = norm.normCode,
                            normName = norm.normName,
                            dimensionId = norm.dimensionCode?.let(dimensionMap::get),
                            applicableTarget = norm.applicableTarget,
                            ageMin = norm.ageMin,
                            ageMax = norm.ageMax,
                            gender = norm.gender,
                            orgType = norm.orgType,
                            meanScore = norm.meanScore,
                            stdDeviation = norm.stdDeviation,
                            tScoreMean = norm.tScoreMean,
                            tScoreStdDeviation = norm.tScoreStdDeviation,
                            sortNo = norm.sortNo
                        )
                    }
                )
                scaleRepository.createHighRiskRules(
                    scaleId,
                    preview.highRiskRules.map { rule ->
                        ScaleHighRiskRuleDraft(
                            ruleCode = rule.ruleCode,
                            questionId = questionNoIdMap[rule.questionNo] ?: error("question not found for high risk rule"),
                            optionId = rule.optionCode?.let { optionIdMap[rule.questionNo to it] },
                            scoreThreshold = rule.scoreThreshold,
                            warningLevel = rule.warningLevel,
                            resultTitle = rule.resultTitle,
                            resultDescription = rule.resultDescription,
                            suggestionText = rule.suggestionText,
                            sortNo = rule.sortNo
                        )
                    }
                )
                ConfirmScaleImportResponse(
                    importId = importId,
                    status = "SUCCESS",
                    scaleId = scaleId,
                    createdDimensionCount = createdDimensions.createdIds.size,
                    createdQuestionCount = createdQuestions.createdIds.size,
                    createdOptionCount = preview.questions.sumOf { it.options.size },
                    createdResultRuleCount = createdRules.createdIds.size
                )
            } ?: error("scale import transaction did not return a result")
            scaleImportRepository.markSuccess(importId, response.scaleId)
            securityAuditService.runCatchingAudit(
                type = "PSY_SCALE_IMPORT_CONFIRMED",
                detail = mapOf("importJobId" to importId, "scaleId" to response.scaleId, "remark" to request.confirmRemark)
            )
            response
        } catch (ex: Exception) {
            scaleImportRepository.markFailed(importId)
            throw ex
        }
    }

    fun findDetail(importId: Long): ScaleImportDetailResponse {
        val job = scaleImportRepository.findJobById(importId)
            ?: throw BizException("SCALE_IMPORT_JOB_NOT_FOUND", messages.get("scale.import.job_not_found"))
        val issues = scaleImportRepository.findIssuesByJobId(importId)
        return job.toDetailResponse(issues)
    }

    fun findPage(query: ScaleImportListQuery): PageResponse<ScaleImportListItemResponse> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val (list, total) = scaleImportRepository.findPage(query)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    private fun parseWorkbook(workbook: XSSFWorkbook): ParseResult {
        val issues = mutableListOf<ScaleImportIssue>()
        val scaleSheet = workbook.getSheet("scale")
        val dimensionSheet = workbook.getSheet("dimensions")
        val questionSheet = workbook.getSheet("questions")
        val optionSheet = workbook.getSheet("options")
        val ruleSheet = workbook.getSheet("result_rules")
        val normSheet = workbook.getSheet("norms")
        val highRiskRuleSheet = workbook.getSheet("high_risk_rules")
        listOf(
            "scale" to scaleSheet,
            "dimensions" to dimensionSheet,
            "questions" to questionSheet,
            "options" to optionSheet,
            "result_rules" to ruleSheet,
            "norms" to normSheet,
            "high_risk_rules" to highRiskRuleSheet
        ).forEach { (name, sheet) ->
            if (sheet == null) {
                issues += issue("ERROR", name, null, null, "SHEET_MISSING", messages.get("scale.import.sheet_missing", name))
            }
        }
        if (issues.isNotEmpty()) return ParseResult(null, ScaleImportSummary(), issues, emptyList())

        val scaleHeaders = readHeaders(scaleSheet!!, "scale", listOf("scaleCode", "scaleName", "scoreMethod"), issues)
        val dimensionHeaders = readHeaders(dimensionSheet!!, "dimensions", listOf("dimensionCode", "dimensionName"), issues)
        val questionHeaders = readHeaders(questionSheet!!, "questions", listOf("questionNo", "questionTitle", "questionType"), issues)
        val optionHeaders = readHeaders(optionSheet!!, "options", listOf("questionNo", "optionCode", "optionLabel", "scoreValue"), issues)
        val ruleHeaders = readHeaders(ruleSheet!!, "result_rules", listOf("riskLevel", "scoreMin", "scoreMax"), issues)
        val normHeaders = readHeaders(normSheet!!, "norms", listOf("normCode"), issues)
        val highRiskHeaders = readHeaders(highRiskRuleSheet!!, "high_risk_rules", listOf("ruleCode", "questionNo", "warningLevel"), issues)
        if (issues.isNotEmpty()) return ParseResult(null, ScaleImportSummary(), issues, emptyList())

        val scaleRows = dataRows(scaleSheet)
        if (scaleRows.size != 1) {
            issues += issue("ERROR", "scale", null, null, "MULTIPLE_SCALE_ROWS", messages.get("scale.import.multiple_scale_rows"))
        }
        val scale = scaleRows.firstOrNull()?.toScalePreview(scaleHeaders)
        if (scale != null && scaleRepository.existsByScaleCode(scale.scaleCode)) {
            issues += issue("ERROR", "scale", 2, "scaleCode", "SCALE_CODE_CONFLICT", messages.get("scale.import.scale_code_conflict", scale.scaleCode))
        }

        val dimensions = dataRows(dimensionSheet).mapIndexed { index, row ->
            ScaleImportDimensionPreview(
                dimensionCode = row.requiredString(dimensionHeaders, "dimensionCode"),
                dimensionName = row.requiredString(dimensionHeaders, "dimensionName"),
                description = row.optionalString(dimensionHeaders, "description"),
                sortNo = row.optionalInt(dimensionHeaders, "sortNo") ?: (index + 1)
            )
        }
        dimensions.groupingBy { it.dimensionCode }.eachCount().filterValues { it > 1 }.keys.forEach {
            issues += issue("ERROR", "dimensions", null, "dimensionCode", "DIMENSION_CODE_DUPLICATE", messages.get("scale.import.dimension_code_duplicate", it))
        }

        val optionRowsByQuestionNo = dataRows(optionSheet).groupBy { it.requiredString(optionHeaders, "questionNo") }
        val questions = dataRows(questionSheet).mapIndexed { index, row ->
            val questionNoText = row.requiredString(questionHeaders, "questionNo")
            val questionNo = questionNoText.toIntOrNull() ?: 0
            val optionRows = optionRowsByQuestionNo[questionNoText].orEmpty()
            ScaleImportQuestionPreview(
                questionNo = questionNo,
                questionTitle = row.requiredString(questionHeaders, "questionTitle"),
                questionType = row.requiredString(questionHeaders, "questionType").uppercase(),
                dimensionCode = row.optionalString(questionHeaders, "dimensionCode"),
                requiredFlag = row.optionalBoolean(questionHeaders, "requiredFlag") ?: true,
                reverseScoreFlag = row.optionalBoolean(questionHeaders, "reverseScoreFlag") ?: false,
                weightValue = row.optionalDecimal(questionHeaders, "weightValue") ?: BigDecimal.ONE,
                optionSelectionLimit = row.optionalInt(questionHeaders, "optionSelectionLimit"),
                sliderMin = row.optionalDecimal(questionHeaders, "sliderMin"),
                sliderMax = row.optionalDecimal(questionHeaders, "sliderMax"),
                sliderStep = row.optionalDecimal(questionHeaders, "sliderStep"),
                textInputEnabled = row.optionalBoolean(questionHeaders, "textInputEnabled") ?: false,
                textInputPlaceholder = row.optionalString(questionHeaders, "textInputPlaceholder"),
                matrixGroupCode = row.optionalString(questionHeaders, "matrixGroupCode"),
                rowCode = row.optionalString(questionHeaders, "rowCode"),
                columnCode = row.optionalString(questionHeaders, "columnCode"),
                sortNo = row.optionalInt(questionHeaders, "sortNo") ?: (index + 1),
                options = optionRows.mapIndexed { optionIndex, optionRow ->
                    ScaleImportOptionPreview(
                        optionCode = optionRow.requiredString(optionHeaders, "optionCode"),
                        optionLabel = optionRow.requiredString(optionHeaders, "optionLabel"),
                        scoreValue = optionRow.optionalDecimal(optionHeaders, "scoreValue") ?: BigDecimal.ZERO,
                        exclusiveFlag = optionRow.optionalBoolean(optionHeaders, "exclusiveFlag") ?: false,
                        optionGroupCode = optionRow.optionalString(optionHeaders, "optionGroupCode"),
                        sortNo = optionRow.optionalInt(optionHeaders, "sortNo") ?: (optionIndex + 1)
                    )
                }
            )
        }
        questions.groupingBy { it.questionNo }.eachCount().filterValues { it > 1 }.keys.forEach {
            issues += issue("ERROR", "questions", null, "questionNo", "QUESTION_NO_DUPLICATE", messages.get("scale.import.question_no_duplicate", it))
        }

        val dimensionCodes = dimensions.map { it.dimensionCode }.toSet()
        questions.forEach { question ->
            when (question.questionType) {
                "SINGLE_CHOICE" -> {
                    if (question.options.size < 2) {
                        issues += issue("ERROR", "options", null, "questionNo", "QUESTION_OPTIONS_INSUFFICIENT", messages.get("scale.import.question_options_insufficient", question.questionNo))
                    }
                    if (question.sliderMin != null || question.sliderMax != null || question.sliderStep != null) {
                        issues += issue("WARNING", "questions", null, "sliderMin", "QUESTION_UNUSED_SLIDER_FIELDS", "Slider fields will be ignored for SINGLE_CHOICE question ${question.questionNo}")
                    }
                }
                "MULTI_SELECT" -> {
                    if (!featureProperties.multiSelectEnabled) {
                        issues += issue("WARNING", "questions", null, "questionType", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "MULTI_SELECT"))
                    }
                    if (question.options.size < 2) {
                        issues += issue("ERROR", "options", null, "questionNo", "QUESTION_OPTIONS_INSUFFICIENT", messages.get("scale.import.question_options_insufficient", question.questionNo))
                    }
                    if (question.optionSelectionLimit != null && question.optionSelectionLimit <= 0) {
                        issues += issue("ERROR", "questions", null, "optionSelectionLimit", "QUESTION_SELECTION_LIMIT_INVALID", "optionSelectionLimit must be greater than 0 for question ${question.questionNo}")
                    }
                    if (question.options.count { it.exclusiveFlag } > 1) {
                        issues += issue("ERROR", "options", null, "exclusiveFlag", "QUESTION_EXCLUSIVE_OPTION_DUPLICATED", "Only one exclusive option is allowed for question ${question.questionNo}")
                    }
                }
                "SLIDER" -> {
                    if (!featureProperties.sliderEnabled) {
                        issues += issue("WARNING", "questions", null, "questionType", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "SLIDER"))
                    }
                    if (question.sliderMin == null || question.sliderMax == null) {
                        issues += issue("ERROR", "questions", null, "sliderMin", "QUESTION_SLIDER_RANGE_REQUIRED", "sliderMin and sliderMax are required for slider question ${question.questionNo}")
                    } else if (question.sliderMin >= question.sliderMax) {
                        issues += issue("ERROR", "questions", null, "sliderMin", "QUESTION_SLIDER_RANGE_INVALID", "sliderMin must be less than sliderMax for question ${question.questionNo}")
                    }
                    if (question.sliderStep != null && question.sliderStep <= BigDecimal.ZERO) {
                        issues += issue("ERROR", "questions", null, "sliderStep", "QUESTION_SLIDER_STEP_INVALID", "sliderStep must be greater than 0 for question ${question.questionNo}")
                    }
                }
                "MATRIX" -> {
                    if (!featureProperties.matrixEnabled) {
                        issues += issue("WARNING", "questions", null, "questionType", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "MATRIX"))
                    }
                    if (question.matrixGroupCode.isNullOrBlank() || question.rowCode.isNullOrBlank() || question.columnCode.isNullOrBlank()) {
                        issues += issue("ERROR", "questions", null, "matrixGroupCode", "QUESTION_MATRIX_FIELDS_REQUIRED", "matrixGroupCode, rowCode and columnCode are required for MATRIX question ${question.questionNo}")
                    }
                    if (question.options.size < 2) {
                        issues += issue("ERROR", "options", null, "questionNo", "QUESTION_OPTIONS_INSUFFICIENT", messages.get("scale.import.question_options_insufficient", question.questionNo))
                    }
                }
                "TEXT_WITH_OPTION" -> {
                    if (!featureProperties.textWithOptionEnabled) {
                        issues += issue("WARNING", "questions", null, "questionType", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "TEXT_WITH_OPTION"))
                    }
                    if (question.options.isEmpty()) {
                        issues += issue("ERROR", "options", null, "questionNo", "QUESTION_OPTIONS_INSUFFICIENT", messages.get("scale.import.question_options_insufficient", question.questionNo))
                    }
                    if (question.textInputEnabled && question.textInputPlaceholder.isNullOrBlank()) {
                        issues += issue("WARNING", "questions", null, "textInputPlaceholder", "QUESTION_TEXT_PLACEHOLDER_RECOMMENDED", "textInputPlaceholder is recommended when textInputEnabled=true for question ${question.questionNo}")
                    }
                }
                else -> {
                    issues += issue("ERROR", "questions", null, "questionType", "QUESTION_TYPE_UNSUPPORTED", messages.get("scale.import.question_type_unsupported"))
                }
            }
            if (question.dimensionCode != null && question.dimensionCode !in dimensionCodes) {
                issues += issue("ERROR", "questions", null, "dimensionCode", "QUESTION_DIMENSION_MISSING", messages.get("scale.import.question_dimension_missing", question.questionNo, question.dimensionCode))
            }
            question.options.groupingBy { it.optionCode }.eachCount().filterValues { it > 1 }.keys.forEach { optionCode ->
                issues += issue("ERROR", "options", null, "optionCode", "OPTION_CODE_DUPLICATE", messages.get("scale.import.option_code_duplicate", question.questionNo, optionCode))
            }
        }

        val questionNos = questions.map { it.questionNo.toString() }.toSet()
        dataRows(optionSheet).forEach { row ->
            val questionNo = row.requiredString(optionHeaders, "questionNo")
            if (questionNo !in questionNos) {
                issues += issue("ERROR", "options", row.rowNum + 1, "questionNo", "OPTION_QUESTION_MISSING", messages.get("scale.import.option_question_missing", questionNo))
            }
        }

        val resultRules = dataRows(ruleSheet).mapIndexed { index, row ->
            ScaleImportResultRulePreview(
                dimensionCode = row.optionalString(ruleHeaders, "dimensionCode"),
                riskLevel = row.requiredString(ruleHeaders, "riskLevel").uppercase(),
                scoreMin = row.optionalDecimal(ruleHeaders, "scoreMin") ?: BigDecimal.ZERO,
                scoreMax = row.optionalDecimal(ruleHeaders, "scoreMax") ?: BigDecimal.ZERO,
                scoreSource = row.optionalString(ruleHeaders, "scoreSource")?.uppercase() ?: "RAW_SCORE",
                normCode = row.optionalString(ruleHeaders, "normCode"),
                resultTitle = row.optionalString(ruleHeaders, "resultTitle"),
                resultDescription = row.optionalString(ruleHeaders, "resultDescription"),
                suggestionText = row.optionalString(ruleHeaders, "suggestionText"),
                sortNo = row.optionalInt(ruleHeaders, "sortNo") ?: (index + 1)
            )
        }
        resultRules.forEach { rule ->
            if (rule.dimensionCode != null && rule.dimensionCode !in dimensionCodes) {
                issues += issue("ERROR", "result_rules", null, "dimensionCode", "RULE_DIMENSION_MISSING", messages.get("scale.import.rule_dimension_missing", rule.dimensionCode))
            }
            if (rule.scoreMin > rule.scoreMax) {
                issues += issue("ERROR", "result_rules", null, "scoreMin", "RULE_RANGE_INVALID", messages.get("scale.import.rule_range_invalid"))
            }
            if (rule.scoreSource !in setOf("RAW_SCORE", "Z_SCORE", "T_SCORE")) {
                issues += issue("ERROR", "result_rules", null, "scoreSource", "RULE_SCORE_SOURCE_UNSUPPORTED", "Unsupported scoreSource ${rule.scoreSource}")
            }
        }
        resultRules.groupBy { it.dimensionCode ?: "__OVERALL__" }.values.forEach { rules ->
            val sorted = rules.sortedBy { it.scoreMin }
            for (index in 1 until sorted.size) {
                if (sorted[index - 1].scoreMax >= sorted[index].scoreMin) {
                    issues += issue("ERROR", "result_rules", null, "scoreMin", "RULE_RANGE_OVERLAP", messages.get("scale.import.rule_range_overlap"))
                    break
                }
            }
        }

        val norms = dataRows(normSheet).mapIndexed { index, row ->
            ScaleImportNormPreview(
                normCode = row.requiredString(normHeaders, "normCode"),
                normName = row.optionalString(normHeaders, "normName"),
                dimensionCode = row.optionalString(normHeaders, "dimensionCode"),
                applicableTarget = row.optionalString(normHeaders, "applicableTarget"),
                ageMin = row.optionalInt(normHeaders, "ageMin"),
                ageMax = row.optionalInt(normHeaders, "ageMax"),
                gender = row.optionalString(normHeaders, "gender"),
                orgType = row.optionalString(normHeaders, "orgType"),
                meanScore = row.optionalDecimal(normHeaders, "meanScore"),
                stdDeviation = row.optionalDecimal(normHeaders, "stdDeviation"),
                tScoreMean = row.optionalDecimal(normHeaders, "tScoreMean"),
                tScoreStdDeviation = row.optionalDecimal(normHeaders, "tScoreStdDeviation"),
                sortNo = row.optionalInt(normHeaders, "sortNo") ?: (index + 1)
            )
        }
        if (!featureProperties.normScoringEnabled && norms.isNotEmpty()) {
            issues += issue("WARNING", "norms", null, "normCode", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "NORM_SCORING"))
        }
        norms.groupBy { (it.dimensionCode ?: "__OVERALL__") to it.normCode }.values.forEach { grouped ->
            if (grouped.size > 1) {
                issues += issue("ERROR", "norms", null, "normCode", "NORM_CODE_DUPLICATE", "Duplicate normCode ${grouped.first().normCode} in the same scope")
            }
        }
        norms.forEach { norm ->
            if (norm.dimensionCode != null && norm.dimensionCode !in dimensionCodes) {
                issues += issue("ERROR", "norms", null, "dimensionCode", "NORM_DIMENSION_MISSING", "Norm ${norm.normCode} references a non-existing dimension code ${norm.dimensionCode}")
            }
            if ((norm.meanScore == null) != (norm.stdDeviation == null)) {
                issues += issue("ERROR", "norms", null, "meanScore", "NORM_MEAN_STD_REQUIRED", "Norm ${norm.normCode} requires both meanScore and stdDeviation")
            }
            if (norm.stdDeviation != null && norm.stdDeviation <= BigDecimal.ZERO) {
                issues += issue("ERROR", "norms", null, "stdDeviation", "NORM_STD_INVALID", "Norm ${norm.normCode} stdDeviation must be greater than 0")
            }
        }

        val questionNoMap = questions.associateBy { it.questionNo }
        val highRiskRules = dataRows(highRiskRuleSheet).mapIndexed { index, row ->
            ScaleImportHighRiskRulePreview(
                ruleCode = row.requiredString(highRiskHeaders, "ruleCode"),
                questionNo = row.requiredString(highRiskHeaders, "questionNo").toIntOrNull() ?: 0,
                optionCode = row.optionalString(highRiskHeaders, "optionCode"),
                scoreThreshold = row.optionalDecimal(highRiskHeaders, "scoreThreshold"),
                warningLevel = row.requiredString(highRiskHeaders, "warningLevel").uppercase(),
                resultTitle = row.optionalString(highRiskHeaders, "resultTitle"),
                resultDescription = row.optionalString(highRiskHeaders, "resultDescription"),
                suggestionText = row.optionalString(highRiskHeaders, "suggestionText"),
                sortNo = row.optionalInt(highRiskHeaders, "sortNo") ?: (index + 1)
            )
        }
        if (!featureProperties.highRiskRuleEnabled && highRiskRules.isNotEmpty()) {
            issues += issue("WARNING", "high_risk_rules", null, "ruleCode", "FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "HIGH_RISK_RULE"))
        }
        highRiskRules.groupBy { it.ruleCode }.values.forEach { grouped ->
            if (grouped.size > 1) {
                issues += issue("ERROR", "high_risk_rules", null, "ruleCode", "HIGH_RISK_RULE_DUPLICATE", "Duplicate high risk rule code ${grouped.first().ruleCode}")
            }
        }
        highRiskRules.forEach { rule ->
            val question = questionNoMap[rule.questionNo]
            if (question == null) {
                issues += issue("ERROR", "high_risk_rules", null, "questionNo", "HIGH_RISK_QUESTION_MISSING", "High risk rule ${rule.ruleCode} references a non-existing question ${rule.questionNo}")
            } else {
                if (rule.optionCode != null && question.options.none { it.optionCode == rule.optionCode }) {
                    issues += issue("ERROR", "high_risk_rules", null, "optionCode", "HIGH_RISK_OPTION_MISSING", "High risk rule ${rule.ruleCode} references a non-existing option ${rule.optionCode}")
                }
                if (rule.optionCode == null && rule.scoreThreshold == null) {
                    issues += issue("ERROR", "high_risk_rules", null, "scoreThreshold", "HIGH_RISK_CONDITION_REQUIRED", "High risk rule ${rule.ruleCode} requires optionCode or scoreThreshold")
                }
            }
        }

        val summary = ScaleImportSummary(
            scaleCode = scale?.scaleCode,
            scaleName = scale?.scaleName,
            dimensionCount = dimensions.size,
            questionCount = questions.size,
            optionCount = questions.sumOf { it.options.size },
            resultRuleCount = resultRules.size
        )
        val preview = scale?.let {
            ScaleImportPreview(
                scale = it,
                dimensions = dimensions,
                questions = questions,
                resultRules = resultRules,
                norms = norms,
                highRiskRules = highRiskRules
            )
        }
        return ParseResult(
            preview = preview,
            summary = summary,
            errors = issues.filter { it.severity == "ERROR" },
            warnings = issues.filter { it.severity == "WARNING" }
        )
    }

    private fun createSheet(workbook: XSSFWorkbook, name: String, headers: List<String>) =
        workbook.createSheet(name).also { sheet ->
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, value -> headerRow.createCell(index).setCellValue(value) }
        }

    private fun appendRow(sheet: org.apache.poi.ss.usermodel.Sheet, values: List<String>) {
        val row = sheet.createRow(sheet.lastRowNum + 1)
        values.forEachIndexed { index, value -> row.createCell(index).setCellValue(value) }
    }

    private fun readHeaders(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        sheetName: String,
        requiredHeaders: List<String>,
        issues: MutableList<ScaleImportIssue>
    ): Map<String, Int> {
        val row = sheet.getRow(0) ?: return emptyMap()
        val headers = buildMap {
            for (index in 0 until row.lastCellNum) {
                val value = row.getCell(index)?.toString()?.trim().orEmpty()
                if (value.isNotEmpty()) put(value, index)
            }
        }
        requiredHeaders.filterNot(headers::containsKey).forEach { header ->
            issues += issue("ERROR", sheetName, 1, header, "HEADER_MISSING", messages.get("scale.import.header_missing", header, sheetName))
        }
        return headers
    }

    private fun dataRows(sheet: org.apache.poi.ss.usermodel.Sheet): List<Row> =
        (1..sheet.lastRowNum).mapNotNull(sheet::getRow).filterNot { row ->
            (0 until row.lastCellNum).all { index -> row.getCell(index)?.toString()?.trim().isNullOrEmpty() }
        }

    private fun Row.toScalePreview(headers: Map<String, Int>) = ScaleImportScalePreview(
        scaleCode = requiredString(headers, "scaleCode"),
        scaleName = requiredString(headers, "scaleName"),
        description = optionalString(headers, "description"),
        applicableTarget = optionalString(headers, "applicableTarget"),
        versionNo = optionalString(headers, "versionNo"),
        scoreMethod = requiredString(headers, "scoreMethod").uppercase(),
        scoreCoefficient = optionalDecimal(headers, "scoreCoefficient") ?: BigDecimal.ONE,
        normStrategy = optionalString(headers, "normStrategy")?.uppercase() ?: "RAW_SCORE",
        normDefaultGroup = optionalString(headers, "normDefaultGroup"),
        highRiskWarningEnabled = optionalBoolean(headers, "highRiskWarningEnabled") ?: false,
        anonymousSupported = optionalBoolean(headers, "anonymousSupported") ?: false,
        reportTemplate = optionalString(headers, "reportTemplate")
    )

    private fun Row.requiredString(headers: Map<String, Int>, header: String): String =
        optionalString(headers, header).orEmpty()

    private fun Row.optionalString(headers: Map<String, Int>, header: String): String? {
        val index = headers[header] ?: return null
        val cell = getCell(index) ?: return null
        val raw = when (cell.cellType) {
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> cell.toString()
        }
        return raw.trim().takeIf { it.isNotEmpty() }
    }

    private fun Row.optionalDecimal(headers: Map<String, Int>, header: String): BigDecimal? =
        optionalString(headers, header)?.toBigDecimalOrNull()

    private fun Row.optionalInt(headers: Map<String, Int>, header: String): Int? =
        optionalString(headers, header)?.toIntOrNull()

    private fun Row.optionalBoolean(headers: Map<String, Int>, header: String): Boolean? =
        optionalString(headers, header)?.lowercase()?.let {
            when (it) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
        }

    private fun issue(
        severity: String,
        sheetName: String,
        rowNo: Int?,
        columnName: String?,
        errorCode: String,
        message: String
    ) = ScaleImportIssue(severity, sheetName, rowNo, columnName, errorCode, message)

    private fun ScaleImportIssue.toResponse() =
        ScaleImportIssueResponse(severity, sheetName, rowNo, columnName, errorCode, message)

    private fun ScaleImportSummary.toResponse() =
        ScaleImportSummaryResponse(scaleCode, scaleName, dimensionCount, questionCount, optionCount, resultRuleCount)

    private fun ScaleImportJobRecord.toDetailResponse(issues: List<ScaleImportIssue>): ScaleImportDetailResponse {
        val summary = summaryJson?.let { objectMapper.readValue(it, ScaleImportSummary::class.java) } ?: ScaleImportSummary()
        return ScaleImportDetailResponse(
            id = id,
            fileName = fileName,
            importMode = importMode,
            draftFlag = draftFlag,
            status = status,
            operatorUserId = operatorUserId,
            createdScaleId = createdScaleId,
            parsedAt = parsedAt,
            confirmedAt = confirmedAt,
            finishedAt = finishedAt,
            summary = summary.toResponse(),
            errors = issues.filter { it.severity == "ERROR" }.map { it.toResponse() },
            warnings = issues.filter { it.severity == "WARNING" }.map { it.toResponse() }
        )
    }

    private data class ParseResult(
        val preview: ScaleImportPreview?,
        val summary: ScaleImportSummary,
        val errors: List<ScaleImportIssue>,
        val warnings: List<ScaleImportIssue>
    )

    private fun validateFeatureFlags(preview: ScaleImportPreview) {
        val questionTypes = preview.questions.map { it.questionType }.toSet()
        if (questionTypes.contains("MULTI_SELECT") && !featureProperties.multiSelectEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "MULTI_SELECT"))
        }
        if (questionTypes.contains("SLIDER") && !featureProperties.sliderEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "SLIDER"))
        }
        if (questionTypes.contains("MATRIX") && !featureProperties.matrixEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "MATRIX"))
        }
        if (questionTypes.contains("TEXT_WITH_OPTION") && !featureProperties.textWithOptionEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "TEXT_WITH_OPTION"))
        }
        if ((preview.norms.isNotEmpty() || preview.scale.normStrategy != "RAW_SCORE") && !featureProperties.normScoringEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "NORM_SCORING"))
        }
        if ((preview.highRiskRules.isNotEmpty() || preview.scale.highRiskWarningEnabled) && !featureProperties.highRiskRuleEnabled) {
            throw BizException("SCALE_IMPORT_FEATURE_DISABLED", messages.get("scale.import.feature_disabled", "HIGH_RISK_RULE"))
        }
    }
}
