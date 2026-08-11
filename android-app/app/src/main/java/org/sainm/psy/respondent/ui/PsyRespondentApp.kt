package org.sainm.psy.respondent.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.data.AuthRepository
import org.sainm.psy.respondent.data.RespondentRepository
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.AppointmentSummary
import org.sainm.psy.respondent.data.model.CounselorOption
import org.sainm.psy.respondent.data.model.CounselorSchedule
import org.sainm.psy.respondent.data.model.CreateAppointmentRequest
import org.sainm.psy.respondent.data.model.MyAssessmentTask
import org.sainm.psy.respondent.data.model.MyNotification
import org.sainm.psy.respondent.data.model.MyReportSummary
import org.sainm.psy.respondent.data.model.ReportAnswerDetail
import org.sainm.psy.respondent.data.model.ReportDetail
import org.sainm.psy.respondent.data.model.SaveAnswerSheetRequest
import org.sainm.psy.respondent.data.model.SubmitAnswerSheetRequest
import org.sainm.psy.respondent.data.model.TaskQuestionItem
import org.sainm.psy.respondent.data.model.TaskQuestionPayload
import org.sainm.psy.respondent.data.remote.ApiFactory
import java.math.BigDecimal

private data class AppDependencies(
    val authRepository: AuthRepository,
    val respondentRepository: RespondentRepository
)

private sealed class RootDestination(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    data object Home : RootDestination("home", R.string.nav_home, Icons.Outlined.Home)
    data object Tasks : RootDestination("tasks", R.string.nav_tasks, Icons.AutoMirrored.Outlined.Assignment)
    data object Reports : RootDestination("reports", R.string.nav_reports, Icons.Outlined.Summarize)
    data object Appointments : RootDestination("appointments", R.string.nav_appointments, Icons.Outlined.CalendarMonth)
    data object Notifications : RootDestination("notifications", R.string.nav_notifications, Icons.Outlined.Notifications)
}

