package org.sainm.psy.scale.api

import jakarta.validation.Valid
import org.sainm.psy.scale.domain.ScalePackageAlgorithmBinding
import org.sainm.psy.scale.domain.ScalePackageDimensionTranslation
import org.sainm.psy.scale.domain.ScalePackageGovernance
import org.sainm.psy.scale.domain.ScalePackageHighRiskRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageOptionTranslation
import org.sainm.psy.scale.domain.ScalePackageNormGovernance
import org.sainm.psy.scale.domain.ScalePackageQualityPolicy
import org.sainm.psy.scale.domain.ScalePackageQuestionTranslation
import org.sainm.psy.scale.domain.ScalePackageResultRuleTranslation
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePackageTranslation
import org.sainm.psy.scale.domain.ScalePackageValidityRule
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.domain.ScalePublicationReview
import java.time.Instant

data class UpdateScalePackageRequest(
    @field:Valid val governance: ScalePackageGovernance? = null,
    @field:Valid val translations: List<ScalePackageTranslation> = emptyList(),
    @field:Valid val dimensionTranslations: List<ScalePackageDimensionTranslation> = emptyList(),
    @field:Valid val questionTranslations: List<ScalePackageQuestionTranslation> = emptyList(),
    @field:Valid val optionTranslations: List<ScalePackageOptionTranslation> = emptyList(),
    @field:Valid val resultRuleTranslations: List<ScalePackageResultRuleTranslation> = emptyList(),
    @field:Valid val highRiskRuleTranslations: List<ScalePackageHighRiskRuleTranslation> = emptyList(),
    @field:Valid val qualityPolicy: ScalePackageQualityPolicy? = null,
    @field:Valid val validityRules: List<ScalePackageValidityRule> = emptyList(),
    @field:Valid val algorithmBinding: ScalePackageAlgorithmBinding? = null,
    @field:Valid val normGovernance: List<ScalePackageNormGovernance> = emptyList()
)

data class ScalePackageExportDocument(
    val format: String = "PSY_SCALE_PACKAGE",
    val schemaVersion: Int = 2,
    val exportId: String,
    val exportedAt: Instant,
    val exportedBy: Long,
    val scaleContentHash: String,
    val releaseFingerprint: String,
    val payloadHash: String,
    val scale: ScaleDetail,
    val scalePackage: ScalePackageSnapshot,
    val goldenCases: List<ScaleGoldenCaseHistory>,
    val publicationReviews: List<ScalePublicationReview>
)

data class PreviewScalePackageImportResponse(
    val importId: Long,
    val fileName: String,
    val format: String? = null,
    val schemaVersion: Int? = null,
    val sourceScaleId: Long? = null,
    val scaleCode: String? = null,
    val versionNo: String? = null,
    val dimensionCount: Int = 0,
    val questionCount: Int = 0,
    val optionCount: Int = 0,
    val resultRuleCount: Int = 0,
    val goldenCaseRevisionCount: Int = 0,
    val publicationReviewCount: Int = 0,
    val readyForControlledImport: Boolean = false,
    val confirmationSupported: Boolean = false,
    val errorCount: Int,
    val warningCount: Int,
    val errors: List<ScaleImportIssueResponse>,
    val warnings: List<ScaleImportIssueResponse>
)

data class ConfirmScalePackageImportResponse(
    val importId: Long,
    val status: String,
    val scaleId: Long,
    val createdDimensionCount: Int,
    val createdQuestionCount: Int,
    val createdOptionCount: Int,
    val createdResultRuleCount: Int,
    val importedGoldenCaseRevisionCount: Int,
    val discardedGoldenCaseRunCount: Int,
    val discardedPublicationReviewCount: Int
)
