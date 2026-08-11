package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.audit.SecurityAuditService
import org.sainm.psy.common.exception.NotFoundBizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.security.TenantAccessPolicy
import org.sainm.psy.scale.api.ScalePackageExportDocument
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.repository.ScalePackageRepository
import org.sainm.psy.scale.repository.ScalePublicationRepository
import org.sainm.psy.scale.repository.ScaleRepository
import org.sainm.psy.visualization.service.VisualizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ScalePackageExportArtifact(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val exportId: String,
    val exportedAt: Instant,
    val scaleContentHash: String,
    val releaseFingerprint: String,
    val payloadHash: String,
    val schemaVersion: Int
)

@Service
class ScalePackageExportService(
    private val scaleRepository: ScaleRepository,
    private val packageRepository: ScalePackageRepository,
    private val publicationRepository: ScalePublicationRepository,
    private val visualizationService: VisualizationService,
    private val fingerprintService: ScaleContentFingerprintService,
    private val integrityService: ScalePackageExportIntegrityService,
    private val currentUserFacade: CurrentUserFacade,
    private val messages: LocalizedMessages,
    private val objectMapper: ObjectMapper,
    private val securityAuditService: SecurityAuditService,
    private val clock: Clock,
    private val tenantAccessPolicy: TenantAccessPolicy
) {
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    fun export(scaleId: Long): ScalePackageExportArtifact {
        val scale = requireOwnedScale(scaleId).copy(visualizationConfigs = visualizationService.findConfigs(scaleId))
        val scalePackage = packageRepository.find(scaleId)
        val cases = publicationRepository.findAllCases(scaleId)
        val runsByCase = publicationRepository.findAllRuns(scaleId).groupBy { it.goldenCaseId }
        val reviews = publicationRepository.findAllReviews(scaleId)
        val history = cases.map { goldenCase -> ScaleGoldenCaseHistory(goldenCase, runsByCase[goldenCase.id].orEmpty()) }
        val latestCases = cases.groupBy { it.caseCode }.values.map { revisions -> revisions.maxBy { it.revisionNo } }
        val scaleHash = fingerprintService.calculate(scale)
        val releaseFingerprint = fingerprintService.calculateReleaseFingerprint(scaleHash, latestCases)
        val payloadHash = integrityService.calculate(scaleHash, releaseFingerprint, scale, scalePackage, history, reviews)
        val exportId = UUID.randomUUID().toString()
        val exportedAt = Instant.now(clock)
        val document = ScalePackageExportDocument(
            exportId = exportId,
            exportedAt = exportedAt,
            exportedBy = currentUserFacade.requireCurrentUserId(),
            scaleContentHash = scaleHash,
            releaseFingerprint = releaseFingerprint,
            payloadHash = payloadHash,
            scale = scale,
            scalePackage = scalePackage,
            goldenCases = history,
            publicationReviews = reviews
        )
        val bytes = objectMapper.writerWithDefaultPrettyPrinter()
            .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsBytes(document)
        securityAuditService.recordScalePackageExported(
            scaleId = scaleId,
            exportId = exportId,
            scaleContentHash = scaleHash,
            releaseFingerprint = releaseFingerprint,
            schemaVersion = document.schemaVersion,
            caseRevisionCount = cases.size,
            runCount = runsByCase.values.sumOf { it.size },
            reviewCount = reviews.size
        )
        return ScalePackageExportArtifact(
            bytes = bytes,
            fileName = "${safeFileToken(scale.scaleCode)}-${safeFileToken(scale.versionNo ?: "version")}-scale-package-v${document.schemaVersion}.json",
            contentType = "application/vnd.psy-scale-package+json",
            exportId = exportId,
            exportedAt = exportedAt,
            scaleContentHash = scaleHash,
            releaseFingerprint = releaseFingerprint,
            payloadHash = payloadHash,
            schemaVersion = document.schemaVersion
        )
    }

    private fun requireOwnedScale(scaleId: Long): ScaleDetail {
        val scale = scaleRepository.findDetailById(scaleId)
            ?: throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        if (!tenantAccessPolicy.canAccess(scale.tenantId, "SCALE_PACKAGE", scaleId, "EXPORT")) {
            throw NotFoundBizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        return scale
    }

    private fun safeFileToken(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', '_', '-')
        .take(80)
        .ifEmpty { "scale" }
}
