package org.sainm.psy.assessment.api

import jakarta.validation.Valid
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.sainm.psy.assessment.service.AssessmentTaskService
import org.sainm.psy.common.api.ApiResponse
import org.sainm.psy.common.api.PageResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AssessmentTaskController(
    private val assessmentTaskService: AssessmentTaskService
) {

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findPage(
        @RequestParam(required = false) taskName: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<AssessmentTaskSummary>> =
        ApiResponse.ok(
            assessmentTaskService.findPage(
                TaskListQuery(
                    taskName = taskName,
                    status = status,
                    page = page,
                    size = size
                )
            )
        )

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun create(@Valid @RequestBody request: CreateAssessmentTaskRequest): ApiResponse<CreateAssessmentTaskResponse> =
        ApiResponse.ok(assessmentTaskService.create(request))

    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun findDetail(@PathVariable id: Long): ApiResponse<AssessmentTaskDetail> =
        ApiResponse.ok(assessmentTaskService.findDetail(id))

    @PostMapping("/tasks/{id}/close")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun closeTask(
        @PathVariable id: Long,
        @Valid @RequestBody request: CloseAssessmentTaskRequest
    ): ApiResponse<AssessmentTaskDetail> =
        ApiResponse.ok(assessmentTaskService.closeTask(id, request))

    @PostMapping("/tasks/{id}/assign-groups")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun assignGroups(
        @PathVariable id: Long,
        @Valid @RequestBody request: TaskAssignGroupsRequest
    ): ApiResponse<Map<String, Any>> {
        assessmentTaskService.assignGroups(id, request)
        return ApiResponse.ok(mapOf("success" to true))
    }

    @PostMapping("/tasks/{id}/assign-users")
    @PreAuthorize("hasAnyRole('ASSESSMENT_ADMIN', 'ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')")
    fun assignUsers(
        @PathVariable id: Long,
        @Valid @RequestBody request: TaskAssignUsersRequest
    ): ApiResponse<Map<String, Any>> {
        assessmentTaskService.assignUsers(id, request)
        return ApiResponse.ok(mapOf("success" to true))
    }

    @GetMapping("/my/tasks")
    fun findMyTasks(): ApiResponse<List<MyAssessmentTask>> =
        ApiResponse.ok(assessmentTaskService.findMyTasks())
}
