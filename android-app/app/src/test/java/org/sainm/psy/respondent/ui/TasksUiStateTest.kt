package org.sainm.psy.respondent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyReportSummary

class TasksUiStateTest {
    @Test
    fun `pending filter preserves overdue tasks and excludes completed tasks`() {
        val state = TasksUiState(
            tasks = listOf(task(1, "IN_PROGRESS"), task(2, "OVERDUE"), task(3, "COMPLETED")),
            selectedFilter = TaskFilter.PENDING,
            isLoading = false
        )

        assertEquals(listOf(1L, 2L), state.visibleTasks.map { it.taskId })
    }

    @Test
    fun `overdue filter returns only overdue tasks`() {
        val state = TasksUiState(
            tasks = listOf(task(1, "IN_PROGRESS"), task(2, "OVERDUE"), task(3, "COMPLETED")),
            selectedFilter = TaskFilter.OVERDUE,
            isLoading = false
        )

        assertEquals(listOf(2L), state.visibleTasks.map { it.taskId })
    }

    @Test
    fun `completed task resolves only its own report`() {
        val state = TasksUiState(
            reports = listOf(report(reportId = 90, taskId = 3)),
            isLoading = false
        )

        assertEquals(90L, state.reportIdFor(3))
        assertNull(state.reportIdFor(4))
    }

    private fun task(taskId: Long, status: String) = MyAssessmentTask(
        taskId = taskId,
        taskName = "task-$taskId",
        scaleId = 10,
        scaleName = "scale",
        endTime = "2026-08-31T00:00:00Z",
        status = status
    )

    private fun report(reportId: Long, taskId: Long) = MyReportSummary(
        reportId = reportId,
        resultId = 80,
        taskId = taskId,
        taskName = "task-$taskId",
        scaleId = 10,
        scaleName = "scale",
        reportType = "PERSONAL",
        totalScore = 5.0,
        riskLevel = "LOW",
        createdAt = "2026-08-11T00:00:00Z"
    )
}
