package org.sainm.psy.scale.api

import jakarta.validation.constraints.NotBlank
import org.sainm.psy.common.api.PageResponse
import java.time.LocalDateTime

data class ParseScaleImportResponse(
    val importId: Long,
    val fileName: String,
    val status: String,
    val summary: ScaleImportSummaryResponse,
    val errorCount: Int,
    val warningCount: Int,
    val errors: List<ScaleImportIssueResponse>,
    val warnings: List<ScaleImportIssueResponse>
)

data class ConfirmScaleImportRequest(
    @field:NotBlank(message = "{validation.confirm_remark_required}")
    val confirmRemark: String
)

data class ConfirmScaleImportResponse(
    val importId: Long,
    val status: String,
    val scaleId: Long,
    val createdDimensionCount: Int,
    val createdQuestionCount: Int,
    val createdOptionCount: Int,
    val createdResultRuleCount: Int
)

data class ScaleImportDetailResponse(
    val id: Long,
    val fileName: String,
    val importMode: String,
    val draftFlag: Boolean,
    val status: String,
    val operatorUserId: Long,
    val createdScaleId: Long?,
    val parsedAt: LocalDateTime?,
    val confirmedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val summary: ScaleImportSummaryResponse,
    val errors: List<ScaleImportIssueResponse>,
    val warnings: List<ScaleImportIssueResponse>
)

data class ScaleImportListItemResponse(
    val id: Long,
    val fileName: String,
    val importMode: String,
    val draftFlag: Boolean,
    val status: String,
    val errorCount: Int,
    val warningCount: Int,
    val createdScaleId: Long?,
    val operatorUserId: Long,
    val createdAt: LocalDateTime,
    val finishedAt: LocalDateTime?
)

data class ScaleImportSummaryResponse(
    val scaleCode: String? = null,
    val scaleName: String? = null,
    val dimensionCount: Int = 0,
    val questionCount: Int = 0,
    val optionCount: Int = 0,
    val resultRuleCount: Int = 0
)

data class ScaleImportIssueResponse(
    val severity: String,
    val sheetName: String,
    val rowNo: Int?,
    val columnName: String?,
    val errorCode: String,
    val message: String
)

data class ScaleImportListQuery(
    val fileName: String? = null,
    val status: String? = null,
    val page: Int = 1,
    val size: Int = 20
)

typealias ScaleImportPageResponse = PageResponse<ScaleImportListItemResponse>
