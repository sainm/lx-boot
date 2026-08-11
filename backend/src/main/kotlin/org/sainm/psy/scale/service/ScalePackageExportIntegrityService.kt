package org.sainm.psy.scale.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.sainm.psy.scale.domain.ScaleDetail
import org.sainm.psy.scale.domain.ScaleGoldenCaseHistory
import org.sainm.psy.scale.domain.ScalePackageSnapshot
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.springframework.stereotype.Service

data class ScalePackageIntegrityPayload(
    val scaleContentHash: String,
    val releaseFingerprint: String,
    val scale: ScaleDetail,
    val scalePackage: ScalePackageSnapshot,
    val goldenCases: List<ScaleGoldenCaseHistory>,
    val publicationReviews: List<ScalePublicationReview>
)

@Service
class ScalePackageExportIntegrityService(
    private val objectMapper: ObjectMapper,
    private val fingerprintService: ScaleContentFingerprintService
) {
    fun calculate(
        scaleContentHash: String,
        releaseFingerprint: String,
        scale: ScaleDetail,
        scalePackage: ScalePackageSnapshot,
        goldenCases: List<ScaleGoldenCaseHistory>,
        publicationReviews: List<ScalePublicationReview>
    ): String {
        val payload = ScalePackageIntegrityPayload(scaleContentHash, releaseFingerprint, scale, scalePackage, goldenCases, publicationReviews)
        val canonicalJson = objectMapper.writer().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(payload)
        return fingerprintService.sha256(canonicalJson)
    }
}
