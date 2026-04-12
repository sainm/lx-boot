package org.sainm.psy.assessment.api

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateAssessmentTaskRequest(
    @field:NotBlank(message = "{validation.task_name_required}")
    @field:Size(max = 255, message = "{validation.task_name_size}")
    val taskName: String,

    @field:NotNull(message = "{validation.scale_id_required}")
    val scaleId: Long,

    @field:NotBlank(message = "{validation.task_mode_required}")
    val taskMode: String,

    val anonymousFlag: Boolean = false,
    val allowSaveFlag: Boolean = true,
    val allowTimeoutSubmitFlag: Boolean = false,
    val allowRetakeFlag: Boolean = false,

    @field:NotNull(message = "{validation.start_time_required}")
    val startTime: LocalDateTime,

    @field:NotNull(message = "{validation.end_time_required}")
    @field:Future(message = "{validation.end_time_future}")
    val endTime: LocalDateTime
)

data class CreateAssessmentTaskResponse(
    val id: Long,
    val status: String
)

data class TaskListQuery(
    val taskName: String? = null,
    val status: String? = null,
    val page: Int = 1,
    val size: Int = 20
)

data class TaskAssignGroupsRequest(
    @field:NotEmpty(message = "{validation.group_ids_required}")
    val groupIds: List<Long>
)

data class TaskAssignUsersRequest(
    @field:NotEmpty(message = "{validation.user_ids_required}")
    val userIds: List<Long>
)

data class CloseAssessmentTaskRequest(
    @field:NotBlank(message = "{validation.close_reason_required}")
    @field:Size(max = 500, message = "{validation.close_reason_size}")
    val reason: String
)
