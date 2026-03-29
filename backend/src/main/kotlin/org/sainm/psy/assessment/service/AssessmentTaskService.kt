package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.CreateAssessmentTaskResponse
import org.sainm.psy.assessment.api.TaskAssignGroupsRequest
import org.sainm.psy.assessment.api.TaskAssignUsersRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AssessmentTaskService(
    private val assessmentTaskRepository: AssessmentTaskRepository,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService
) {

    fun findPage(query: TaskListQuery): PageResponse<AssessmentTaskSummary> {
        require(query.page > 0) { "page 必须大于 0" }
        require(query.size in 1..200) { "size 必须在 1 到 200 之间" }
        val (list, total) = assessmentTaskRepository.findPage(query)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun create(request: CreateAssessmentTaskRequest): CreateAssessmentTaskResponse {
        require(request.endTime.isAfter(request.startTime)) { "截止时间必须晚于开始时间" }
        if (!assessmentTaskRepository.existsScaleById(request.scaleId)) {
            throw BizException("SCALE_NOT_FOUND", "关联量表不存在")
        }
        val userId = currentUserFacade.requireCurrentUserId()
        val taskId = assessmentTaskRepository.create(request, userId)
        return CreateAssessmentTaskResponse(id = taskId, status = "DRAFT")
    }

    fun findDetail(taskId: Long): AssessmentTaskDetail =
        assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", "测评任务不存在")

    @Transactional
    fun assignGroups(taskId: Long, request: TaskAssignGroupsRequest) {
        if (!assessmentTaskRepository.existsById(taskId)) {
            throw BizException("TASK_NOT_FOUND", "测评任务不存在")
        }
        val userId = currentUserFacade.requireCurrentUserId()
        assessmentTaskRepository.assignTargets(taskId, "GROUP", request.groupIds, userId)
    }

    @Transactional
    fun assignUsers(taskId: Long, request: TaskAssignUsersRequest) {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", "测评任务不存在")
        val userId = currentUserFacade.requireCurrentUserId()
        assessmentTaskRepository.assignTargets(taskId, "USER", request.userIds, userId)
        notificationDispatchService.notifyUsers(
            notificationType = "TASK_ASSIGNED",
            title = "新的测评任务已分配",
            content = "任务《${detail.taskName}》已分配给你，请在截止时间前完成。",
            bizType = "TASK",
            bizId = detail.id,
            targetPath = "/my/tasks/${detail.id}",
            payloadJson = """{"taskId":${detail.id},"taskName":"${detail.taskName.replace("\"", "\\\"")}","scaleId":${detail.scaleId},"endTime":"${detail.endTime}","status":"${detail.status}"}""",
            receiverUserIds = request.userIds
        )
    }

    fun findMyTasks(): List<MyAssessmentTask> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return assessmentTaskRepository.findMyTasks(currentUser.userId, currentUser.groupId)
    }
}
