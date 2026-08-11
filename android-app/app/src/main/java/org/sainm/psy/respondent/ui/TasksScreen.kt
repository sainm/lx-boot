package org.sainm.psy.respondent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.data.RespondentRepository
import org.sainm.psy.respondent.data.RespondentTasksDataSource
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyReportSummary

internal enum class TaskFilter {
    ALL,
    PENDING,
    COMPLETED,
    OVERDUE
}

internal data class TasksUiState(
    val tasks: List<MyAssessmentTask> = emptyList(),
    val reports: List<MyReportSummary> = emptyList(),
    val selectedFilter: TaskFilter = TaskFilter.ALL,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
) {
    val visibleTasks: List<MyAssessmentTask>
        get() = when (selectedFilter) {
            TaskFilter.ALL -> tasks
            TaskFilter.PENDING -> tasks.filter { it.status != TASK_STATUS_COMPLETED }
            TaskFilter.COMPLETED -> tasks.filter { it.status == TASK_STATUS_COMPLETED }
            TaskFilter.OVERDUE -> tasks.filter { it.status == TASK_STATUS_OVERDUE }
        }

    fun reportIdFor(taskId: Long): Long? = reports.firstOrNull { it.taskId == taskId }?.reportId
}

internal class TasksViewModel(
    private val repository: RespondentTasksDataSource
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TasksUiState())
    val uiState = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectFilter(filter: TaskFilter) {
        mutableUiState.update { it.copy(selectedFilter = filter) }
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, loadFailed = false) }
            val previous = mutableUiState.value
            var loadFailed = false
            val tasks = try {
                repository.fetchMyTasks()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed = true
                previous.tasks
            }
            val reports = try {
                repository.fetchMyReports()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadFailed = true
                previous.reports
            }
            mutableUiState.update {
                it.copy(tasks = tasks, reports = reports, isLoading = false, loadFailed = loadFailed)
            }
        }
    }

    companion object {
        fun factory(repository: RespondentTasksDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(TasksViewModel::class.java))
                    return TasksViewModel(repository) as T
                }
            }
    }
}

@Composable
internal fun TasksRoute(
    repository: RespondentRepository,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit,
    viewModel: TasksViewModel = viewModel(factory = TasksViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksScreen(
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onRetry = viewModel::refresh,
        onOpenTask = onOpenTask,
        onOpenReport = onOpenReport
    )
}

@Composable
internal fun TasksScreen(
    state: TasksUiState,
    onSelectFilter: (TaskFilter) -> Unit,
    onRetry: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit
) {
    if (state.isLoading) {
        FullscreenLoading(androidx.compose.ui.res.stringResource(R.string.loading_tasks))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(
                androidx.compose.ui.res.stringResource(R.string.nav_tasks),
                androidx.compose.ui.res.stringResource(R.string.tasks_description)
            )
        }
        if (state.loadFailed) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ErrorCard(androidx.compose.ui.res.stringResource(R.string.error_tasks_load))
                    Button(onClick = onRetry) {
                        Text(androidx.compose.ui.res.stringResource(R.string.action_retry))
                    }
                }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 1.dp)
            ) {
                items(TaskFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                androidx.compose.ui.res.stringResource(
                                    when (filter) {
                                        TaskFilter.ALL -> R.string.filter_all
                                        TaskFilter.PENDING -> R.string.filter_pending
                                        TaskFilter.COMPLETED -> R.string.filter_completed
                                        TaskFilter.OVERDUE -> R.string.filter_overdue
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
        if (state.visibleTasks.isEmpty()) {
            item { EmptyHint(androidx.compose.ui.res.stringResource(R.string.tasks_empty_filter)) }
        } else {
            items(state.visibleTasks, key = { it.taskId }) { task ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            task.taskName,
                            modifier = Modifier.semantics { heading() },
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(
                                R.string.task_due_format,
                                task.scaleName,
                                task.endTime
                            ),
                            color = androidx.compose.ui.graphics.Color(0xFF5E7384)
                        )
                        StatusPill(
                            androidx.compose.ui.res.stringResource(
                                when (task.status) {
                                    TASK_STATUS_COMPLETED -> R.string.status_completed
                                    TASK_STATUS_OVERDUE -> R.string.status_overdue
                                    else -> R.string.status_in_progress
                                }
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (task.status == TASK_STATUS_COMPLETED) {
                                val reportId = state.reportIdFor(task.taskId)
                                Button(
                                    onClick = { reportId?.let(onOpenReport) },
                                    enabled = reportId != null
                                ) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.action_view_report))
                                }
                            } else {
                                Button(onClick = { onOpenTask(task.taskId) }) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.action_continue_answering))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TASK_STATUS_COMPLETED = "COMPLETED"
private const val TASK_STATUS_OVERDUE = "OVERDUE"
