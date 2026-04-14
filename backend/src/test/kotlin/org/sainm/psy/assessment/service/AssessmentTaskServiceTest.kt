package org.sainm.psy.assessment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.CloseAssessmentTaskRequest
import org.sainm.psy.assessment.api.TaskAssignGroupsRequest
import org.sainm.psy.assessment.api.TaskAssignUsersRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.domain.OverdueTaskNotification
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.common.i18n.LocalizedMessages
import org.sainm.psy.notification.service.NotificationDispatchService
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AssessmentTaskServiceTest {

    @Mock private lateinit var assessmentTaskRepository: AssessmentTaskRepository
    @Mock private lateinit var answerSheetService: AnswerSheetService
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService
    @Mock private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var assessmentTaskService: AssessmentTaskService

    @BeforeEach
    fun setUp() {
        val messageSource = ReloadableResourceBundleMessageSource().apply {
            setBasenames("classpath:i18n/messages")
            setDefaultEncoding("UTF-8")
        }
        doAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Any?>>(0)
            callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus::class.java))
        }.`when`(transactionTemplate).execute<Any?>(org.mockito.ArgumentMatchers.any())
        assessmentTaskService = AssessmentTaskService(
            assessmentTaskRepository = assessmentTaskRepository,
            answerSheetService = answerSheetService,
            currentUserFacade = currentUserFacade,
            notificationDispatchService = notificationDispatchService,
            messages = LocalizedMessages(messageSource),
            transactionTemplate = transactionTemplate
        )
    }

    private val now = LocalDateTime.now()

    private fun makeDetail(
        id: Long = 10L,
        taskName: String = "Spring Survey",
        scaleId: Long = 2L,
        status: String = "DRAFT"
    ) = AssessmentTaskDetail(
        id = id,
        taskName = taskName,
        scaleId = scaleId,
        scaleName = "PHQ-9",
        scaleVersionNo = "v1",
        scaleVersionGroupId = 2L,
        taskMode = "SCREENING",
        anonymousFlag = false,
        allowSaveFlag = true,
        allowTimeoutSubmitFlag = false,
        allowRetakeFlag = false,
        startTime = now,
        endTime = now.plusDays(7),
        status = status,
        createdBy = 1L,
        createdAt = now,
        assignments = emptyList()
    )

    @Test
    fun `findPage throws when page is 0`() {
        assertThrows<IllegalArgumentException> {
            assessmentTaskService.findPage(TaskListQuery(page = 0, size = 20))
        }
    }

    @Test
    fun `findPage throws when size is out of range`() {
        assertThrows<IllegalArgumentException> {
            assessmentTaskService.findPage(TaskListQuery(page = 1, size = 0))
        }
        assertThrows<IllegalArgumentException> {
            assessmentTaskService.findPage(TaskListQuery(page = 1, size = 201))
        }
    }

    @Test
    fun `findPage returns wrapped PageResponse`() {
        val summary = AssessmentTaskSummary(
            id = 10L,
            taskName = "Spring Survey",
            scaleId = 2L,
            scaleName = "PHQ-9",
            scaleVersionNo = "v1",
            scaleVersionGroupId = 2L,
            taskMode = "SCREENING",
            anonymousFlag = false,
            startTime = now,
            endTime = now.plusDays(7),
            status = "DRAFT"
        )
        `when`(assessmentTaskRepository.findPage(TaskListQuery(page = 1, size = 20))).thenReturn(listOf(summary) to 1L)

        val result = assessmentTaskService.findPage(TaskListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
        assertEquals(1, result.page)
    }

    @Test
    fun `create throws when endTime is not after startTime`() {
        val request = CreateAssessmentTaskRequest(
            taskName = "Task",
            scaleId = 1L,
            taskMode = "SCREENING",
            startTime = now.plusDays(2),
            endTime = now.plusDays(1)
        )

        assertThrows<IllegalArgumentException> {
            assessmentTaskService.create(request)
        }
        verify(assessmentTaskRepository, never()).existsScaleById(org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun `create throws SCALE_NOT_FOUND when scale does not exist`() {
        val request = CreateAssessmentTaskRequest(
            taskName = "Task",
            scaleId = 99L,
            taskMode = "SCREENING",
            startTime = now,
            endTime = now.plusDays(7)
        )
        `when`(assessmentTaskRepository.existsScaleById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> { assessmentTaskService.create(request) }
        assertEquals("SCALE_NOT_FOUND", ex.code)
    }

    @Test
    fun `create returns id and DRAFT status on success`() {
        val request = CreateAssessmentTaskRequest(
            taskName = "Spring Survey",
            scaleId = 2L,
            taskMode = "SCREENING",
            startTime = now,
            endTime = now.plusDays(7)
        )
        `when`(assessmentTaskRepository.existsScaleById(2L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)
        `when`(assessmentTaskRepository.create(request, 1L)).thenReturn(10L)

        val result = assessmentTaskService.create(request)

        assertEquals(10L, result.id)
        assertEquals("DRAFT", result.status)
    }

    @Test
    fun `assignGroups throws TASK_NOT_FOUND when task does not exist`() {
        `when`(assessmentTaskRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            assessmentTaskService.assignGroups(99L, TaskAssignGroupsRequest(groupIds = listOf(1L, 2L)))
        }
        assertEquals("TASK_NOT_FOUND", ex.code)
    }

    @Test
    fun `assignGroups calls assignTargets with GROUP type on success`() {
        `when`(assessmentTaskRepository.existsById(10L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)

        assessmentTaskService.assignGroups(10L, TaskAssignGroupsRequest(groupIds = listOf(3L, 4L)))

        verify(assessmentTaskRepository).assignTargets(10L, "GROUP", listOf(3L, 4L), 1L)
    }

    @Test
    fun `assignUsers throws TASK_NOT_FOUND when task does not exist`() {
        `when`(assessmentTaskRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            assessmentTaskService.assignUsers(99L, TaskAssignUsersRequest(userIds = listOf(5L)))
        }
        assertEquals("TASK_NOT_FOUND", ex.code)
    }

    @Test
    fun `assignUsers calls assignTargets and notifies receivers on success`() {
        val detail = makeDetail()
        `when`(assessmentTaskRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)

        assessmentTaskService.assignUsers(10L, TaskAssignUsersRequest(userIds = listOf(5L, 6L)))

        verify(assessmentTaskRepository).assignTargets(10L, "USER", listOf(5L, 6L), 1L)
        verify(notificationDispatchService).notifyTaskAssigned(10L, "Spring Survey", 2L, detail.endTime, "DRAFT", listOf(5L, 6L))
    }

    @Test
    fun `processOverdueTasks delegates to repository and auto submit service`() {
        val scanTime = now.plusDays(1)
        `when`(assessmentTaskRepository.markOverdueTasks(scanTime)).thenReturn(3)
        `when`(answerSheetService.autoSubmitOverdueDrafts(scanTime)).thenReturn(2)
        `when`(assessmentTaskRepository.findTasksNeedingOverdueNotification(scanTime)).thenReturn(emptyList())

        val updatedCount = assessmentTaskService.processOverdueTasks(scanTime)

        assertEquals(3, updatedCount)
        verify(assessmentTaskRepository).markOverdueTasks(scanTime)
        verify(answerSheetService).autoSubmitOverdueDrafts(scanTime)
    }

    @Test
    fun `notifyOverdueTasks sends notifications and marks tasks as notified`() {
        val scanTime = now.plusDays(2)
        val overdueTask = OverdueTaskNotification(
            taskId = 21L,
            taskName = "Missed Survey",
            receiverUserIds = listOf(5L, 6L)
        )
        `when`(assessmentTaskRepository.findTasksNeedingOverdueNotification(scanTime)).thenReturn(listOf(overdueTask))

        val notifiedCount = assessmentTaskService.notifyOverdueTasks(scanTime)

        assertEquals(1, notifiedCount)
        verify(notificationDispatchService).notifyTaskOverdue(21L, "Missed Survey", listOf(5L, 6L))
        verify(assessmentTaskRepository).markOverdueNotificationSent(listOf(21L), scanTime)
    }

    @Test
    fun `closeTask closes draft task and returns updated detail`() {
        val openDetail = makeDetail(status = "DRAFT")
        val closedDetail = makeDetail(status = "CLOSED")
        `when`(assessmentTaskRepository.findDetailById(10L)).thenReturn(openDetail, closedDetail)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)
        `when`(assessmentTaskRepository.closeTask(10L, 1L, "abnormal task")).thenReturn(1)

        val result = assessmentTaskService.closeTask(10L, CloseAssessmentTaskRequest(reason = " abnormal task "))

        assertEquals("CLOSED", result.status)
        verify(assessmentTaskRepository).closeTask(10L, 1L, "abnormal task")
    }

    @Test
    fun `closeTask throws when task is already closed`() {
        `when`(assessmentTaskRepository.findDetailById(10L)).thenReturn(makeDetail(status = "CLOSED"))

        val ex = assertThrows<BizException> {
            assessmentTaskService.closeTask(10L, CloseAssessmentTaskRequest(reason = "duplicate close"))
        }

        assertEquals("TASK_ALREADY_CLOSED", ex.code)
        verify(assessmentTaskRepository, never()).closeTask(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }

    @Test
    fun `closeTask throws when task status is not closable`() {
        `when`(assessmentTaskRepository.findDetailById(10L)).thenReturn(makeDetail(status = "COMPLETED"))

        val ex = assertThrows<BizException> {
            assessmentTaskService.closeTask(10L, CloseAssessmentTaskRequest(reason = "cannot close completed"))
        }

        assertEquals("TASK_NOT_CLOSABLE", ex.code)
        verify(assessmentTaskRepository, never()).closeTask(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString()
        )
    }
}