private val rootDestinations = listOf(
    RootDestination.Home,
    RootDestination.Tasks,
    RootDestination.Reports,
    RootDestination.Appointments,
    RootDestination.Notifications
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PsyRespondentApp() {
    val context = LocalContext.current
    val sessionStorage = remember(context) { SessionStorage(context) }
    val dependencies = remember(sessionStorage) {
        val apiFactory = ApiFactory(sessionStorage)
        AppDependencies(
            authRepository = AuthRepository(apiFactory, sessionStorage),
            respondentRepository = RespondentRepository(apiFactory)
        )
    }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val loginFailedMessage = stringResource(R.string.error_login_failed)
    var loggedIn by rememberSaveable { mutableStateOf(dependencies.authRepository.currentSession() != null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F6FA)) {
        if (!loggedIn) {
            LoginScreen(snackbarHostState = snackbarHostState) { username, password ->
                runCatching {
                    dependencies.authRepository.login(username, password)
                }.onSuccess {
                    loggedIn = true
                }.onFailure {
                    snackbarHostState.showSnackbar(it.message ?: loginFailedMessage)
                }
            }
            return@Surface
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route?.substringBefore("/")
        val currentTitleRes = when (route) {
            RootDestination.Tasks.route -> R.string.nav_tasks
            RootDestination.Reports.route -> R.string.nav_reports
            RootDestination.Appointments.route -> R.string.nav_appointments
            RootDestination.Notifications.route -> R.string.nav_notifications
            "task" -> R.string.title_start_assessment
            "report" -> R.string.title_report_detail
            else -> R.string.title_assessment
        }
        val showBottomBar = route in rootDestinations.map { it.route }

        Scaffold(
            containerColor = Color(0xFFF3F6FA),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFF183B56)
                    ),
                    title = { Text(stringResource(currentTitleRes), fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    dependencies.authRepository.logout()
                                    loggedIn = false
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_logout))
                        }
                    }
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = Color.White) {
                        rootDestinations.forEach { item ->
                            val selected = backStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                                label = { Text(stringResource(item.titleRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = RootDestination.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(RootDestination.Home.route) {
                    HomeScreen(
                        repository = dependencies.respondentRepository,
                        onOpenTasks = { navController.navigate(RootDestination.Tasks.route) },
                        onOpenReports = { navController.navigate(RootDestination.Reports.route) },
                        onOpenAppointments = { navController.navigate(RootDestination.Appointments.route) },
                        onOpenNotifications = { navController.navigate(RootDestination.Notifications.route) },
                        onOpenTask = { navController.navigate("task/$it") },
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(RootDestination.Tasks.route) {
                    TasksRoute(
                        repository = dependencies.respondentRepository,
                        onOpenTask = { navController.navigate("task/$it") },
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(
                    route = "task/{taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType })
                ) { entry ->
                    TaskQuestionScreen(
                        repository = dependencies.respondentRepository,
                        sessionStorage = sessionStorage,
                        taskId = entry.arguments?.getLong("taskId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        onSubmitted = { reportId ->
                            if (reportId != null) navController.navigate("report/$reportId")
                            else navController.navigate(RootDestination.Tasks.route)
                        }
                    )
                }
                composable(RootDestination.Reports.route) {
                    ReportsScreen(
                        repository = dependencies.respondentRepository,
                        onOpenReport = { navController.navigate("report/$it") }
                    )
                }
                composable(
                    route = "report/{reportId}",
                    arguments = listOf(navArgument("reportId") { type = NavType.LongType })
                ) { entry ->
                    ReportDetailScreen(
                        repository = dependencies.respondentRepository,
                        reportId = entry.arguments?.getLong("reportId") ?: 0L,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(RootDestination.Appointments.route) {
                    AppointmentsScreen(repository = dependencies.respondentRepository)
                }
                composable(RootDestination.Notifications.route) {
                    NotificationsScreen(repository = dependencies.respondentRepository)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    snackbarHostState: SnackbarHostState,
    onLogin: suspend (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val loginRequiredMessage = stringResource(R.string.error_login_required)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF12324D), Color(0xFF2B6F9A), Color(0xFFE7F0F7))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(stringResource(R.string.login_app_label), style = MaterialTheme.typography.labelLarge, color = Color(0xFF0F5F8F))
                    Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.login_description),
                        color = Color(0xFF587082)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            R.string.login_chip_assessments,
                            R.string.login_chip_reports,
                            R.string.login_chip_appointments
                        ).forEach {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(it)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEAF3FB))
                            )
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_account)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(loginRequiredMessage)
                                }
                            } else {
                                loading = true
                                coroutineScope.launch {
                                    onLogin(username.trim(), password)
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text(stringResource(if (loading) R.string.login_signing_in else R.string.login_sign_in))
                    }
                    Text(
                        stringResource(R.string.login_service_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6A7F90)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    repository: RespondentRepository,
    onOpenTasks: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MyAssessmentTask>>(emptyList()) }
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<MyNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val homeLoadError = stringResource(R.string.error_home_load)

    LaunchedEffect(Unit) {
        runCatching {
            tasks = repository.fetchMyTasks()
            reports = repository.fetchMyReports()
            notifications = repository.fetchMyNotifications()
        }.onFailure {
            error = it.message ?: homeLoadError
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_home))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(stringResource(R.string.home_welcome), stringResource(R.string.home_description))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(stringResource(R.string.home_pending_assessments), tasks.count { it.status != "COMPLETED" }.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.home_generated_reports), reports.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(stringResource(R.string.home_unread_notifications), notifications.count { !it.readFlag }.toString(), Modifier.weight(1f))
                MetricCard(stringResource(R.string.nav_appointments), stringResource(R.string.home_open_appointments), Modifier.weight(1f), emphasized = false)
            }
        }
        item {
            QuickActionCard(stringResource(R.string.home_continue_assessment), stringResource(R.string.home_continue_assessment_hint), Color(0xFF1B587D), onOpenTasks)
        }
        item {
            QuickActionCard(stringResource(R.string.action_view_report), stringResource(R.string.home_view_report_hint), Color(0xFF1F744C), onOpenReports)
        }
        item {
            QuickActionCard(stringResource(R.string.nav_appointments), stringResource(R.string.home_book_counseling_hint), Color(0xFF945C1E), onOpenAppointments)
        }
        item {
            QuickActionCard(stringResource(R.string.nav_notifications), stringResource(R.string.home_notifications_hint), Color(0xFF6A4BA8), onOpenNotifications)
        }
        item {
            SectionCard(stringResource(R.string.home_recent_tasks)) {
                val pending = tasks.filter { it.status != "COMPLETED" }.take(3)
                if (pending.isEmpty()) {
                    EmptyHint(stringResource(R.string.home_no_pending_tasks))
                } else {
                    pending.forEach { task ->
                        ListLine(
                            title = task.taskName,
                            subtitle = stringResource(R.string.task_due_format, task.scaleName, task.endTime),
                            actionLabel = stringResource(R.string.action_answer),
                            onAction = { onOpenTask(task.taskId) }
                        )
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.home_recent_reports)) {
                if (reports.isEmpty()) {
                    EmptyHint(stringResource(R.string.home_no_reports))
                } else {
                    reports.take(3).forEach { report ->
                        ListLine(
                            title = report.scaleName,
                            subtitle = "${report.taskName} · ${report.createdAt}",
                            actionLabel = stringResource(R.string.action_view),
                            onAction = { onOpenReport(report.reportId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskQuestionScreen(
    repository: RespondentRepository,
    sessionStorage: SessionStorage,
    taskId: Long,
    onBack: () -> Unit,
    onSubmitted: (Long?) -> Unit
) {
    var payload by remember { mutableStateOf<TaskQuestionPayload?>(null) }
    var answerSheetId by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var versionNo by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var currentIndex by rememberSaveable(taskId) { mutableStateOf(0) }
    var reviewing by rememberSaveable(taskId) { mutableStateOf(false) }
    val answers = remember(taskId) { mutableStateListOf<AnswerItemRequest>() }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val questionLoadError = stringResource(R.string.error_questions_load)
    val draftSavedMessage = stringResource(R.string.draft_saved)
    val draftSaveError = stringResource(R.string.error_draft_save)
    val submitError = stringResource(R.string.error_submit)

    LaunchedEffect(taskId) {
        runCatching {
            repository.fetchTaskQuestions(taskId)
        }.onSuccess {
            answerSheetId = it.draftAnswerSheetId
            versionNo = it.draftVersionNo
            currentIndex = sessionStorage.readAssessmentCursor(taskId)
                .coerceIn(0, it.questions.lastIndex.coerceAtLeast(0))
            reviewing = false
            answers.clear()
            answers.addAll(
                it.draftAnswers.map { draft ->
                    AnswerItemRequest(
                        questionId = draft.questionId,
                        optionId = draft.optionId,
                        answerText = draft.answerText,
                        answerValue = draft.answerValue
                    )
                }
            )
            payload = it
        }.onFailure {
            message = it.message ?: questionLoadError
        }
        loading = false
    }

    val data = payload
    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_questions))
        return
    }
    if (data == null) {
        ErrorFullScreen(message ?: stringResource(R.string.error_question_data_missing), onBack)
        return
    }
    if (data.completedFlag && data.completedReportId != null) {
        sessionStorage.clearSubmitToken(data.taskId)
        sessionStorage.clearAssessmentCursor(data.taskId)
        ErrorFullScreen(
            stringResource(R.string.task_completed_message),
            onBack,
            stringResource(R.string.action_view_report)
        ) {
            onSubmitted(data.completedReportId)
        }
        return
    }
    if (data.questions.isEmpty()) {
        ErrorFullScreen(stringResource(R.string.error_question_data_missing), onBack)
        return
    }

    if (reviewing) {
        AssessmentReviewScreen(
            questions = data.questions,
            answers = answers.toList(),
            processing = processing,
            message = message,
            onEdit = { index ->
                currentIndex = index
                sessionStorage.writeAssessmentCursor(data.taskId, index)
                message = null
                reviewing = false
            },
            onBack = {
                message = null
                reviewing = false
            },
            onSubmit = {
                val validationIssue = AssessmentAnswerValidator.validate(
                    data,
                    answers.toList(),
                    requireCompleteAnswers = true
                )
                if (validationIssue != null) {
                    message = validationIssue.localizedMessage(context)
                } else {
                    processing = true
                    scope.launch {
                        runCatching {
                            repository.submitAnswerSheet(
                                SubmitAnswerSheetRequest(
                                    taskId = data.taskId,
                                    scaleId = data.scaleId,
                                    answerSheetId = answerSheetId,
                                    versionNo = versionNo,
                                    submitToken = sessionStorage.getOrCreateSubmitToken(data.taskId),
                                    answers = answers.toList()
                                )
                            )
                        }.onSuccess {
                            sessionStorage.clearSubmitToken(data.taskId)
                            sessionStorage.clearAssessmentCursor(data.taskId)
                            onSubmitted(it.reportId)
                        }.onFailure {
                            message = it.message ?: submitError
                        }
                        processing = false
                    }
                }
            }
        )
        return
    }

    val current = data.questions.getOrNull(currentIndex)
    val progress = if (data.questions.isEmpty()) 0f else (currentIndex + 1f) / data.questions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientHeader(data.scaleName, stringResource(R.string.task_question_count_format, data.taskId, data.questions.size))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = context.getString(
                        R.string.task_question_progress_format,
                        currentIndex + 1,
                        data.questions.size
                    )
                }
        )
        current?.let { question ->
            ElevatedPanel(modifier = Modifier.weight(1f, fill = false)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.question_title_format, question.questionNo, question.questionTitle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.question_type_format, questionTypeLabel(question.questionType)), color = Color(0xFF587082))
                    QuestionEditor(
                        question = question,
                        existing = answers.filter { it.questionId == question.questionId },
                        onChanged = { replacement ->
                            message = null
                            answers.removeAll { it.questionId == question.questionId }
                            answers.addAll(replacement)
                        }
                    )
                }
            }
        }
        message?.let { ErrorCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    if (currentIndex > 0) {
                        currentIndex -= 1
                        sessionStorage.writeAssessmentCursor(data.taskId, currentIndex)
                        message = null
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(if (currentIndex > 0) R.string.action_previous else R.string.action_back))
            }
            if (data.allowSaveFlag) {
                OutlinedButton(
                    onClick = {
                        AssessmentAnswerValidator.validate(data, answers.toList(), requireCompleteAnswers = false)?.let {
                            message = it.localizedMessage(context)
                            return@OutlinedButton
                        }
                        processing = true
                        scope.launch {
                            runCatching {
                                repository.saveAnswerSheet(
                                    SaveAnswerSheetRequest(
                                        taskId = data.taskId,
                                        scaleId = data.scaleId,
                                        answerSheetId = answerSheetId,
                                        versionNo = versionNo,
                                        answers = answers.toList()
                                    )
                                )
                            }.onSuccess {
                                answerSheetId = it.answerSheetId
                                versionNo = it.versionNo
                                message = draftSavedMessage
                            }.onFailure {
                                message = it.message ?: draftSaveError
                            }
                            processing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !processing
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Button(
                onClick = {
                    if (currentIndex < data.questions.lastIndex) {
                        message = null
                        currentIndex += 1
                        sessionStorage.writeAssessmentCursor(data.taskId, currentIndex)
                    } else {
                        AssessmentAnswerValidator.validate(data, answers.toList(), requireCompleteAnswers = true)?.let {
                            message = it.localizedMessage(context)
                            return@Button
                        }
                        message = null
                        reviewing = true
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !processing
            ) {
                Text(stringResource(if (currentIndex < data.questions.lastIndex) R.string.action_next else R.string.action_review_answers))
            }
        }
    }
}

@Composable
private fun ReportsScreen(
    repository: RespondentRepository,
    onOpenReport: (Long) -> Unit
) {
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val reportLoadError = stringResource(R.string.error_reports_load)

    LaunchedEffect(Unit) {
        runCatching {
            reports = repository.fetchMyReports()
        }.onFailure {
            error = it.message ?: reportLoadError
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_reports))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(stringResource(R.string.nav_reports), stringResource(R.string.reports_description))
        }
        if (error != null) item { ErrorCard(error!!) }
        if (reports.isEmpty()) {
            item { EmptyHint(stringResource(R.string.home_no_reports)) }
        } else {
            items(reports, key = { it.reportId }) { report ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(report.scaleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(report.taskName, color = Color(0xFF5E7384))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(riskLabel(report.riskLevel))
                            StatusPill(stringResource(R.string.report_total_score_format, report.totalScore), filled = false)
                        }
                        Text(report.createdAt, color = Color(0xFF5E7384))
                        Button(onClick = { onOpenReport(report.reportId) }) {
                            Text(stringResource(R.string.action_view_report))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDetailScreen(
    repository: RespondentRepository,
    reportId: Long,
    onBack: () -> Unit
) {
    var report by remember { mutableStateOf<ReportDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val reportLoadError = stringResource(R.string.error_reports_load)

    LaunchedEffect(reportId) {
        runCatching {
            report = repository.fetchReportDetail(reportId)
        }.onFailure {
            error = it.message ?: reportLoadError
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_report_detail))
        return
    }
    if (report == null) {
        ErrorFullScreen(error ?: stringResource(R.string.error_report_missing), onBack)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(stringResource(R.string.report_assessment_result), riskSummary(report!!.riskLevel))
        }
        item {
            ElevatedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.report_current_status), style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(riskLabel(report!!.riskLevel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(report!!.content, style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                    Text(stringResource(R.string.report_next_step), style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(nextSuggestion(report!!.riskLevel), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.report_answer_summary)) {
                if (report!!.answerDetails.isEmpty()) {
                    EmptyHint(stringResource(R.string.report_no_answer_details))
                } else {
                    report!!.answerDetails.take(10).forEach { answer ->
                        AnswerSummaryLine(answer)
                    }
                }
            }
        }
        if (
            report!!.scoreSource != null ||
            report!!.standardScore != null ||
            report!!.zScore != null ||
            report!!.tScore != null ||
            report!!.normCode != null ||
            report!!.highRiskFlag
        ) {
            item {
                SectionCard(stringResource(R.string.report_scoring_details)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report!!.scoreSource?.let { Text(stringResource(R.string.report_score_source_format, it)) }
                        report!!.standardScore?.let { Text(stringResource(R.string.report_standard_score_format, it)) }
                        report!!.zScore?.let { Text(stringResource(R.string.report_z_score_format, it)) }
                        report!!.tScore?.let { Text(stringResource(R.string.report_t_score_format, it)) }
                        report!!.normCode?.let { Text(stringResource(R.string.report_norm_code_format, it)) }
                        if (report!!.highRiskFlag) {
                            StatusPill(stringResource(R.string.report_high_risk), filled = false)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun AppointmentsScreen(repository: RespondentRepository) {
    var counselors by remember { mutableStateOf<List<CounselorOption>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<CounselorSchedule>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<AppointmentSummary>>(emptyList()) }
    var selectedCounselorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedScheduleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var remark by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val appointmentsLoadError = stringResource(R.string.error_appointments_load)
    val schedulesLoadError = stringResource(R.string.error_schedules_load)
    val appointmentSelectionError = stringResource(R.string.error_appointment_selection_required)
    val appointmentCreateError = stringResource(R.string.error_appointment_create)
    val appointmentCancelError = stringResource(R.string.error_appointment_cancel)

    suspend fun reload() {
        counselors = repository.fetchCounselors()
        appointments = repository.fetchMyAppointments()
        if (selectedCounselorId != null) {
            schedules = repository.fetchCounselorSchedules(selectedCounselorId!!)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: appointmentsLoadError
        }
        loading = false
    }

    LaunchedEffect(selectedCounselorId) {
        selectedCounselorId?.let { id ->
            runCatching {
                schedules = repository.fetchCounselorSchedules(id)
            }.onFailure {
                error = it.message ?: schedulesLoadError
            }
        }
    }

    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_appointments))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(stringResource(R.string.nav_appointments), stringResource(R.string.appointments_description))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            SectionCard(stringResource(R.string.appointments_new)) {
                if (counselors.isEmpty()) {
                    EmptyHint(stringResource(R.string.appointments_no_counselor))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        counselors.forEach { counselor ->
                            FilterChip(
                                selected = selectedCounselorId == counselor.userId,
                                onClick = { selectedCounselorId = counselor.userId },
                                label = { Text("${counselor.displayName} (${counselor.username})") }
                            )
                        }
                        if (selectedCounselorId != null) {
                            Text(stringResource(R.string.appointments_available_times), fontWeight = FontWeight.Bold)
                            schedules.forEach { schedule ->
                                FilterChip(
                                    selected = selectedScheduleId == schedule.id,
                                    onClick = { selectedScheduleId = schedule.id },
                                    label = { Text("${schedule.scheduleDate} ${schedule.startTime} - ${schedule.endTime}") }
                                )
                            }
                            OutlinedTextField(
                                value = remark,
                                onValueChange = { remark = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.appointments_notes)) }
                            )
                            Button(
                                onClick = {
                                    if (selectedCounselorId == null || selectedScheduleId == null) {
                                        error = appointmentSelectionError
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                repository.createAppointment(
                                                    CreateAppointmentRequest(
                                                        counselorUserId = selectedCounselorId!!,
                                                        scheduleId = selectedScheduleId!!,
                                                        remark = remark.ifBlank { null }
                                                    )
                                                )
                                                reload()
                                                remark = ""
                                                selectedScheduleId = null
                                            }.onFailure {
                                                error = it.message ?: appointmentCreateError
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.appointments_submit))
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.appointments_mine)) {
                if (appointments.isEmpty()) {
                    EmptyHint(stringResource(R.string.appointments_empty))
                } else {
                    appointments.forEach { appointment ->
                        AppointmentLine(appointment) {
                            scope.launch {
                                runCatching {
                                    repository.cancelAppointment(appointment.id)
                                    reload()
                                }.onFailure {
                                    error = it.message ?: appointmentCancelError
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(repository: RespondentRepository) {
    var notifications by remember { mutableStateOf<List<MyNotification>>(emptyList()) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val notificationsLoadError = stringResource(R.string.error_notifications_load)
    val notificationMarkReadError = stringResource(R.string.error_notification_mark_read)

    suspend fun reload() {
        notifications = repository.fetchMyNotifications()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: notificationsLoadError
        }
        loading = false
    }

    val visible = if (unreadOnly) notifications.filter { !it.readFlag } else notifications

    if (loading) {
        FullscreenLoading(stringResource(R.string.loading_notifications))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(
                stringResource(R.string.nav_notifications),
                stringResource(R.string.notifications_unread_count_format, notifications.count { !it.readFlag })
            )
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = !unreadOnly },
                label = { Text(stringResource(if (unreadOnly) R.string.notifications_unread_only else R.string.notifications_show_all)) }
            )
        }
        if (visible.isEmpty()) {
            item { EmptyHint(stringResource(if (unreadOnly) R.string.notifications_no_unread else R.string.notifications_empty)) }
        } else {
            items(visible, key = { it.id }) { notification ->
                ElevatedPanel(containerColor = if (notification.readFlag) Color.White else Color(0xFFF6FBFF)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notification.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(stringResource(if (notification.readFlag) R.string.notification_read else R.string.notification_unread), filled = !notification.readFlag)
                        }
                        Text(notification.content, color = Color(0xFF35495A))
                        Text(notification.createdAt, color = Color(0xFF6A7F90))
                        if (!notification.readFlag) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        repository.markNotificationRead(notification.id)
                                        reload()
                                    }.onFailure {
                                        error = it.message ?: notificationMarkReadError
                                    }
                                }
                            }) {
                                Text(stringResource(R.string.notification_mark_read))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionEditor(
    question: TaskQuestionItem,
    existing: List<AnswerItemRequest>,
    onChanged: (List<AnswerItemRequest>) -> Unit
) {
    when (question.questionType) {
        "MULTI_SELECT" -> {
            var selectedOptionIds by rememberSaveable(question.questionId) {
                mutableStateOf(existing.mapNotNull { it.optionId })
            }
            LaunchedEffect(question.questionId, existing) {
                selectedOptionIds = existing.mapNotNull { it.optionId }
            }
            val optionMap = question.options.associateBy { it.optionId }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.optionId in selectedOptionIds,
                            onCheckedChange = { checked ->
                                selectedOptionIds = if (checked) {
                                    selectedOptionIds.updatedMultiSelectOptions(option, optionMap)
                                } else {
                                    selectedOptionIds.filterNot { it == option.optionId }
                                }
                                onChanged(selectedOptionIds.map { AnswerItemRequest(question.questionId, optionId = it) })
                            }
                        )
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
            }
        }
        "TEXT" -> {
            var text by rememberSaveable(question.questionId) { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                text = existing.firstOrNull()?.answerText.orEmpty()
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChanged(if (it.isBlank()) emptyList() else listOf(AnswerItemRequest(question.questionId, answerText = it)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.answer_placeholder)) }
            )
        }
        "SLIDER" -> {
            var value by rememberSaveable(question.questionId) { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                value = existing.firstOrNull()?.answerValue.toDisplayValue()
            }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it.toDecimalInput()
                    onChanged(
                        value.toDoubleOrNull()?.let { amount ->
                            listOf(AnswerItemRequest(question.questionId, answerValue = amount))
                        } ?: emptyList()
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(
                            R.string.answer_score_range_format,
                            question.sliderMin ?: 0,
                            question.sliderMax ?: 100
                        )
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        "TEXT_WITH_OPTION" -> {
            var selectedOptionId by rememberSaveable(question.questionId) { mutableStateOf<Long?>(null) }
            var extraText by rememberSaveable("${question.questionId}-text") { mutableStateOf("") }
            LaunchedEffect(question.questionId, existing) {
                selectedOptionId = existing.firstOrNull()?.optionId
                extraText = existing.firstOrNull()?.answerText.orEmpty()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    OutlinedButton(
                        onClick = {
                            selectedOptionId = option.optionId
                            onChanged(listOf(AnswerItemRequest(question.questionId, optionId = option.optionId, answerText = extraText)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedOptionId == option.optionId) Color(0xFFEAF3FB) else Color.Transparent
                        )
                    ) {
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
                OutlinedTextField(
                    value = extraText,
                    onValueChange = {
                        extraText = it
                        onChanged(
                            selectedOptionId?.let { choice ->
                                listOf(AnswerItemRequest(question.questionId, optionId = choice, answerText = it))
                            } ?: emptyList()
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(question.textInputPlaceholder ?: stringResource(R.string.answer_details_placeholder)) }
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    val chosen = existing.firstOrNull()?.optionId == option.optionId
                    OutlinedButton(
                        onClick = { onChanged(listOf(AnswerItemRequest(question.questionId, optionId = option.optionId))) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (chosen) Color(0xFFEAF3FB) else Color.Transparent
                        )
                    ) {
                        Text("${option.optionCode}. ${option.optionLabel}")
                    }
                }
            }
        }
    }
}

private fun List<Long>.updatedMultiSelectOptions(
    option: org.sainm.psy.respondent.data.model.TaskQuestionOption,
    optionMap: Map<Long, org.sainm.psy.respondent.data.model.TaskQuestionOption>
): List<Long> {
    if (option.exclusiveFlag) {
        return listOf(option.optionId)
    }
    return filterNot { optionMap[it]?.exclusiveFlag == true } + option.optionId
}

private fun Double?.toDisplayValue(): String =
    this?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()

private fun String.toDecimalInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val decimalPoint = filtered.indexOf('.')
    if (decimalPoint < 0) {
        return filtered
    }
    val integerPart = filtered.substring(0, decimalPoint + 1)
    val fractionPart = filtered.substring(decimalPoint + 1).replace(".", "")
    return integerPart + fractionPart
}

@Composable
internal fun GradientHeader(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF163957), Color(0xFF2A6F9B), Color(0xFF75B8C2)))
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = true) {
    ElevatedPanel(modifier = modifier, containerColor = if (emphasized) Color.White else Color(0xFFF7FBFF)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF678091))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.84f))
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
internal fun ElevatedPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun ListLine(title: String, subtitle: String, actionLabel: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color(0xFF5E7384))
        TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
            Text(actionLabel)
        }
        HorizontalDivider()
    }
}

@Composable
internal fun StatusPill(label: String, filled: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) Color(0xFFEAF3FB) else Color(0xFFF5F7FA))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF23455F))
    }
}

@Composable
internal fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF718697), textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ErrorCard(message: String) {
    ElevatedPanel(containerColor = Color(0xFFFFF4F2)) {
        Text(
            message,
            color = Color(0xFF9A3C2B),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
        )
    }
}

@Composable
internal fun FullscreenLoading(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, color = Color(0xFF587082))
        }
    }
}

@Composable
private fun ErrorFullScreen(message: String, onBack: () -> Unit, actionLabel: String? = null, action: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, textAlign = TextAlign.Center, color = Color(0xFF7A3B30))
            Button(onClick = action ?: onBack) {
                Text(actionLabel ?: stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun AnswerSummaryLine(answer: ReportAnswerDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${answer.questionNo}. ${answer.questionTitle}", fontWeight = FontWeight.Bold)
        Text(answer.optionLabel ?: answer.answerText ?: answer.answerValue?.toString() ?: "-", color = Color(0xFF567082))
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AppointmentLine(appointment: AppointmentSummary, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            appointment.counselorDisplayName
                ?: stringResource(R.string.appointments_counselor_format, appointment.counselorUserId),
            fontWeight = FontWeight.Bold
        )
        Text(
            listOfNotNull(appointment.scheduleDate, appointment.startTime, appointment.endTime).joinToString(" "),
            color = Color(0xFF5E7384)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(appointmentStatusLabel(appointment.appointmentStatus))
            StatusPill(sourceLabel(appointment.sourceType), filled = false)
        }
        appointment.remark?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFF5E7384))
        }
        if (appointment.appointmentStatus != "COMPLETED" && appointment.appointmentStatus != "CANCELLED") {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.appointments_cancel))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun questionTypeLabel(type: String): String = when (type) {
    "SINGLE_CHOICE" -> stringResource(R.string.question_type_single_choice)
    "MULTI_SELECT" -> stringResource(R.string.question_type_multi_select)
    "SLIDER" -> stringResource(R.string.question_type_slider)
    "MATRIX" -> stringResource(R.string.question_type_matrix)
    "TEXT" -> stringResource(R.string.question_type_text)
    "TEXT_WITH_OPTION" -> stringResource(R.string.question_type_text_with_option)
    else -> type
}

@Composable
private fun appointmentStatusLabel(status: String): String = when (status) {
    "CREATED", "CONFIRMED" -> stringResource(R.string.appointment_status_pending)
    "COMPLETED" -> stringResource(R.string.status_completed)
    "CANCELLED" -> stringResource(R.string.appointment_status_cancelled)
    else -> status
}

@Composable
private fun sourceLabel(source: String): String = when (source) {
    "USER" -> stringResource(R.string.appointment_source_user)
    "ADMIN" -> stringResource(R.string.appointment_source_admin)
    else -> source
}

@Composable
private fun riskLabel(level: String): String = when (level.uppercase()) {
    "CRITICAL", "P0" -> stringResource(R.string.risk_label_critical)
    "HIGH", "P1" -> stringResource(R.string.risk_label_high)
    "MODERATE", "MEDIUM", "ATTENTION", "P2" -> stringResource(R.string.risk_label_moderate)
    "LOW" -> stringResource(R.string.risk_label_low)
    "NORMAL" -> stringResource(R.string.risk_label_normal)
    else -> stringResource(R.string.risk_label_unknown)
}

@Composable
private fun riskSummary(level: String): String = when (level.uppercase()) {
    "CRITICAL", "P0", "HIGH", "P1" -> stringResource(R.string.risk_summary_high)
    "MODERATE", "MEDIUM", "ATTENTION", "P2" -> stringResource(R.string.risk_summary_moderate)
    "LOW", "NORMAL" -> stringResource(R.string.risk_summary_low)
    else -> stringResource(R.string.risk_summary_unknown)
}

@Composable
private fun nextSuggestion(level: String): String = when (level.uppercase()) {
    "CRITICAL", "P0", "HIGH", "P1" -> stringResource(R.string.risk_next_high)
    "MODERATE", "MEDIUM", "ATTENTION", "P2" -> stringResource(R.string.risk_next_moderate)
    "LOW", "NORMAL" -> stringResource(R.string.risk_next_low)
    else -> stringResource(R.string.risk_next_unknown)
}
