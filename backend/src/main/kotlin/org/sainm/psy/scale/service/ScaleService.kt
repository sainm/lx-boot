package org.sainm.psy.scale.service

import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.scale.api.CreateScaleRequest
import org.sainm.psy.scale.api.CreateScaleResponse
import org.sainm.psy.scale.api.BatchCreateResponse
import org.sainm.psy.scale.api.BatchCreateScaleDimensionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleQuestionsRequest
import org.sainm.psy.scale.api.BatchCreateScaleResultRulesRequest
import org.sainm.psy.scale.api.ScaleListQuery
import org.sainm.psy.scale.domain.ScaleDimensionDraft
import org.sainm.psy.scale.domain.ScaleQuestionDraft
import org.sainm.psy.scale.domain.ScaleQuestionOptionDraft
import org.sainm.psy.scale.domain.ScaleResultRuleDraft
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleSummary
import org.sainm.psy.scale.repository.ScaleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ScaleService(
    private val scaleRepository: ScaleRepository,
    private val currentUserFacade: CurrentUserFacade
) {

    fun findPage(query: ScaleListQuery): PageResponse<ScaleSummary> {
        require(query.page > 0) { "page 必须大于 0" }
        require(query.size in 1..200) { "size 必须在 1 到 200 之间" }
        val (list, total) = scaleRepository.findPage(query)
        return PageResponse(
            list = list,
            page = query.page,
            size = query.size,
            total = total
        )
    }

    @Transactional
    fun create(request: CreateScaleRequest): CreateScaleResponse {
        if (scaleRepository.existsByScaleCode(request.scaleCode.trim())) {
            throw BizException("SCALE_CODE_EXISTS", "量表编码已存在")
        }
        val currentUserId = currentUserFacade.requireCurrentUserId()
        val id = scaleRepository.create(request, currentUserId)
        return CreateScaleResponse(id = id, status = "DRAFT")
    }

    fun findDetail(id: Long): ScaleDetail =
        scaleRepository.findDetailById(id)
            ?: throw BizException("SCALE_NOT_FOUND", "量表不存在")

    @Transactional
    fun batchCreateDimensions(scaleId: Long, request: BatchCreateScaleDimensionsRequest): BatchCreateResponse {
        ensureScaleExists(scaleId)
        val duplicateCodes = request.dimensions.map { it.dimensionCode.trim() }
            .groupBy { it }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateCodes.isNotEmpty()) {
            throw BizException("DIMENSION_CODE_DUPLICATED", "维度编码重复: ${duplicateCodes.joinToString(",")}")
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
            throw BizException("QUESTION_NO_DUPLICATED", "题号重复: ${duplicateQuestionNos.joinToString(",")}")
        }
        request.questions.forEach { question ->
            val options = question.options.map { it.optionCode.trim() }
            val duplicateOptionCodes = options.groupBy { it }.filterValues { it.size > 1 }.keys
            if (duplicateOptionCodes.isNotEmpty()) {
                throw BizException(
                    "OPTION_CODE_DUPLICATED",
                    "题目 ${question.questionNo} 的选项编码重复: ${duplicateOptionCodes.joinToString(",")}"
                )
            }
            if (question.dimensionId != null && question.dimensionId !in dimensionIds) {
                throw BizException("DIMENSION_NOT_FOUND", "题目 ${question.questionNo} 关联的维度不存在")
            }
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
                sortNo = question.sortNo,
                options = question.options.map {
                    ScaleQuestionOptionDraft(
                        optionCode = it.optionCode,
                        optionLabel = it.optionLabel,
                        scoreValue = it.scoreValue,
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
                throw BizException("RESULT_RULE_RANGE_INVALID", "第 ${index + 1} 条结果规则的分值范围不合法")
            }
            if (rule.dimensionId != null && rule.dimensionId !in dimensionIds) {
                throw BizException("DIMENSION_NOT_FOUND", "第 ${index + 1} 条结果规则关联的维度不存在")
            }
        }
        val drafts = request.resultRules.map {
            ScaleResultRuleDraft(
                dimensionId = it.dimensionId,
                riskLevel = it.riskLevel,
                scoreMin = it.scoreMin,
                scoreMax = it.scoreMax,
                resultTitle = it.resultTitle,
                resultDescription = it.resultDescription,
                suggestionText = it.suggestionText
            )
        }
        return scaleRepository.createResultRules(scaleId, drafts)
    }

    private fun ensureScaleExists(scaleId: Long) {
        if (!scaleRepository.existsById(scaleId)) {
            throw BizException("SCALE_NOT_FOUND", "量表不存在")
        }
    }
}
