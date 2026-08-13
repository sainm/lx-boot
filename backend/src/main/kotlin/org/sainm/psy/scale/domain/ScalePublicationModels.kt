package org.sainm.psy.scale.domain

import java.time.LocalDateTime

data class ScaleGoldenCase(
    val id: Long,
    val scaleId: Long,
    val caseCode: String,
    val revisionNo: Int,
    val caseType: String,
    val sourceReference: String,
    val scaleContentHash: String,
    val caseContentHash: String,
    val inputJson: String,
    val expectedJson: String,
    val createdBy: Long,
    val createdAt: LocalDateTime,
    val approvedBy: Long? = null,
    val approvedAt: LocalDateTime? = null
)

data class ScaleGoldenCaseRun(
    val id: Long,
    val goldenCaseId: Long,
    val scaleContentHash: String,
    val caseContentHash: String,
    val algorithmCode: String?,
    val algorithmVersion: String?,
    val passed: Boolean,
    val actualJson: String,
    val differencesJson: String,
    val executedBy: Long,
    val executedAt: LocalDateTime
)

data class ScaleGoldenCaseHistory(
    val goldenCase: ScaleGoldenCase,
    val runs: List<ScaleGoldenCaseRun>
)

data class ScalePublicationHistory(
    val cases: List<ScaleGoldenCaseHistory>,
    val reviews: List<ScalePublicationReview>
)

data class ScalePublicationReview(
    val id: Long,
    val reviewType: String,
    val decision: String,
    val reviewerId: Long,
    val reviewerRoleSnapshot: String,
    val scaleContentHash: String,
    val releaseFingerprint: String,
    val commentText: String?,
    val createdAt: LocalDateTime,
    val reviewerNameSnapshot: String? = null,
    val qualificationReference: String? = null,
    val evidenceReference: String? = null,
    val reviewScope: String? = null
)

data class ScaleGoldenCaseReadiness(
    val id: Long,
    val caseCode: String,
    val revisionNo: Int,
    val caseType: String,
    val currentContent: Boolean,
    val approved: Boolean,
    val latestRunPassed: Boolean
)

data class ScalePublicationReadiness(
    val scaleId: Long,
    val scaleContentHash: String,
    val releaseFingerprint: String,
    val ready: Boolean,
    val requiredCaseTypes: Set<String>,
    val cases: List<ScaleGoldenCaseReadiness>,
    val professionalReview: ScalePublicationReview?,
    val businessReview: ScalePublicationReview?,
    val blockers: List<String>
)
