package org.sainm.psy.respondent.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.sainm.psy.respondent.data.RespondentTasksDataSource
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyReportSummary

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load publishes tasks and reports`() {
        val viewModel = TasksViewModel(
            FakeTasksDataSource(
                tasksResult = Result.success(listOf(task(1, "IN_PROGRESS"))),
                reportsResult = Result.success(listOf(report(90, 1)))
            )
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(1L), viewModel.uiState.value.tasks.map { it.taskId })
        assertEquals(90L, viewModel.uiState.value.reportIdFor(1))
    }

    @Test
    fun `partial report failure keeps loaded tasks and exposes retry state`() {
        val viewModel = TasksViewModel(
            FakeTasksDataSource(
                tasksResult = Result.success(listOf(task(2, "OVERDUE"))),
                reportsResult = Result.failure(IllegalStateException("sensitive upstream detail"))
            )
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(2L), viewModel.uiState.value.tasks.map { it.taskId })
        assertTrue(viewModel.uiState.value.reports.isEmpty())
    }

    private class FakeTasksDataSource(
        private val tasksResult: Result<List<MyAssessmentTask>>,
        private val reportsResult: Result<List<MyReportSummary>>
    ) : RespondentTasksDataSource {
        override suspend fun fetchMyTasks(): List<MyAssessmentTask> = tasksResult.getOrThrow()
        override suspend fun fetchMyReports(): List<MyReportSummary> = reportsResult.getOrThrow()
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
