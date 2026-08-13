package org.sainm.psy.scale.service

import org.sainm.psy.scale.api.ScaleSourcePackageDocument
import java.math.BigDecimal

data class SourcePackageProblem(
    val path: String,
    val code: String
)

object ScaleSourcePackageValidation {
    const val FORMAT = "PSY_SCALE_SOURCE_PACKAGE"
    const val SCHEMA_VERSION = 1
    const val IMPORT_MODE = "SOURCE_PACKAGE_CREATE_ONLY"
    val REQUIRED_LOCALES = setOf("zh-CN", "ja-JP", "en")
    val SUPPORTED_SCORE_METHODS = setOf("SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM", "AVERAGE", "WEIGHTED_AVERAGE")
    val SUPPORTED_RECODE_RULES = setOf("RECODE_SUM_TO_0_3", "SLEEP_DURATION_RECODE_0_3", "SLEEP_EFFICIENCY_RECODE_0_3")
    val SUPPORTED_QUESTION_TYPES = setOf("SINGLE_CHOICE", "MULTI_SELECT", "SLIDER", "MATRIX", "TEXT_WITH_OPTION", "TEXT", "TIME")
    val OPTION_QUESTION_TYPES = setOf("SINGLE_CHOICE", "MULTI_SELECT", "MATRIX", "TEXT_WITH_OPTION")

