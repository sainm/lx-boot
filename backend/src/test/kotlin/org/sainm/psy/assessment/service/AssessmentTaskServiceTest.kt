package org.sainm.psy.assessment.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.sainm.psy.assessment.api.CreateAssessmentTaskRequest
import org.sainm.psy.assessment.api.TaskAssignGroupsRequest
import org.sainm.psy.assessment.api.TaskAssignUsersRequest
import org.sainm.psy.assessment.api.TaskListQuery
import org.sainm.psy.assessment.domain.AssessmentTaskDetail
import org.sainm.psy.assessment.domain.AssessmentTaskSummary
import org.sainm.psy.assessment.repository.AssessmentTaskRepository
import org.sainm.psy.auth.CurrentUser
import org.sainm.psy.auth.CurrentUserFacade
import org.sainm.psy.common.exception.BizException
import org.sainm.psy.notification.service.NotificationDispatchService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class AssessmentTaskServiceTest {

    @Mock private lateinit var assessmentTaskRepository: AssessmentTaskRepository
    @Mock private lateinit var currentUserFacade: CurrentUserFacade
    @Mock private lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    private lateinit var assessmentTaskService: AssessmentTaskService

    private val mockUser = CurrentUser(
        userId = 1L,
        username = "admin01",
        displayName = "Admin",
        tenantId = 1L,
        groupId = null,
        roles = setOf("ASSESSMENT_ADMIN"),
        permissions = emptySet()
    )

    private val now = LocalDateTime.now()

    private fun makeDetail(
        id: Long = 10L,
        taskName: String = "春季普查",
        scaleId: Long = 2L,
        status: String = "DRAFT"
    ) = AssessmentTaskDetail(
        id = id,
        taskName = taskName,
        scaleId = scaleId,
        scaleName = "PHQ-9",
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

    // ── findPage ──────────────────────────────────────────────────────────────

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
            id = 10L, taskName = "春季普查", scaleId = 2L, scaleName = "PHQ-9",
            taskMode = "SCREENING", anonymousFlag = false,
            startTime = now, endTime = now.plusDays(7), status = "DRAFT"
        )
        `when`(assessmentTaskRepository.findPage(TaskListQuery(page = 1, size = 20)))
            .thenReturn(listOf(summary) to 1L)

        val result = assessmentTaskService.findPage(TaskListQuery(page = 1, size = 20))

        assertEquals(1, result.list.size)
        assertEquals(1L, result.total)
        assertEquals(1, result.page)
    }

    // ── create ────────────────────────────────────────────────────────────────

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
        verify(assessmentTaskRepository, never()).create(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong()
        )
    }

    @Test
    fun `create returns id and DRAFT status on success`() {
        val request = CreateAssessmentTaskRequest(
            taskName = "春季普查",
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

    // ── assignGroups ──────────────────────────────────────────────────────────

    @Test
    fun `assignGroups throws TASK_NOT_FOUND when task does not exist`() {
        `when`(assessmentTaskRepository.existsById(99L)).thenReturn(false)

        val ex = assertThrows<BizException> {
            assessmentTaskService.assignGroups(99L, TaskAssignGroupsRequest(groupIds = listOf(1L, 2L)))
        }
        assertEquals("TASK_NOT_FOUND", ex.code)
        verify(assessmentTaskRepository, never()).assignTargets(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyLong()
        )
    }

    @Test
    fun `assignGroups calls assignTargets with GROUP type on success`() {
        `when`(assessmentTaskRepository.existsById(10L)).thenReturn(true)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)

        assessmentTaskService.assignGroups(10L, TaskAssignGroupsRequest(groupIds = listOf(3L, 4L)))

        verify(assessmentTaskRepository).assignTargets(10L, "GROUP", listOf(3L, 4L), 1L)
    }

    // ── assignUsers ───────────────────────────────────────────────────────────

    @Test
    fun `assignUsers throws TASK_NOT_FOUND when task does not exist`() {
        `when`(assessmentTaskRepository.findDetailById(99L)).thenReturn(null)

        val ex = assertThrows<BizException> {
            assessmentTaskService.assignUsers(99L, TaskAssignUsersRequest(userIds = listOf(5L)))
        }
        assertEquals("TASK_NOT_FOUND", ex.code)
        verify(notificationDispatchService, never()).notifyUsers(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyList()
        )
    }

    @Test
    fun `assignUsers calls assignTargets and notifies receivers on success`() {
        val detail = makeDetail(id = 10L, taskName = "春季普查", scaleId = 2L)
        `when`(assessmentTaskRepository.findDetailById(10L)).thenReturn(detail)
        `when`(currentUserFacade.requireCurrentUserId()).thenReturn(1L)

        assessmentTaskService.assignUsers(10L, TaskAssignUsersRequest(userIds = listOf(5L, 6L)))

        val expectedPayloadJson =
            """{"taskId":10,"taskName":"春季普查","scaleId":2,"endTime":"${detail.endTime}","status":"DRAFT"}"""

        verify(assessmentTaskRepository).assignTargets(10L, "USER", listOf(5L, 6L), 1L)
        verify(notificationDispatchService).notifyUsers(
            notificationType = "TASK_ASSIGNED",
            title = "新的测评任务已分配",
            content = "任务《春季普查》已分配给你，请在截止时间前完成。",
            bizType = "TASK",
            bizId = 10L,
            targetPath = "/my/tasks/10",
            payloadJson = expectedPayloadJson,
            receiverUserIds = listOf(5L, 6L)
        )
    }
}
