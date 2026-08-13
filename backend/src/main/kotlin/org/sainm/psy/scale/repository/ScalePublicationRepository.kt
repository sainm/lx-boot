package org.sainm.psy.scale.repository

import org.sainm.psy.scale.domain.ScaleGoldenCase
import org.sainm.psy.scale.domain.ScaleGoldenCaseRun
import org.sainm.psy.scale.domain.ScalePublicationReview
import org.sainm.psy.common.api.CursorPage
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.DataClassRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDateTime

@Repository
class ScalePublicationRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun findCaseHistoryPage(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScaleGoldenCase> =
        cursorPage(limit) { fetchLimit ->
            jdbc.query(
                """select c.id, c.scale_id, c.case_code, c.revision_no, c.case_type, c.source_reference,
                    c.scale_content_hash, c.case_content_hash, c.input_json::text input_json, c.expected_json::text expected_json,
                    c.created_by, c.created_at, c.approved_by, c.approved_at
                   from psy_scale_golden_case c
                  where c.scale_id=:scaleId
                    and c.tenant_id is not distinct from (select s.tenant_id from psy_scale s where s.id=:scaleId)
                    and (cast(:afterId as bigint) is null or c.id < cast(:afterId as bigint))
                  order by c.id desc
                  limit :fetchLimit""",
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("afterId", afterId)
                    .addValue("fetchLimit", fetchLimit),
                DataClassRowMapper(ScaleGoldenCase::class.java)
            )
        }

    fun findRunHistoryPage(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScaleGoldenCaseRun> =
        cursorPage(limit) { fetchLimit ->
            jdbc.query(
                """select r.id, r.golden_case_id, r.scale_content_hash, r.case_content_hash,
                    r.algorithm_code, r.algorithm_version, r.passed, r.actual_json::text actual_json,
                    r.differences_json::text differences_json, r.executed_by, r.executed_at
                   from psy_scale_golden_case_run r
                  where r.scale_id=:scaleId
                    and r.tenant_id is not distinct from (select s.tenant_id from psy_scale s where s.id=:scaleId)
                    and (cast(:afterId as bigint) is null or r.id < cast(:afterId as bigint))
                  order by r.id desc
                  limit :fetchLimit""",
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("afterId", afterId)
                    .addValue("fetchLimit", fetchLimit),
                DataClassRowMapper(ScaleGoldenCaseRun::class.java)
            )
        }

    fun findReviewHistoryPage(scaleId: Long, afterId: Long?, limit: Int): CursorPage<ScalePublicationReview> =
        cursorPage(limit) { fetchLimit ->
            jdbc.query(
                """select r.id, r.review_type, r.decision, r.reviewer_id, r.reviewer_role_snapshot,
                    r.scale_content_hash, r.release_fingerprint, r.comment_text, r.created_at,
                    r.reviewer_name_snapshot, r.qualification_reference, r.evidence_reference, r.review_scope
                   from psy_scale_publication_review r
                  where r.scale_id=:scaleId
                    and r.tenant_id is not distinct from (select s.tenant_id from psy_scale s where s.id=:scaleId)
                    and (cast(:afterId as bigint) is null or r.id < cast(:afterId as bigint))
                  order by r.id desc
                  limit :fetchLimit""",
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId)
                    .addValue("afterId", afterId)
                    .addValue("fetchLimit", fetchLimit),
                DataClassRowMapper(ScalePublicationReview::class.java)
            )
        }

    fun saveCaseRevision(
        scaleId: Long,
        caseCode: String,
        caseType: String,
        sourceReference: String,
        scaleContentHash: String,
        caseContentHash: String,
        inputJson: String,
        expectedJson: String,
        userId: Long
    ): ScaleGoldenCase {
        jdbc.queryForObject("select id from psy_scale where id=:scaleId for update", mapOf("scaleId" to scaleId), Long::class.java)
        findLatestCase(scaleId, caseCode)?.takeIf {
            it.scaleContentHash == scaleContentHash && it.caseContentHash == caseContentHash
        }?.let { return it }
        val now = Timestamp.valueOf(LocalDateTime.now(clock))
        val keyHolder = GeneratedKeyHolder()
        jdbc.update(
            """insert into psy_scale_golden_case (
                tenant_id, scale_id, case_code, revision_no, case_type, source_reference,
                scale_content_hash, case_content_hash, input_json, expected_json, created_by, created_at
            ) select tenant_id, id, :caseCode,
                coalesce((select max(c.revision_no) + 1 from psy_scale_golden_case c where c.scale_id=:scaleId and c.case_code=:caseCode), 1),
                :caseType, :sourceReference, :scaleContentHash, :caseContentHash,
                cast(:inputJson as jsonb), cast(:expectedJson as jsonb), :userId, :now
              from psy_scale where id=:scaleId""",
            MapSqlParameterSource()
                .addValue("scaleId", scaleId).addValue("caseCode", caseCode).addValue("caseType", caseType)
                .addValue("sourceReference", sourceReference).addValue("scaleContentHash", scaleContentHash)
                .addValue("caseContentHash", caseContentHash).addValue("inputJson", inputJson)
                .addValue("expectedJson", expectedJson).addValue("userId", userId).addValue("now", now),
            keyHolder,
            arrayOf("id")
        )
        return findCase(requireNotNull(keyHolder.key).toLong()) ?: error("failed to create Golden Case")
    }

    fun findLatestCases(scaleId: Long): List<ScaleGoldenCase> = jdbc.query(
        """select distinct on (case_code) id, scale_id, case_code, revision_no, case_type, source_reference,
            scale_content_hash, case_content_hash, input_json::text input_json, expected_json::text expected_json,
            created_by, created_at, approved_by, approved_at
           from psy_scale_golden_case where scale_id=:scaleId
           order by case_code, revision_no desc""",
        mapOf("scaleId" to scaleId),
        DataClassRowMapper(ScaleGoldenCase::class.java)
    )

    fun findAllCases(scaleId: Long): List<ScaleGoldenCase> = jdbc.query(
        """select id, scale_id, case_code, revision_no, case_type, source_reference,
            scale_content_hash, case_content_hash, input_json::text input_json, expected_json::text expected_json,
            created_by, created_at, approved_by, approved_at
           from psy_scale_golden_case where scale_id=:scaleId
           order by case_code, revision_no desc, id desc""",
        mapOf("scaleId" to scaleId),
        DataClassRowMapper(ScaleGoldenCase::class.java)
    )

    fun findLatestCase(scaleId: Long, caseCode: String): ScaleGoldenCase? = jdbc.query(
        """select id, scale_id, case_code, revision_no, case_type, source_reference,
            scale_content_hash, case_content_hash, input_json::text input_json, expected_json::text expected_json,
            created_by, created_at, approved_by, approved_at
           from psy_scale_golden_case where scale_id=:scaleId and case_code=:caseCode
           order by revision_no desc limit 1""",
        mapOf("scaleId" to scaleId, "caseCode" to caseCode),
        DataClassRowMapper(ScaleGoldenCase::class.java)
    ).firstOrNull()

    fun findCase(id: Long): ScaleGoldenCase? = jdbc.query(
        """select id, scale_id, case_code, revision_no, case_type, source_reference,
            scale_content_hash, case_content_hash, input_json::text input_json, expected_json::text expected_json,
            created_by, created_at, approved_by, approved_at
           from psy_scale_golden_case where id=:id""",
        mapOf("id" to id),
        DataClassRowMapper(ScaleGoldenCase::class.java)
    ).firstOrNull()

    fun approveCase(caseId: Long, userId: Long): Boolean = jdbc.update(
        """update psy_scale_golden_case set approved_by=:userId, approved_at=:now
           where id=:caseId and approved_by is null""",
        mapOf("caseId" to caseId, "userId" to userId, "now" to Timestamp.valueOf(LocalDateTime.now(clock)))
    ) == 1

    fun saveRun(
        case: ScaleGoldenCase,
        algorithmCode: String?,
        algorithmVersion: String?,
        passed: Boolean,
        actualJson: String,
        differencesJson: String,
        userId: Long
    ): ScaleGoldenCaseRun {
        val keyHolder = GeneratedKeyHolder()
        jdbc.update(
            """insert into psy_scale_golden_case_run (
                tenant_id, scale_id, golden_case_id, scale_content_hash, case_content_hash,
                algorithm_code, algorithm_version, passed, actual_json, differences_json, executed_by, executed_at
            ) select tenant_id, id, :caseId, :scaleContentHash, :caseContentHash,
                :algorithmCode, :algorithmVersion, :passed, cast(:actualJson as jsonb),
                cast(:differencesJson as jsonb), :userId, :now
              from psy_scale where id=:scaleId""",
            MapSqlParameterSource()
                .addValue("scaleId", case.scaleId).addValue("caseId", case.id)
                .addValue("scaleContentHash", case.scaleContentHash).addValue("caseContentHash", case.caseContentHash)
                .addValue("algorithmCode", algorithmCode).addValue("algorithmVersion", algorithmVersion)
                .addValue("passed", passed).addValue("actualJson", actualJson)
                .addValue("differencesJson", differencesJson).addValue("userId", userId)
                .addValue("now", Timestamp.valueOf(LocalDateTime.now(clock))),
            keyHolder,
            arrayOf("id")
        )
        return findRun(requireNotNull(keyHolder.key).toLong()) ?: error("failed to create Golden Case run")
    }

    fun findLatestRun(caseId: Long): ScaleGoldenCaseRun? = jdbc.query(
        """select id, golden_case_id, scale_content_hash, case_content_hash,
            algorithm_code, algorithm_version, passed, actual_json::text actual_json,
            differences_json::text differences_json, executed_by, executed_at
           from psy_scale_golden_case_run where golden_case_id=:caseId order by executed_at desc, id desc limit 1""",
        mapOf("caseId" to caseId),
        DataClassRowMapper(ScaleGoldenCaseRun::class.java)
    ).firstOrNull()

    fun findAllRuns(scaleId: Long): List<ScaleGoldenCaseRun> = jdbc.query(
        """select id, golden_case_id, scale_content_hash, case_content_hash,
            algorithm_code, algorithm_version, passed, actual_json::text actual_json,
            differences_json::text differences_json, executed_by, executed_at
           from psy_scale_golden_case_run where scale_id=:scaleId
           order by executed_at desc, id desc""",
        mapOf("scaleId" to scaleId),
        DataClassRowMapper(ScaleGoldenCaseRun::class.java)
    )

    private fun findRun(id: Long): ScaleGoldenCaseRun? = jdbc.query(
        """select id, golden_case_id, scale_content_hash, case_content_hash,
            algorithm_code, algorithm_version, passed, actual_json::text actual_json,
            differences_json::text differences_json, executed_by, executed_at
           from psy_scale_golden_case_run where id=:id""",
        mapOf("id" to id),
        DataClassRowMapper(ScaleGoldenCaseRun::class.java)
    ).firstOrNull()

    fun saveReview(
        scaleId: Long,
        reviewType: String,
        decision: String,
        reviewerId: Long,
        reviewerRole: String,
        reviewerName: String,
        scaleContentHash: String,
        releaseFingerprint: String,
        reviewToken: String,
        comment: String?,
        qualificationReference: String?,
        evidenceReference: String?,
        reviewScope: String?
    ): ScalePublicationReview {
        val keyHolder = GeneratedKeyHolder()
        try {
            jdbc.update(
                """insert into psy_scale_publication_review (
                    tenant_id, scale_id, review_type, decision, reviewer_id, reviewer_role_snapshot,
                    reviewer_name_snapshot, scale_content_hash, release_fingerprint, review_token,
                    comment_text, qualification_reference, evidence_reference, review_scope, created_at
                ) select tenant_id, id, :reviewType, :decision, :reviewerId, :reviewerRole,
                    :reviewerName, :scaleContentHash, :releaseFingerprint, :reviewToken,
                    :comment, :qualificationReference, :evidenceReference, :reviewScope, :now
                  from psy_scale where id=:scaleId""",
                MapSqlParameterSource()
                    .addValue("scaleId", scaleId).addValue("reviewType", reviewType).addValue("decision", decision)
                    .addValue("reviewerId", reviewerId).addValue("reviewerRole", reviewerRole)
                    .addValue("reviewerName", reviewerName)
                    .addValue("scaleContentHash", scaleContentHash).addValue("releaseFingerprint", releaseFingerprint)
                    .addValue("reviewToken", reviewToken).addValue("comment", comment)
                    .addValue("qualificationReference", qualificationReference)
                    .addValue("evidenceReference", evidenceReference).addValue("reviewScope", reviewScope)
                    .addValue("now", Timestamp.valueOf(LocalDateTime.now(clock))),
                keyHolder,
                arrayOf("id")
            )
        } catch (_: DuplicateKeyException) {
            return findReviewByToken(scaleId, reviewType, reviewToken)
                ?: throw IllegalStateException("publication review token conflict")
        }
        return findReview(requireNotNull(keyHolder.key).toLong()) ?: error("failed to create publication review")
    }

    fun findLatestReviews(scaleId: Long, releaseFingerprint: String): Map<String, ScalePublicationReview> = jdbc.query(
        """select distinct on (review_type) id, review_type, decision, reviewer_id, reviewer_role_snapshot,
            scale_content_hash, release_fingerprint, comment_text, created_at, reviewer_name_snapshot,
            qualification_reference, evidence_reference, review_scope
           from psy_scale_publication_review
           where scale_id=:scaleId and release_fingerprint=:releaseFingerprint
           order by review_type, created_at desc, id desc""",
        mapOf("scaleId" to scaleId, "releaseFingerprint" to releaseFingerprint),
        DataClassRowMapper(ScalePublicationReview::class.java)
    ).associateBy { it.reviewType }

    fun findAllReviews(scaleId: Long): List<ScalePublicationReview> = jdbc.query(
        """select id, review_type, decision, reviewer_id, reviewer_role_snapshot,
            scale_content_hash, release_fingerprint, comment_text, created_at, reviewer_name_snapshot,
            qualification_reference, evidence_reference, review_scope
           from psy_scale_publication_review where scale_id=:scaleId
           order by created_at desc, id desc""",
        mapOf("scaleId" to scaleId),
        DataClassRowMapper(ScalePublicationReview::class.java)
    )

    private fun findReviewByToken(scaleId: Long, reviewType: String, token: String): ScalePublicationReview? = jdbc.query(
        """select id, review_type, decision, reviewer_id, reviewer_role_snapshot,
            scale_content_hash, release_fingerprint, comment_text, created_at, reviewer_name_snapshot,
            qualification_reference, evidence_reference, review_scope
           from psy_scale_publication_review where scale_id=:scaleId and review_type=:reviewType and review_token=:token""",
        mapOf("scaleId" to scaleId, "reviewType" to reviewType, "token" to token),
        DataClassRowMapper(ScalePublicationReview::class.java)
    ).firstOrNull()

    private fun findReview(id: Long): ScalePublicationReview? = jdbc.query(
        """select id, review_type, decision, reviewer_id, reviewer_role_snapshot,
            scale_content_hash, release_fingerprint, comment_text, created_at, reviewer_name_snapshot,
            qualification_reference, evidence_reference, review_scope
           from psy_scale_publication_review where id=:id""",
        mapOf("id" to id),
        DataClassRowMapper(ScalePublicationReview::class.java)
    ).firstOrNull()

    private fun <T> cursorPage(limit: Int, query: (fetchLimit: Int) -> List<T>): CursorPage<T> {
        val normalizedLimit = limit.coerceIn(1, MAX_HISTORY_PAGE_SIZE)
        val rows = query(normalizedLimit + 1)
        val hasNext = rows.size > normalizedLimit
        val page = rows.take(normalizedLimit)
        val nextCursor = if (hasNext) {
            page.lastOrNull()?.let { row ->
                when (row) {
                    is ScaleGoldenCase -> row.id
                    is ScaleGoldenCaseRun -> row.id
                    is ScalePublicationReview -> row.id
                    else -> null
                }
            }
        } else {
            null
        }
        return CursorPage(page, nextCursor, normalizedLimit)
    }

    companion object {
        const val MAX_HISTORY_PAGE_SIZE = 100
    }
}
