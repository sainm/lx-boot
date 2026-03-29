package org.sainm.psy.assessment.api

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateAssessmentTaskRequest(
    @field:NotBlank(message = "任务名称不能为空")
    @field:Size(max = 255, message = "任务名称长度不能超过 255")
    val taskName: String,

    @field:NotNull(message = "量表不能为空")
    val scaleId: Long,

    @field:NotBlank(message = "任务模式不能为空")
    val taskMode: String,

    val anonymousFlag: Boolean = false,
    val allowSaveFlag: Boolean = true,
    val allowTimeoutSubmitFlag: Boolean = false,
    val allowRetakeFlag: Boolean = false,

    @field:NotNull(message = "开始时间不能为空")
    val startTime: LocalDateTime,

    @field:NotNull(message = "截止时间不能为空")
    @field:Future(message = "截止时间必须是未来时间")
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
    @field:NotEmpty(message = "组列表不能为空")
    val groupIds: List<Long>
)

data class TaskAssignUsersRequest(
    @field:NotEmpty(message = "用户列表不能为空")
    val userIds: List<Long>
)
