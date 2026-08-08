package org.sainm.psy.assessment.service

import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.CloseAssessmentTaskRequest
import org.sainm.psy.assessment.api.CreateAssessmentTaskResponse
import org.sainm.psy.assessment.api.TaskAssignGroupsRequest
import org.sainm.psy.assessment.api.TaskAssignUsersRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.api.UpdateAssessmentTaskRequest
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.MyAssessmentTask
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.auth.core.domain.UserPrincipal
import org.sainm.auth.security.support.CurrentUserFacade
import org.sainm.psy.common.api.PageResponse
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.common.monitoring.PsyMetrics
import org.sainm.psy.common.scheduler.SchedulerLockService
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.beans.factory.annotation.Value
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
    private val psyMetrics: PsyMetrics? = null,
    @Value("\${psy.assessment.anonymous-identity-secret:local-dev-only-change-this-anonymous-secret}")
    private val anonymousIdentitySecret: String = "local-dev-only-change-this-anonymous-secret"
) {

    fun findPage(query: TaskListQuery): PageResponse<AssessmentTaskSummary> {
        require(query.page > 0) { messages.get("validation.page_positive") }
        require(query.size in 1..200) { messages.get("validation.size_range") }
        val currentUser = currentUserFacade.requireCurrentUser()
        val (list, total) = assessmentTaskRepository.findPage(query, currentUser.scopedTenantId())
        return PageResponse(list = list, page = query.page, size = query.size, total = total)
    }

    @Transactional
    fun create(request: CreateAssessmentTaskRequest): CreateAssessmentTaskResponse {
        require(request.endTime.isAfter(request.startTime)) { messages.get("error.end_time_after_start") }
        if (!assessmentTaskRepository.existsScaleById(request.scaleId)) {
            throw BizException("SCALE_NOT_PUBLISHED", messages.get("error.scale_not_published"))
        }
        if (request.anonymousFlag && !assessmentTaskRepository.isScaleAnonymousSupported(request.scaleId)) {
            throw BizException("SCALE_ANONYMOUS_UNSUPPORTED", messages.get("error.scale_anonymous_unsupported"))
        }
        val userId = currentUserFacade.requireCurrentUserId()
        val taskId = assessmentTaskRepository.create(request, userId)
        return CreateAssessmentTaskResponse(id = taskId, status = "DRAFT")
    }

    fun findDetail(taskId: Long): AssessmentTaskDetail =
        assessmentTaskRepository.findDetailById(taskId)
            ?.also { requireTaskAccess(taskId) }
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))

    @Transactional
    fun update(taskId: Long, request: UpdateAssessmentTaskRequest): AssessmentTaskDetail {
        require(request.endTime.isAfter(request.startTime)) { messages.get("error.end_time_after_start") }
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        requireTaskAccess(taskId)
        if (detail.status != "DRAFT") {
            throw BizException("TASK_NOT_EDITABLE", messages.get("error.task_not_editable", detail.status))
        }
        if (!assessmentTaskRepository.existsScaleById(request.scaleId)) {
            throw BizException("SCALE_NOT_PUBLISHED", messages.get("error.scale_not_published"))
        }
        if (request.anonymousFlag && !assessmentTaskRepository.isScaleAnonymousSupported(request.scaleId)) {
            throw BizException("SCALE_ANONYMOUS_UNSUPPORTED", messages.get("error.scale_anonymous_unsupported"))
        }
        val updated = assessmentTaskRepository.updateDraft(taskId, request)
        if (updated == 0) {
            throw BizException("TASK_NOT_EDITABLE", messages.get("error.task_not_editable", detail.status))
        }
        return findDetail(taskId)
    }

    @Transactional
    fun delete(taskId: Long) {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        requireTaskAccess(taskId)
        if (detail.status != "DRAFT") {
            throw BizException("TASK_NOT_DELETABLE", messages.get("error.task_not_deletable", detail.status))
        }
        val deleted = assessmentTaskRepository.deleteDraft(taskId)
        if (deleted == 0) {
            throw BizException("TASK_NOT_DELETABLE", messages.get("error.task_not_deletable", detail.status))
        }
    }

    @Transactional
    fun assignGroups(taskId: Long, request: TaskAssignGroupsRequest) {
        if (!assessmentTaskRepository.existsById(taskId)) {
            throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        }
        val currentUser = currentUserFacade.requireCurrentUser()
        requireTaskAccess(taskId, currentUser)
        if (!assessmentTaskRepository.areGroupsInTenant(request.groupIds, currentUser.scopedTenantId())) {
            throw BizException("TASK_TARGET_OUT_OF_SCOPE", messages.get("error.task_target_out_of_scope"))
        }
        val userId = currentUser.userId
        assessmentTaskRepository.assignTargets(taskId, "GROUP", request.groupIds, userId)
        val receiverUserIds = assessmentTaskRepository.findActiveUserIdsByGroupIds(request.groupIds)
        if (receiverUserIds.isNotEmpty()) {
            val detail = assessmentTaskRepository.findDetailById(taskId)
                ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
            notificationDispatchService.notifyTaskAssigned(
                taskId = detail.id,
                taskName = detail.taskName,
                scaleId = detail.scaleId,
                endTime = detail.endTime,
                status = detail.status,
                receiverUserIds = receiverUserIds
            )
        }
    }

    @Transactional
    fun assignUsers(taskId: Long, request: TaskAssignUsersRequest) {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        val currentUser = currentUserFacade.requireCurrentUser()
        requireTaskAccess(taskId, currentUser)
        if (!assessmentTaskRepository.areUsersInTenant(request.userIds, currentUser.scopedTenantId())) {
            throw BizException("TASK_TARGET_OUT_OF_SCOPE", messages.get("error.task_target_out_of_scope"))
        }
        val userId = currentUser.userId
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
        return assessmentTaskRepository.findMyTasks(currentUser.userId, currentUser.groupId).map { task ->
            if (
                task.anonymousFlag &&
                assessmentTaskRepository.hasAnonymousSubmitted(
                    task.taskId,
                    AnonymousAssessmentIdentity.token(anonymousIdentitySecret, task.taskId, currentUser.userId)
                )
            ) task.copy(status = "COMPLETED") else task
        }
    }

    @Transactional
    fun closeTask(taskId: Long, request: CloseAssessmentTaskRequest): AssessmentTaskDetail {
        val detail = assessmentTaskRepository.findDetailById(taskId)
            ?: throw BizException("TASK_NOT_FOUND", messages.get("error.task_not_found"))
        requireTaskAccess(taskId)
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

    private fun requireTaskAccess(taskId: Long, currentUser: UserPrincipal = currentUserFacade.requireCurrentUser()) {
        if (currentUser.isGlobalAdmin()) return
        val taskTenantId = assessmentTaskRepository.findTaskTenantId(taskId)
        if (currentUser.tenantId != null && currentUser.tenantId == taskTenantId) return
        throw BizException("TASK_FORBIDDEN", messages.get("error.task_management_forbidden"))
    }

    private fun UserPrincipal.isGlobalAdmin(): Boolean =
        tenantId == null && roles.any { it in GLOBAL_ADMIN_ROLES }

    private fun UserPrincipal.scopedTenantId(): Long? = if (isGlobalAdmin()) null else tenantId

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

    companion object {
        private val GLOBAL_ADMIN_ROLES = setOf("ADMIN", "SYS_ADMIN", "SUPER_ADMIN")
    }
}