    fun validate(document: ScaleSourcePackageDocument): List<SourcePackageProblem> = buildList {
        if (document.format != FORMAT) add(SourcePackageProblem("format", "PACKAGE_FORMAT_UNSUPPORTED"))
        if (document.schemaVersion != SCHEMA_VERSION) add(SourcePackageProblem("schemaVersion", "PACKAGE_SCHEMA_UNSUPPORTED"))
        if (document.scale.scaleCode.isBlank() || document.scale.scaleName.isBlank()) {
            add(SourcePackageProblem("scale", "SOURCE_PACKAGE_SCALE_INVALID"))
        }
        if (document.governance.sourceTitle.isNullOrBlank() ||
            document.governance.authorizationStatus.isBlank() ||
            document.governance.copyrightStatus.isBlank() ||
            document.governance.nonDiagnosticStatement.isNullOrBlank() ||
            document.governance.copyrightStatus !in setOf("PENDING_REVIEW", "AUTHORIZED", "PUBLIC_DOMAIN", "RESTRICTED", "EXPIRED", "REJECTED") ||
            document.governance.authorizationStatus !in setOf("PENDING_REVIEW", "AUTHORIZED", "NOT_REQUIRED", "RESTRICTED", "EXPIRED", "REJECTED") ||
            document.governance.governanceStatus !in setOf("DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED")
        ) {
            add(SourcePackageProblem("governance", "SOURCE_PACKAGE_GOVERNANCE_INVALID"))
        }
        if (document.sourceReferences.isEmpty() || document.sourceReferences.any { it.title.isBlank() || !it.url.startsWith("https://") }) {
            add(SourcePackageProblem("sourceReferences", "SOURCE_PACKAGE_REFERENCE_MISSING"))
        }
        val normalizedScoreMethod = document.scale.scoreMethod.trim().uppercase()
        if (normalizedScoreMethod !in SUPPORTED_SCORE_METHODS) {
            add(SourcePackageProblem("scale.scoreMethod", "SOURCE_PACKAGE_SCORE_METHOD_UNSUPPORTED"))
        }
        val responseMin = document.scale.responseScale.min
        val responseMax = document.scale.responseScale.max
        val responseLabels = document.scale.responseScale.labels
        if (responseMin >= responseMax ||
            (responseLabels.isNotEmpty() && responseLabels.size != responseMax - responseMin + 1)
        ) {
            add(SourcePackageProblem("scale.responseScale", "SOURCE_PACKAGE_RESPONSE_SCALE_INVALID"))
        }
        val qualityPolicy = document.scale.qualityPolicy
        if (qualityPolicy.missingAnswerPolicy !in setOf("REJECT", "ALLOW", "PRORATE") ||
            qualityPolicy.maxMissingRatio < BigDecimal.ZERO || qualityPolicy.maxMissingRatio > BigDecimal.ONE ||
            qualityPolicy.invalidResultAction !in setOf("INVALIDATE", "REQUIRE_REVIEW", "ALLOW_WITH_WARNING")
        ) {
            add(SourcePackageProblem("scale.qualityPolicy", "SOURCE_PACKAGE_QUALITY_POLICY_INVALID"))
        }
        val binding = document.scale.algorithmBinding
        if (binding == null || !isSupportedBinding(binding.algorithmCode, binding.algorithmVersion, binding.implementationType)) {
            add(SourcePackageProblem("scale.algorithmBinding", "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED"))
        }
        val questionCount = document.questions.size
        if (questionCount == 0 || document.questions.map { it.questionNo } != (1..questionCount).toList()) {
            add(SourcePackageProblem("questions", "SOURCE_PACKAGE_QUESTION_SET_INVALID"))
        }
        if (binding?.algorithmCode == "SCL90_PROFILE" &&
            (binding.algorithmVersion != "1" || questionCount != 90 || normalizedScoreMethod != "SIMPLE_SUM" ||
                responseMin != 0 || responseMax != 4)
        ) {
            add(SourcePackageProblem("scale.algorithmBinding", "SOURCE_PACKAGE_ALGORITHM_UNSUPPORTED"))
        }
        if (document.scoring.indices.isNotEmpty() && binding?.algorithmCode != "SCL90_PROFILE") {
            add(SourcePackageProblem("scoring.indices", "SOURCE_PACKAGE_INDICES_UNSUPPORTED"))
        }
        if (document.translations.keys != REQUIRED_LOCALES || document.translations.values.any { it.scaleName.isBlank() }) {
            add(SourcePackageProblem("translations", "PACKAGE_TRANSLATION_MISSING"))
        }
        if (document.scale.instruction.keys != REQUIRED_LOCALES || document.scale.instruction.values.any { it.isBlank() }) {
            add(SourcePackageProblem("scale.instruction", "SOURCE_PACKAGE_TRANSLATION_INVALID"))
        }

        val dimensionCodes = document.dimensions.map { it.dimensionCode }
        if (dimensionCodes.size != dimensionCodes.toSet().size || dimensionCodes.any { it.isBlank() }) {
            add(SourcePackageProblem("dimensions", "SOURCE_PACKAGE_DIMENSION_INVALID"))
        }
        val dimensionSet = dimensionCodes.toSet()
        document.dimensions.forEachIndexed { index, dimension ->
            if (dimension.translations.keys != REQUIRED_LOCALES || dimension.translations.values.any { it.name.isBlank() }) {
                add(SourcePackageProblem("dimensions[$index].translations", "PACKAGE_TRANSLATION_MISSING"))
            }
            dimension.recode?.let { recode ->
                if (recode.rule !in SUPPORTED_RECODE_RULES) {
                    add(SourcePackageProblem("dimensions[$index].recode", "SOURCE_PACKAGE_RECODE_UNSUPPORTED"))
                } else {
                    val invalidBand = recode.bands.isEmpty() || recode.bands.any {
                        it.min > it.max || it.value < BigDecimal.ZERO || it.value > BigDecimal(3)
                    }
                    val ordered = recode.bands.sortedBy { it.min }
                    val overlap = ordered.zipWithNext().any { (left, right) -> right.min <= left.max }
                    val invalidRef = when (recode.rule) {
                        "SLEEP_DURATION_RECODE_0_3" ->
                            recode.startQuestionNo == null || recode.endQuestionNo == null ||
                                recode.startQuestionNo !in 1..questionCount || recode.endQuestionNo !in 1..questionCount ||
                                recode.startQuestionNo == recode.endQuestionNo
                        "SLEEP_EFFICIENCY_RECODE_0_3" ->
                            recode.startQuestionNo == null || recode.endQuestionNo == null || recode.sleepQuestionNo == null ||
                                recode.startQuestionNo !in 1..questionCount || recode.endQuestionNo !in 1..questionCount ||
                                recode.sleepQuestionNo !in 1..questionCount ||
                                setOf(recode.startQuestionNo, recode.endQuestionNo, recode.sleepQuestionNo).size != 3
                        else -> false
                    }
                    if (invalidBand || overlap || invalidRef) {
                        add(SourcePackageProblem("dimensions[$index].recode", "SOURCE_PACKAGE_RECODE_INVALID"))
                    }
                }
            }
            val questionNos = document.questions.filter { it.dimensionCode == dimension.dimensionCode }.map { it.questionNo }.toSet()
            if (questionNos != dimension.questionNos.toSet()) {
                add(SourcePackageProblem("dimensions[$index].questionNos", "SOURCE_PACKAGE_REFERENCE_INVALID"))
            }
        }
        document.questions.forEachIndexed { index, question ->
            val normalizedType = question.questionType.trim().uppercase()
            if (normalizedType !in SUPPORTED_QUESTION_TYPES) {
                add(SourcePackageProblem("questions[$index].questionType", "SOURCE_PACKAGE_QUESTION_TYPE_UNSUPPORTED"))
            }
            if (question.dimensionCode !in dimensionSet || question.translations.keys != REQUIRED_LOCALES ||
                question.translations.values.any { it.text.isBlank() } ||
                (normalizedType in OPTION_QUESTION_TYPES && question.options.isEmpty())
            ) {
                add(SourcePackageProblem("questions[$index]", "SOURCE_PACKAGE_REFERENCE_INVALID"))
            }
            val optionCodes = question.options.map { it.code }
            if (optionCodes.size != optionCodes.toSet().size || question.options.any {
                    it.score < BigDecimal(responseMin) || it.score > BigDecimal(responseMax) ||
                        it.translations.keys != REQUIRED_LOCALES || it.translations.values.any(String::isBlank)
                }) {
                add(SourcePackageProblem("questions[$index].options", "SOURCE_PACKAGE_OPTION_INVALID"))
            }
        }

        val resultRuleCodes = document.resultRules.map { it.ruleCode }
        if (resultRuleCodes.size != resultRuleCodes.toSet().size) {
            add(SourcePackageProblem("resultRules", "SOURCE_PACKAGE_RESULT_RULE_INVALID"))
        }
        document.resultRules.forEachIndexed { index, rule ->
            if (rule.ruleCode.isBlank() || rule.dimensionCode?.let { it !in dimensionSet } == true ||
                rule.scoreMin > rule.scoreMax || rule.scoreSource !in setOf("RAW_SCORE", "Z_SCORE", "T_SCORE") ||
                rule.translations.keys != REQUIRED_LOCALES ||
                rule.translations.values.any { it.resultTitle.isBlank() || it.resultDescription.isNullOrBlank() || it.suggestionText.isNullOrBlank() }
            ) {
                add(SourcePackageProblem("resultRules[$index]", "SOURCE_PACKAGE_RESULT_RULE_INVALID"))
            }
        }
        document.resultRules.groupBy { it.dimensionCode }.forEach { (dimensionCode, rules) ->
            val ordered = rules.sortedBy { it.scoreMin }
            ordered.zipWithNext().forEach { (left, right) ->
                if (right.scoreMin <= left.scoreMax) {
                    add(SourcePackageProblem("resultRules[$dimensionCode]", "SOURCE_PACKAGE_RESULT_RULE_OVERLAP"))
                }
            }
        }

        document.highRiskRules.forEachIndexed { index, rule ->
            if (rule.questionNo !in 1..questionCount || rule.ruleCode.isBlank() || rule.translations.keys != REQUIRED_LOCALES ||
                rule.translations.values.any { it.resultTitle.isBlank() || it.resultDescription.isNullOrBlank() || it.suggestionText.isNullOrBlank() }
            ) {
                add(SourcePackageProblem("highRiskRules[$index]", "SOURCE_PACKAGE_REFERENCE_INVALID"))
            }
        }
        val normFactors = document.norms.factorReferenceFromUserText
        if (normFactors.isNotEmpty()) {
            val hasNormSourceReference = document.norms.sourceReference?.isNotBlank() == true ||
                normFactors.values.any { it.sourceReference?.isNotBlank() == true }
            if (!hasNormSourceReference) {
                add(SourcePackageProblem("norms.sourceReference", "SOURCE_PACKAGE_REFERENCE_MISSING"))
            }
            normFactors.entries.forEachIndexed { index, (code, factor) ->
                if ((factor.ageMin != null && factor.ageMax != null && factor.ageMin > factor.ageMax) ||
                    (factor.validFrom != null && factor.validTo != null && factor.validFrom.isAfter(factor.validTo))
                ) {
                    add(SourcePackageProblem("norms.factorReferenceFromUserText.$code", "SOURCE_PACKAGE_NORM_INVALID"))
                }
            }
        }
        if (document.goldenCases.isEmpty()) add(SourcePackageProblem("goldenCases", "SOURCE_PACKAGE_GOLDEN_CASE_MISSING"))
        document.goldenCases.forEachIndexed { index, goldenCase ->
            if (goldenCase.caseCode.isBlank() || goldenCase.caseType.isBlank() || !goldenCase.input.isObject || !goldenCase.expected.isObject) {
                add(SourcePackageProblem("goldenCases[$index]", "SOURCE_PACKAGE_GOLDEN_CASE_INVALID"))
            }
        }
    }

    private fun isSupportedBinding(code: String, version: String, implementationType: String): Boolean =
        (code == "GENERIC_SCORE_CALCULATOR" && version == "1" && implementationType == "BUILTIN") ||
            (code == "SCL90_PROFILE" && version == "1" && implementationType == "RESTRICTED_EXTENSION")
}
