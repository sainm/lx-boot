package org.sainm.psy.statistics.api

import java.time.LocalDate
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class GroupReportListQuery(
    val taskId: Long? = null,
    val groupId: Long? = null,
    val scaleId: Long? = null,
    val compareUserId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class GroupReportExportJobRequest(
    @field:Min(1) val taskId: Long,
    @field:Min(1) val groupId: Long,
    @field:Min(1) val scaleId: Long? = null,
    @field:Min(1) val compareUserId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:NotBlank val format: String = "PDF"
) {
    fun toQuery() = GroupReportListQuery(
        taskId = taskId,
        groupId = groupId,
        scaleId = scaleId,
        compareUserId = compareUserId,
        startDate = startDate,
        endDate = endDate,
        page = 1,
        size = 200
    )
}
