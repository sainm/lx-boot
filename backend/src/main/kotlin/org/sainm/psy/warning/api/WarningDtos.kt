package org.sainm.psy.warning.api

import jakarta.validation.constraints.NotNull

data class WarningListQuery(
    val status: String? = null,
    val warningLevel: String? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class AssignWarningRequest(
    @field:NotNull(message = "责任人不能为空")
    val assigneeUserId: Long
)
