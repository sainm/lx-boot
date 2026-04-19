package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.CloseAssessmentTaskRequest
import org.sainm.psy.assessment.api.CreateAssessmentTaskResponse
import org.sainm.psy.assessment.api.TaskAssignGroupsRequest
import org.sainm.psy.assessment.api.TaskAssignUsersRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@Service
class AssessmentTaskService(
    private val assessmentTaskRepository: AssessmentTaskRepository,
    private val answerSheetService: AnswerSheetService,
    private val currentUserFacade: CurrentUserFacade,
    private val notificationDispatchService: NotificationDispatchService,
    private val messages: LocalizedMessages,
    private val transactionTemplate: TransactionTemplate,
    private val schedulerLockService: SchedulerLockService? = null,
    private val psyMetrics: PsyMetrics? = null
) {

    fun findPage(query: TaskListQuery): PageResponse<AssessmentTaskSummary> {
        require(query.page > 0) { "page must be greater than 0" }
        require(query.size in 1..200) { "size must be between 1 and 200" }
        val (list, total) = assessmentTaskRepository.findPage(query)
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun create(request: CreateAssessmentTaskRequest): CreateAssessmentTaskResponse {
        require(request.endTime.isAfter(request.startTime)) { messages.get("error.end_time_after_start") }
        if (!assessmentTaskRepository.existsScaleById(request.scaleId)) {
            throw BizException("SCALE_NOT_FOUND", messages.get("error.scale_not_found"))
        }
        val userId = currentUserFacade.requireCurrentUserId()
        val taskId = assessmentTaskRepository.create(request, userId)
        return CreateAssessmentTaskResponse(id = taskId, status = "DRAFT")
    }

    fun findDetail(taskId: Long): AssessmentTaskDetail =
        assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))

    @Transactional
    fun assignGroups(taskId: Long, request: TaskAssignGroupsRequest) {
        if (!assessmentTaskRepository.existsById(taskId)) {
            throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        }
        val userId = currentUserFacade.requireCurrentUserId()
        assessmentTaskRepository.assignTargets(taskId, "GROUP", request.groupIds, userId)
    }

    @Transactional
    fun assignUsers(taskId: Long, request: TaskAssignUsersRequest) {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        val userId = currentUserFacade.requireCurrentUserId()
        assessmentTaskRepository.assignTargets(taskId, "USER", request.userIds, userId)
        notificationDispatchService.notifyTaskAssigned(
            taskId = detail.id,
            taskName = detail.taskName,
            scaleId = detail.scaleId,
            endTime = detail.endTime,
            status = detail.status,
            receiverUserIds = request.userIds
        )
    }

    fun findMyTasks(): List<MyAssessmentTask> {
        val currentUser = currentUserFacade.requireCurrentUser()
        return assessmentTaskRepository.findMyTasks(currentUser.userId, currentUser.groupId)
    }

    @Transactional
    fun closeTask(taskId: Long, request: CloseAssessmentTaskRequest): AssessmentTaskDetail {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        if (detail.status == "CLOSED") {
            throw BizException("TASK_ALREADY_CLOSED", messages.get("error.task_already_closed"))
        }
        if (detail.status !in setOf("DRAFT", "IN_PROGRESS", "OVERDUE")) {
            throw BizException("TASK_NOT_CLOSABLE", messages.get("error.task_not_closable", detail.status))
        }
        val userId = currentUserFacade.requireCurrentUserId()
        val updated = assessmentTaskRepository.closeTask(
            taskId = taskId,
            closedBy = userId,
            reason = request.reason.trim()
        )
        if (updated == 0) {
            throw BizException("TASK_NOT_CLOSABLE", messages.get("error.task_not_closable", detail.status))
        }
        return findDetail(taskId)
    }

    @Scheduled(fixedDelayString = "\${psy.assessment.task-overdue-scan-delay-ms:60000}")
    fun processOverdueTasks(): Int {
        val now = java.time.LocalDateTime.now()
        val lock = schedulerLockService ?: return processOverdueTasks(now)
        val jobName = "assessment.task-overdue"
        val result = lock.withLock("assessment:task-overdue", Duration.ofMinutes(2)) {
            psyMetrics?.recordSchedulerRun(jobName) { processOverdueTasks(now) }
                ?: processOverdueTasks(now)
        }
        if (result == null) {
            psyMetrics?.recordSchedulerSkipped(jobName)
        }
        return result ?: 0
    }

    fun processOverdueTasks(now: java.time.LocalDateTime): Int {
        return transactionTemplate.execute<Int> {
            val updatedCount = assessmentTaskRepository.markOverdueTasks(now)
            answerSheetService.autoSubmitOverdueDrafts(now)
            notifyOverdueTasks(now)
            updatedCount
        } ?: 0
    }

    @Transactional
    fun notifyOverdueTasks(now: java.time.LocalDateTime): Int {
        val tasks = assessmentTaskRepository.findTasksNeedingOverdueNotification(now)
        tasks.forEach { task ->
            notificationDispatchService.notifyTaskOverdue(
                taskId = task.taskId,
                taskName = task.taskName,
                receiverUserIds = task.receiverUserIds
            )
        }
        assessmentTaskRepository.markOverdueNotificationSent(tasks.map { it.taskId }, now)
        return tasks.size
    }
}
