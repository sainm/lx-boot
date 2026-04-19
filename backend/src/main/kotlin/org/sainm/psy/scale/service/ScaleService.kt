package org.sainm.psy.scale.service

import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.scale.api.BatchCreateResponse
import org.sainm.psy.scale.api.BatchCreateScaleDimensionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleNormsRequest
import org.sainm.psy.scale.api.BatchCreateScaleQuestionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleResultRulesRequest
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.CreateScaleResponse
import org.sainm.psy.scale.api.CreateScaleVersionRequest
import org.sainm.psy.scale.api.CreateScaleVersionResponse
import org.sainm.psy.scale.api.PublishScaleVersionResponse
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleNormCoverage
import org.sainm.psy.scale.domain.ScaleNormCoverageItem
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleQuestionOptionDraft
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.domain.ScaleVersionDiff
import org.sainm.psy.scale.domain.ScaleVersionDiffChange
import org.sainm.psy.scale.domain.ScaleVersionDiffSummary
import org.sainm.psy.scale.domain.ScaleVersionRef
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ScaleService(
    private val scaleRepository: ScaleRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages
) {

    fun findPage(query: ScaleListQuery): PageResponse<ScaleSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val (list, total) = scaleRepository.findPage(query)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun create(request: CreateScaleRequest): CreateScaleResponse {
        if (scaleRepository.existsByScaleCode(request.scaleCode.trim())) {
            throw BizException("SCALE_CODE_EXISTS", messages.get("scale.code_exists"))
        }
        val currentUserId = currentUserFacade.requireCurrentUserId()
        val id = scaleRepository.create(request, currentUserId)
        return CreateScaleResponse(id = id, status = "DRAFT")
    }

    fun findDetail(id: Long): ScaleDetail =
        scaleRepository.findDetailById(id)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))

    fun findVersions(scaleId: Long): List<ScaleSummary> {
        val detail = scaleRepository.findDetailById(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val versionGroupId = detail.versionGroupId ?: detail.id
        return scaleRepository.findVersionsByGroupId(versionGroupId)
    }

    fun compareVersions(fromScaleId: Long, toScaleId: Long): ScaleVersionDiff {
        val from = scaleRepository.findDetailById(fromScaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val to = scaleRepository.findDetailById(toScaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val fromGroupId = from.versionGroupId ?: from.id
        val toGroupId = to.versionGroupId ?: to.id
        if (fromGroupId != toGroupId) {
            throw BizException("SCALE_VERSION_GROUP_MISMATCH", messages.get("scale.version_group_mismatch"))
        }

        val changes = buildList {
            compareKeyed(
                section = "BASIC",
                before = mapOf("scale" to from.basicSnapshot()),
                after = mapOf("scale" to to.basicSnapshot())
            ).let(::addAll)
            compareKeyed(
                section = "DIMENSION",
                before = from.dimensions.associate { it.dimensionCode to it.snapshot() },
                after = to.dimensions.associate { it.dimensionCode to it.snapshot() }
            ).let(::addAll)
            compareKeyed(
                section = "QUESTION",
                before = from.questions.associate { it.questionNo.toString() to it.snapshot(from) },
                after = to.questions.associate { it.questionNo.toString() to it.snapshot(to) }
            ).let(::addAll)
            compareKeyed(
                section = "OPTION",
                before = from.questions.flatMap { question ->
                    question.options.map { option -> "${question.questionNo}:${option.optionCode}" to option.snapshot() }
                }.toMap(),
                after = to.questions.flatMap { question ->
                    question.options.map { option -> "${question.questionNo}:${option.optionCode}" to option.snapshot() }
                }.toMap()
            ).let(::addAll)
            compareKeyed(
                section = "RESULT_RULE",
                before = from.resultRules.associate { it.ruleKey(from) to it.snapshot(from) },
                after = to.resultRules.associate { it.ruleKey(to) to it.snapshot(to) }
            ).let(::addAll)
        }

        return ScaleVersionDiff(
            from = from.versionRef(fromGroupId),
            to = to.versionRef(toGroupId),
            summary = ScaleVersionDiffSummary(
                addedCount = changes.count { it.changeType == "ADDED" },
                removedCount = changes.count { it.changeType == "REMOVED" },
                modifiedCount = changes.count { it.changeType == "MODIFIED" }
            ),
            changes = changes
        )
    }

    @Transactional
    fun createVersion(sourceScaleId: Long, request: CreateScaleVersionRequest): CreateScaleVersionResponse {
        val versionNo = request.versionNo.trim()
        if (versionNo.isBlank()) {
            throw BizException("SCALE_VERSION_REQUIRED", messages.get("scale.version_required"))
        }
        val source = scaleRepository.findDetailById(sourceScaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val versionGroupId = source.versionGroupId ?: source.id
        if (scaleRepository.existsByVersionGroupAndVersion(versionGroupId, versionNo)) {
            throw BizException("SCALE_VERSION_EXISTS", messages.get("scale.version_exists", versionNo))
        }
        val currentUserId = currentUserFacade.requireCurrentUserId()
        val newScaleId = scaleRepository.createVersionFrom(sourceScaleId, request.copy(versionNo = versionNo), currentUserId)
        return CreateScaleVersionResponse(
            id = newScaleId,
            versionGroupId = versionGroupId,
            versionNo = versionNo,
            status = "DRAFT"
        )
    }

    @Transactional
    fun publishVersion(scaleId: Long): PublishScaleVersionResponse {
        val scale = scaleRepository.findDetailById(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val versionGroupId = scale.versionGroupId ?: scale.id
        val currentUserId = currentUserFacade.requireCurrentUserId()
        val updated = scaleRepository.publishVersion(scale.id, versionGroupId, currentUserId)
        if (!updated) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return PublishScaleVersionResponse(
            id = scale.id,
            versionGroupId = versionGroupId,
            versionNo = scale.versionNo,
            status = "PUBLISHED",
            currentVersionFlag = true
        )
    }

    @Transactional
    fun batchCreateDimensions(scaleId: Long, request: BatchCreateScaleDimensionsRequest): BatchCreateResponse {
        ensureScaleExists(scaleId)
        val duplicateCodes = request.dimensions.map { it.dimensionCode.trim() }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateCodes.isNotEmpty()) {
            throw BizException(
                "DIMENSION_CODE_DUPLICATED",
                messages.get("scale.dimension_code_duplicated", duplicateCodes.joinToString(","))
            )
        }
        val drafts = request.dimensions.map {
            ScaleDimensionDraft(
                dimensionCode = it.dimensionCode,
                dimensionName = it.dimensionName,
                description = it.description,
                sortNo = it.sortNo
            )
        }
        return scaleRepository.createDimensions(scaleId, drafts)
    }

    @Transactional
    fun batchCreateQuestions(scaleId: Long, request: BatchCreateScaleQuestionsRequest): BatchCreateResponse {
        ensureScaleExists(scaleId)
        val dimensionIds = scaleRepository.findDimensionIdsByScaleId(scaleId)
        val duplicateQuestionNos = request.questions.map { it.questionNo }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateQuestionNos.isNotEmpty()) {
            throw BizException(
                "QUESTION_NO_DUPLICATED",
                messages.get("scale.question_no_duplicated", duplicateQuestionNos.joinToString(","))
            )
        }
        request.questions.forEach { question ->
            val normalizedType = question.questionType.trim().uppercase()
            val options = question.options.map { it.optionCode.trim() }
            val duplicateOptionCodes = options.groupBy { it }.filterValues { it.size > 1 }.keys
            if (duplicateOptionCodes.isNotEmpty()) {
                throw BizException(
                    "OPTION_CODE_DUPLICATED",
                    messages.get("scale.option_code_duplicated", question.questionNo, duplicateOptionCodes.joinToString(","))
                )
            }
            if (question.dimensionId != null && question.dimensionId !in dimensionIds) {
                throw BizException("DIMENSION_NOT_FOUND", messages.get("scale.question_dimension_not_found", question.questionNo))
            }
            when (normalizedType) {
                "SINGLE_CHOICE", "MULTI_SELECT" -> {
                    if (question.options.size < 2) {
                        throw BizException("QUESTION_OPTIONS_REQUIRED", "Question ${question.questionNo} requires at least 2 options")
                    }
                    if (
                        normalizedType == "MULTI_SELECT" &&
                        question.optionSelectionLimit != null &&
                        (question.optionSelectionLimit <= 0 || question.optionSelectionLimit > question.options.size)
                    ) {
                        throw BizException("QUESTION_SELECTION_LIMIT_INVALID", "Question ${question.questionNo} selection limit is invalid")
                    }
                }
                "SLIDER" -> {
                    if (question.sliderMin == null || question.sliderMax == null || question.sliderMin >= question.sliderMax) {
                        throw BizException("QUESTION_SLIDER_INVALID", "Question ${question.questionNo} slider range is invalid")
                    }
                    if (question.sliderStep != null && question.sliderStep <= BigDecimal.ZERO) {
                        throw BizException("QUESTION_SLIDER_INVALID", "Question ${question.questionNo} slider step is invalid")
                    }
                }
                "MATRIX" -> {
                    if (question.options.size < 2) {
                        throw BizException("QUESTION_OPTIONS_REQUIRED", "Question ${question.questionNo} requires at least 2 options")
                    }
                    if (question.matrixGroupCode.isNullOrBlank() || question.rowCode.isNullOrBlank() || question.columnCode.isNullOrBlank()) {
                        throw BizException("QUESTION_MATRIX_CONFIG_REQUIRED", "Question ${question.questionNo} matrix config is required")
                    }
                }
                "TEXT_WITH_OPTION" -> {
                    if (question.options.isEmpty()) {
                        throw BizException("QUESTION_OPTIONS_REQUIRED", "Question ${question.questionNo} requires at least 1 option")
                    }
                    if (!question.textInputEnabled) {
                        throw BizException("QUESTION_TEXT_INPUT_REQUIRED", "Question ${question.questionNo} text input must be enabled")
                    }
                }
                "TEXT" -> {
                    if (question.options.isNotEmpty()) {
                        throw BizException("QUESTION_OPTIONS_NOT_ALLOWED", "Question ${question.questionNo} does not allow options")
                    }
                }
                else -> throw BizException("QUESTION_TYPE_UNSUPPORTED", "Question ${question.questionNo} type ${question.questionType} is not supported")
            }
        }
        val duplicatedMatrixCells = request.questions
            .filter { it.questionType.trim().uppercase() == "MATRIX" }
            .groupBy {
                listOf(
                    it.matrixGroupCode?.trim()?.uppercase().orEmpty(),
                    it.rowCode?.trim()?.uppercase().orEmpty(),
                    it.columnCode?.trim()?.uppercase().orEmpty()
                ).joinToString("|")
            }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicatedMatrixCells.isNotEmpty()) {
            throw BizException("QUESTION_MATRIX_CELL_DUPLICATED", "Matrix question cells are duplicated")
        }
        val drafts = request.questions.map { question ->
            ScaleQuestionDraft(
                questionNo = question.questionNo,
                questionTitle = question.questionTitle,
                questionType = question.questionType,
                dimensionId = question.dimensionId,
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
                options = question.options.map {
                    ScaleQuestionOptionDraft(
                        optionCode = it.optionCode,
                        optionLabel = it.optionLabel,
                        scoreValue = it.scoreValue,
                        exclusiveFlag = it.exclusiveFlag,
                        optionGroupCode = it.optionGroupCode,
                        sortNo = it.sortNo
                    )
                }
            )
        }
        return scaleRepository.createQuestions(scaleId, drafts)
    }

    @Transactional
    fun batchCreateResultRules(scaleId: Long, request: BatchCreateScaleResultRulesRequest): BatchCreateResponse {
        ensureScaleExists(scaleId)
        val dimensionIds = scaleRepository.findDimensionIdsByScaleId(scaleId)
        request.resultRules.forEachIndexed { index, rule ->
            if (rule.scoreMin > rule.scoreMax) {
                throw BizException("RESULT_RULE_RANGE_INVALID", messages.get("scale.result_rule_range_invalid", index + 1))
            }
            if (rule.dimensionId != null && rule.dimensionId !in dimensionIds) {
                throw BizException("DIMENSION_NOT_FOUND", messages.get("scale.result_rule_dimension_not_found", index + 1))
            }
        }
        val drafts = request.resultRules.map {
            ScaleResultRuleDraft(
                dimensionId = it.dimensionId,
                riskLevel = it.riskLevel,
                scoreMin = it.scoreMin,
                scoreMax = it.scoreMax,
                scoreSource = it.scoreSource,
                normCode = it.normCode,
                resultTitle = it.resultTitle,
                resultDescription = it.resultDescription,
                suggestionText = it.suggestionText
            )
        }
        return scaleRepository.createResultRules(scaleId, drafts)
    }

    @Transactional
    fun batchCreateNorms(scaleId: Long, request: BatchCreateScaleNormsRequest): BatchCreateResponse {
        ensureScaleExists(scaleId)
        val dimensionIds = scaleRepository.findDimensionIdsByScaleId(scaleId)
        val duplicateScopeCodes = request.norms
            .groupBy { (it.dimensionId ?: 0L) to it.normCode.trim().uppercase() }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateScopeCodes.isNotEmpty()) {
            throw BizException("NORM_CODE_DUPLICATED", "Norm code is duplicated in the same scope")
        }
        request.norms.forEach { norm ->
            if (norm.dimensionId != null && norm.dimensionId !in dimensionIds) {
                throw BizException("DIMENSION_NOT_FOUND", "Norm ${norm.normCode} dimension does not belong to this scale")
            }
            if (norm.ageMin != null && norm.ageMax != null && norm.ageMin > norm.ageMax) {
                throw BizException("NORM_AGE_RANGE_INVALID", "Norm ${norm.normCode} age range is invalid")
            }
            if ((norm.meanScore == null) != (norm.stdDeviation == null)) {
                throw BizException("NORM_MEAN_STD_REQUIRED", "Norm ${norm.normCode} requires both meanScore and stdDeviation")
            }
            if (norm.stdDeviation != null && norm.stdDeviation <= BigDecimal.ZERO) {
                throw BizException("NORM_STD_INVALID", "Norm ${norm.normCode} stdDeviation must be greater than 0")
            }
            if (norm.tScoreStdDeviation != null && norm.tScoreStdDeviation <= BigDecimal.ZERO) {
                throw BizException("NORM_T_STD_INVALID", "Norm ${norm.normCode} tScoreStdDeviation must be greater than 0")
            }
        }
        return scaleRepository.createNorms(
            scaleId,
            request.norms.map { norm ->
                org.sainm.psy.scale.domain.ScaleNormDraft(
                    normCode = norm.normCode.trim().uppercase(),
                    normName = norm.normName?.trim()?.takeIf(String::isNotBlank),
                    dimensionId = norm.dimensionId,
                    applicableTarget = norm.applicableTarget?.trim()?.takeIf(String::isNotBlank),
                    ageMin = norm.ageMin,
                    ageMax = norm.ageMax,
                    gender = norm.gender?.trim()?.takeIf(String::isNotBlank),
                    orgType = norm.orgType?.trim()?.takeIf(String::isNotBlank),
                    meanScore = norm.meanScore,
                    stdDeviation = norm.stdDeviation,
                    tScoreMean = norm.tScoreMean,
                    tScoreStdDeviation = norm.tScoreStdDeviation,
                    sortNo = norm.sortNo
                )
            }
        )
    }

    fun getNormCoverage(scaleId: Long): ScaleNormCoverage {
        val detail = findDetail(scaleId)
        val globalNormCount = detail.norms.count { it.dimensionId == null }
        val items = buildList {
            add(
                ScaleNormCoverageItem(
                    dimensionId = null,
                    dimensionCode = "GLOBAL",
                    dimensionName = "Overall",
                    normCount = globalNormCount,
                    hasGlobalNorm = globalNormCount > 0,
                    missingOverallNorm = globalNormCount == 0
                )
            )
            detail.dimensions.forEach { dimension ->
                val normCount = detail.norms.count { it.dimensionId == dimension.id }
                add(
                    ScaleNormCoverageItem(
                        dimensionId = dimension.id,
                        dimensionCode = dimension.dimensionCode,
                        dimensionName = dimension.dimensionName,
                        normCount = normCount,
                        hasGlobalNorm = globalNormCount > 0,
                        missingOverallNorm = globalNormCount == 0
                    )
                )
            }
        }
        val uncoveredDimensionCount = items.count { it.dimensionId != null && it.normCount == 0 }
        return ScaleNormCoverage(
            scaleId = detail.id,
            normStrategy = detail.normStrategy,
            defaultNormGroup = detail.normDefaultGroup,
            totalNormCount = detail.norms.size,
            coveredDimensionCount = detail.dimensions.size - uncoveredDimensionCount,
            uncoveredDimensionCount = uncoveredDimensionCount,
            items = items
        )
    }

    private fun ensureScaleExists(scaleId: Long) {
        if (!scaleRepository.existsById(scaleId)) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
    }

    private fun compareKeyed(
        section: String,
        before: Map<String, Map<String, String?>>,
        after: Map<String, Map<String, String?>>
    ): List<ScaleVersionDiffChange> {
        val keys = (before.keys + after.keys).sorted()
        return keys.mapNotNull { key ->
            val oldValue = before[key]
            val newValue = after[key]
            when {
                oldValue == null && newValue != null -> ScaleVersionDiffChange(section, key, "ADDED", after = newValue)
                oldValue != null && newValue == null -> ScaleVersionDiffChange(section, key, "REMOVED", before = oldValue)
                oldValue != null && newValue != null && oldValue != newValue ->
                    ScaleVersionDiffChange(section, key, "MODIFIED", before = oldValue, after = newValue)
                else -> null
            }
        }
    }

    private fun ScaleDetail.versionRef(versionGroupId: Long) = ScaleVersionRef(
        id = id,
        versionGroupId = versionGroupId,
        versionNo = versionNo,
        scaleName = scaleName,
        status = status,
        currentVersionFlag = currentVersionFlag
    )

    private fun ScaleDetail.basicSnapshot(): Map<String, String?> = mapOf(
        "scaleCode" to scaleCode,
        "scaleName" to scaleName,
        "description" to description,
        "applicableTarget" to applicableTarget,
        "versionNo" to versionNo,
        "status" to status,
        "scoreMethod" to scoreMethod,
        "scoreCoefficient" to scoreCoefficient.normalized(),
        "normStrategy" to normStrategy,
        "normDefaultGroup" to normDefaultGroup,
        "highRiskWarningEnabled" to highRiskWarningEnabled.toString(),
        "anonymousSupported" to anonymousSupported.toString(),
        "reportTemplate" to reportTemplate
    )

    private fun org.sainm.psy.scale.domain.ScaleDimension.snapshot(): Map<String, String?> = mapOf(
        "dimensionCode" to dimensionCode,
        "dimensionName" to dimensionName,
        "description" to description,
        "sortNo" to sortNo.toString()
    )

    private fun org.sainm.psy.scale.domain.ScaleQuestion.snapshot(scale: ScaleDetail): Map<String, String?> {
        val dimensionCode = dimensionId?.let { id -> scale.dimensions.firstOrNull { it.id == id }?.dimensionCode }
        return mapOf(
            "questionNo" to questionNo.toString(),
            "dimensionCode" to dimensionCode,
            "questionTitle" to questionTitle,
            "questionType" to questionType,
            "requiredFlag" to requiredFlag.toString(),
            "reverseScoreFlag" to reverseScoreFlag.toString(),
            "weightValue" to weightValue.normalized(),
            "optionSelectionLimit" to optionSelectionLimit?.toString(),
            "sliderMin" to sliderMin?.normalized(),
            "sliderMax" to sliderMax?.normalized(),
            "sliderStep" to sliderStep?.normalized(),
            "textInputEnabled" to textInputEnabled.toString(),
            "textInputPlaceholder" to textInputPlaceholder,
            "matrixGroupCode" to matrixGroupCode,
            "rowCode" to rowCode,
            "columnCode" to columnCode,
            "sortNo" to sortNo.toString()
        )
    }

    private fun org.sainm.psy.scale.domain.ScaleQuestionOption.snapshot(): Map<String, String?> = mapOf(
        "optionCode" to optionCode,
        "optionLabel" to optionLabel,
        "scoreValue" to scoreValue.normalized(),
        "exclusiveFlag" to exclusiveFlag.toString(),
        "optionGroupCode" to optionGroupCode,
        "sortNo" to sortNo.toString()
    )

    private fun org.sainm.psy.scale.domain.ScaleResultRule.ruleKey(scale: ScaleDetail): String {
        val dimensionCode = dimensionId?.let { id -> scale.dimensions.firstOrNull { it.id == id }?.dimensionCode } ?: "GLOBAL"
        return "$dimensionCode:$riskLevel:${scoreMin.normalized()}-${scoreMax.normalized()}"
    }

    private fun org.sainm.psy.scale.domain.ScaleResultRule.snapshot(scale: ScaleDetail): Map<String, String?> {
        val dimensionCode = dimensionId?.let { id -> scale.dimensions.firstOrNull { it.id == id }?.dimensionCode }
        return mapOf(
            "dimensionCode" to dimensionCode,
            "riskLevel" to riskLevel,
            "scoreMin" to scoreMin.normalized(),
            "scoreMax" to scoreMax.normalized(),
            "scoreSource" to scoreSource,
            "normCode" to normCode,
            "resultTitle" to resultTitle,
            "resultDescription" to resultDescription,
            "suggestionText" to suggestionText
        )
    }

    private fun BigDecimal.normalized(): String = stripTrailingZeros().toPlainString()
}
