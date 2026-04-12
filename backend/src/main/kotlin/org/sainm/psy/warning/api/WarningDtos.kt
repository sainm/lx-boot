package org.sainm.psy.warning.api

import jakarta.validation.constraints.NotNull

data class WarningListQuery(
    val status: String? = null,
    val warningLevel: String? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class AssignWarningRequest(
    @field:NotNull(message = "{validation.assignee_user_id_required}")
    val assigneeUserId: Long
)
