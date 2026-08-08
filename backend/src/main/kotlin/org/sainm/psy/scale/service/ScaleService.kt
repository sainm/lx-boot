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
import org.sainm.psy.scale.api.UpdateScaleBasicRequest
import org.sainm.psy.scale.api.UpdateScaleDimensionRequest
import org.sainm.psy.scale.api.UpdateScaleOptionRequest
import org.sainm.psy.scale.api.UpdateScaleQuestionRequest
import org.sainm.psy.scale.api.UpdateScaleVisualizationsRequest
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
import org.sainm.psy.visualization.domain.ScaleVisualizationConfigDraft
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ScaleService(
    private val scaleRepository: ScaleRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val visualizationService: VisualizationService
) {

    fun findPage(query: ScaleListQuery): PageResponse<ScaleSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val tenantId = currentUserFacade.requireCurrentUser().tenantId
        val (list, total) = scaleRepository.findPage(query, tenantId)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun create(request: CreateScaleRequest): CreateScaleResponse {
        val currentUser = currentUserFacade.requireCurrentUser()
        if (scaleRepository.existsByScaleCode(request.scaleCode.trim(), currentUser.tenantId)) {
            throw BizException("SCALE_CODE_EXISTS", messages.get("scale.code_exists"))
        }
        val id = scaleRepository.create(request, currentUser.userId)
        return CreateScaleResponse(id = id, status = "DRAFT")
    }

    fun findDetail(id: Long): ScaleDetail =
        findOwnedScale(id)
            ?.withVisualizationConfigs()
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))

    @Transactional
    fun delete(scaleId: Long) {
        val scale = findOwnedScale(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        if (scale.status != "DRAFT") {
            throw BizException("SCALE_NOT_DELETABLE", messages.get("scale.not_deletable"))
        }
        if (scaleRepository.isInUse(scaleId)) {
            throw BizException("SCALE_IN_USE", messages.get("scale.in_use"))
        }
        if (scaleRepository.deleteDraft(scaleId) == 0) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
    }

    @Transactional
    fun updateVisualizations(scaleId: Long, request: UpdateScaleVisualizationsRequest): ScaleDetail {
        ensureDraftScale(scaleId)
        visualizationService.replaceConfigs(
            scaleId,
            request.visualizations.map {
                ScaleVisualizationConfigDraft(
                    chartType = it.chartType,
                    chartTitle = it.chartTitle,
                    viewScope = it.viewScope,
                    dataSource = it.dataSource,
                    configJson = it.configJson,
                    enabled = it.enabled,
                    sortNo = it.sortNo
                )
            }
        )
        return findDetail(scaleId)
    }

    @Transactional
    fun updateBasic(scaleId: Long, request: UpdateScaleBasicRequest): ScaleDetail {
        ensureDraftScale(scaleId)
        val currentUserId = currentUserFacade.requireCurrentUserId()
        if (!scaleRepository.updateBasic(scaleId, request, currentUserId)) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return findDetail(scaleId)
    }

    @Transactional
    fun updateDimension(scaleId: Long, dimensionId: Long, request: UpdateScaleDimensionRequest): ScaleDetail {
        ensureDraftScale(scaleId)
        if (!scaleRepository.updateDimension(scaleId, dimensionId, request)) {
            throw BizException("DIMENSION_NOT_FOUND", "Dimension not found")
        }
        return findDetail(scaleId)
    }

    @Transactional
    fun updateQuestion(scaleId: Long, questionId: Long, request: UpdateScaleQuestionRequest): ScaleDetail {
        ensureDraftScale(scaleId)
        val dimensionIds = scaleRepository.findDimensionIdsByScaleId(scaleId)
        if (request.dimensionId != null && request.dimensionId !in dimensionIds) {
            throw BizException("DIMENSION_NOT_FOUND", "Question dimension does not belong to this scale")
        }
        if (!scaleRepository.updateQuestion(scaleId, questionId, request)) {
            throw BizException("QUESTION_NOT_FOUND", "Question not found")
        }
        return findDetail(scaleId)
    }

    @Transactional
    fun updateOption(scaleId: Long, optionId: Long, request: UpdateScaleOptionRequest): ScaleDetail {
        ensureDraftScale(scaleId)
        if (!scaleRepository.updateOption(scaleId, optionId, request)) {
            throw BizException("OPTION_NOT_FOUND", "Option not found")
        }
        return findDetail(scaleId)
    }

    fun findVersions(scaleId: Long): List<ScaleSummary> {
        val detail = findOwnedScale(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val versionGroupId = detail.versionGroupId ?: detail.id
        return scaleRepository.findVersionsByGroupId(versionGroupId)
    }

    fun compareVersions(fromScaleId: Long, toScaleId: Long): ScaleVersionDiff {
        val from = findOwnedScale(fromScaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val to = findOwnedScale(toScaleId)
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
        val source = findOwnedScale(sourceScaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        val versionGroupId = source.versionGroupId ?: source.id
        if (scaleRepository.existsByVersionGroupAndVersion(versionGroupId, versionNo)) {
            throw BizException("SCALE_VERSION_EXISTS", messages.get("scale.version_exists", versionNo))
        }
        val currentUserId = currentUserFacade.requireCurrentUserId()
        val newScaleId = scaleRepository.createVersionFrom(sourceScaleId, request.copy(versionNo = versionNo), currentUserId)
        visualizationService.copyConfigs(sourceScaleId, newScaleId)
        return CreateScaleVersionResponse(
            id = newScaleId,
            versionGroupId = versionGroupId,
            versionNo = versionNo,
            status = "DRAFT"
        )
    }

    @Transactional
    fun publishVersion(scaleId: Long): PublishScaleVersionResponse {
        val scale = findOwnedScale(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        validatePublishable(scale)
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

    private fun validatePublishable(scale: ScaleDetail) {
        if (scale.status != "DRAFT") {
            throw BizException("SCALE_NOT_DRAFT", messages.get("scale.publish.draft_required"))
        }
        if (scale.questions.isEmpty()) {
            throw BizException("SCALE_QUESTIONS_REQUIRED", messages.get("scale.publish.questions_required"))
        }
        if (scale.resultRules.none { it.dimensionId == null }) {
            throw BizException("SCALE_OVERALL_RULE_REQUIRED", messages.get("scale.publish.overall_rule_required"))
        }
        val supportedScoreMethods = setOf("SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM", "AVERAGE", "WEIGHTED_AVERAGE")
        if (scale.scoreMethod !in supportedScoreMethods) {
            throw BizException("SCALE_SCORE_METHOD_UNSUPPORTED", messages.get("scale.publish.score_method_unsupported", scale.scoreMethod))
        }
        val rulesByScope = scale.resultRules.groupBy { it.dimensionId }
        rulesByScope.forEach { (_, rules) ->
            val sorted = rules.sortedBy { it.scoreMin }
            sorted.forEach { rule ->
                if (rule.scoreMin > rule.scoreMax) {
                    throw BizException("SCALE_RESULT_RULE_INVALID", messages.get("scale.publish.rule_range_invalid"))
                }
                if (rule.scoreSource !in setOf("RAW_SCORE", "Z_SCORE", "T_SCORE")) {
                    throw BizException("SCALE_SCORE_SOURCE_UNSUPPORTED", messages.get("scale.publish.score_source_unsupported", rule.scoreSource))
                }
                if (rule.scoreSource in setOf("Z_SCORE", "T_SCORE") && scale.norms.none { norm ->
                        norm.dimensionId == rule.dimensionId && (rule.normCode.isNullOrBlank() || norm.normCode == rule.normCode)
                    }
                ) {
                    throw BizException("SCALE_NORM_REQUIRED", messages.get("scale.publish.norm_required"))
                }
            }
            sorted.zipWithNext().firstOrNull { (left, right) -> left.scoreMax >= right.scoreMin }?.let {
                throw BizException("SCALE_RESULT_RULE_OVERLAP", messages.get("scale.publish.rule_overlap"))
            }
        }
        scale.questions.filter { it.reverseScoreFlag }.firstOrNull { question -> question.options.isEmpty() }?.let {
            throw BizException("SCALE_REVERSE_RANGE_REQUIRED", messages.get("scale.publish.reverse_range_required", it.questionNo))
        }
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
        val tenantId = currentUserFacade.requireCurrentUser().tenantId
        if (!scaleRepository.existsById(scaleId, tenantId)) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
    }

    private fun ScaleDetail.withVisualizationConfigs(): ScaleDetail =
        copy(visualizationConfigs = visualizationService.findConfigs(id))

    private fun ensureDraftScale(scaleId: Long): ScaleDetail {
        val scale = findOwnedScale(scaleId)
            ?: throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        if (scale.status != "DRAFT") {
            throw BizException("SCALE_NOT_DRAFT", "Only draft scale versions can be edited. Create a new version first.")
        }
        return scale
    }

    private fun findOwnedScale(scaleId: Long): ScaleDetail? {
        val scale = scaleRepository.findDetailById(scaleId) ?: return null
        val tenantId = currentUserFacade.requireCurrentUser().tenantId
        return scale.takeIf { tenantId == null || it.tenantId == tenantId }
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
