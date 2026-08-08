package org.sainm.psy.respondent.ui

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import org.sainm.psy.respondent.data.AuthRepository
import org.sainm.psy.respondent.data.RespondentRepository
import org.sainm.psy.respondent.data.local.SessionStorage
import org.sainm.psy.respondent.data.model.AnswerItemRequest
import org.sainm.psy.respondent.data.model.AppointmentSummary
import org.sainm.psy.respondent.data.model.CounselorOption
import org.sainm.psy.respondent.data.model.CounselorSchedule
import org.sainm.psy.respondent.data.model.CreateAppointmentRequest
import org.sainm.psy.respondent.data.model.RescheduleAppointmentRequest
import org.sainm.psy.respondent.data.model.AppointmentStatusLog
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
import java.util.UUID
import org.sainm.psy.respondent.R
import org.sainm.psy.respondent.push.FirebasePushRegistration

private data class AppDependencies(
    val authRepository: AuthRepository,
    val respondentRepository: RespondentRepository
)

private sealed class RootDestination(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object Home : RootDestination("home", R.string.text_home, Icons.Outlined.Home)
    data object Tasks : RootDestination("tasks", R.string.text_my_tasks, Icons.AutoMirrored.Outlined.Assignment)
    data object Reports : RootDestination("reports", R.string.text_my_reports, Icons.Outlined.Summarize)
    data object Appointments : RootDestination("appointments", R.string.text_appointments, Icons.Outlined.CalendarMonth)
    data object Notifications : RootDestination("notifications", R.string.text_notifications, Icons.Outlined.Notifications)
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
fun PsyRespondentApp(notificationTargetPath: String? = null, onNotificationTargetConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val sessionStorage = remember(context) { SessionStorage(context) }
    val dependencies = remember(sessionStorage) {
        val apiFactory = ApiFactory(sessionStorage)
        AppDependencies(
            authRepository = AuthRepository(apiFactory, sessionStorage),
            respondentRepository = RespondentRepository(apiFactory, sessionStorage)
        )
    }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var localeTag by rememberSaveable { mutableStateOf(sessionStorage.readLocaleTag()) }
    var loggedIn by rememberSaveable { mutableStateOf(dependencies.authRepository.currentSession() != null) }
    val changeLocale: (String) -> Unit = { selected ->
        sessionStorage.writeLocaleTag(selected)
        AppText.initialize(context, selected)
        localeTag = selected
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) FirebasePushRegistration.registerCurrentToken(context, dependencies.respondentRepository)
    }

    LaunchedEffect(loggedIn, notificationTargetPath) {
        if (loggedIn && !notificationTargetPath.isNullOrBlank()) {
            notificationTargetPath.toAppRoute()?.let { navController.navigate(it) { launchSingleTop = true } }
            onNotificationTargetConsumed()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F6FA)) {
        if (!loggedIn) {
            LoginScreen(snackbarHostState = snackbarHostState, localeTag = localeTag, onLocaleChange = changeLocale) { username, password ->
                runCatching {
                    dependencies.authRepository.login(username, password)
                }.onSuccess {
                    loggedIn = true
                }.onFailure {
                    snackbarHostState.showSnackbar(it.message ?: tr(R.string.text_login_failed_check_your_credentials_and_server_address))
                }
            }
            return@Surface
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route?.substringBefore("/")
        val currentTitle = when (route) {
            RootDestination.Tasks.route -> tr(R.string.text_my_tasks)
            RootDestination.Reports.route -> tr(R.string.text_my_reports)
            RootDestination.Appointments.route -> tr(R.string.text_appointments)
            RootDestination.Notifications.route -> tr(R.string.text_notifications)
            "task" -> tr(R.string.text_assessment)
            "report" -> tr(R.string.text_report_details)
            else -> tr(R.string.text_psychological_assessment)
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
                    title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                    actions = {
                        LanguageSelector(localeTag, changeLocale)
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    dependencies.authRepository.logout()
                                    loggedIn = false
                                }
                            }
                        ) {
                            Text(tr(R.string.text_log_out))
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
                                icon = { Icon(item.icon, contentDescription = tr(item.titleRes)) },
                                label = { Text(tr(item.titleRes)) }
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
                    TasksScreen(
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
                        taskId = entry.arguments?.getLong("taskId") ?: 0L,
                        onBack = { navController.popBackStack() },
                        onSubmitted = { navController.navigate("report/$it") }
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

private fun String.toAppRoute(): String? = when {
    startsWith("/reports/") -> substringAfter("/reports/").substringBefore('?').toLongOrNull()?.let { "report/$it" }
    startsWith("/my/tasks") || startsWith("/tasks") -> RootDestination.Tasks.route
    startsWith("/appointments") -> RootDestination.Appointments.route
    startsWith("/notifications") || startsWith("/my/notifications") -> RootDestination.Notifications.route
    else -> null
}

@Composable
private fun LanguageSelector(localeTag: String, onLocaleChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "zh-CN" to R.string.language_chinese,
        "ja-JP" to R.string.language_japanese,
        "en-US" to R.string.language_english
    )
    val selectedLabel = options.firstOrNull { it.first == localeTag }?.second ?: R.string.language_english
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(tr(selectedLabel))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (tag, labelRes) ->
                DropdownMenuItem(
                    text = { Text(tr(labelRes), fontWeight = if (tag == localeTag) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        expanded = false
                        if (tag != localeTag) onLocaleChange(tag)
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    snackbarHostState: SnackbarHostState,
    localeTag: String,
    onLocaleChange: (String) -> Unit,
    onLogin: suspend (String, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

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
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        LanguageSelector(localeTag, onLocaleChange)
                    }
                    Text(tr(R.string.text_respondent_app), style = MaterialTheme.typography.labelLarge, color = Color(0xFF0F5F8F))
                    Text(tr(R.string.text_your_assessment_portal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        tr(R.string.text_only_the_tasks_reports_appointments_and_notifications_you),
                        color = Color(0xFF587082)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(tr(R.string.text_assessments), tr(R.string.text_reports), tr(R.string.text_appointments_2)).forEach {
                            AssistChip(
                                onClick = {},
                                label = { Text(it) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFEAF3FB))
                            )
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(R.string.text_username)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(R.string.text_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(tr(R.string.text_enter_your_username_and_password))
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
                        Text(if (loading) tr(R.string.text_signing_in) else tr(R.string.text_sign_in))
                    }
                    Text(
                        tr(R.string.text_the_default_server_is_10_0_2_2),
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

    LaunchedEffect(Unit) {
        runCatching {
            tasks = repository.fetchMyTasks()
            reports = repository.fetchMyReports()
            notifications = repository.fetchMyNotifications()
        }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_home)
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_home))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_welcome_back), tr(R.string.text_complete_tasks_review_reports_book_counseling_and_manage))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(tr(R.string.text_pending_assessments), tasks.count { it.status != "COMPLETED" }.toString(), Modifier.weight(1f))
                MetricCard(tr(R.string.text_generated_reports), reports.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(tr(R.string.text_unread_notifications), notifications.count { !it.readFlag }.toString(), Modifier.weight(1f))
                MetricCard(tr(R.string.text_appointments), tr(R.string.text_view), Modifier.weight(1f), emphasized = false)
            }
        }
        item {
            QuickActionCard(tr(R.string.text_continue_assessment), tr(R.string.text_complete_your_unsubmitted_assessment_tasks), Color(0xFF1B587D), onOpenTasks)
        }
        item {
            QuickActionCard(tr(R.string.text_view_reports), tr(R.string.text_review_your_results_and_recommendations), Color(0xFF1F744C), onOpenReports)
        }
        item {
            QuickActionCard(tr(R.string.text_book_counseling), tr(R.string.text_book_a_counselor_when_you_need_support), Color(0xFF945C1E), onOpenAppointments)
        }
        item {
            QuickActionCard(tr(R.string.text_notifications), tr(R.string.text_review_report_appointment_and_task_updates), Color(0xFF6A4BA8), onOpenNotifications)
        }
        item {
            SectionCard(tr(R.string.text_recent_tasks)) {
                val pending = tasks.filter { it.status != "COMPLETED" }.take(3)
                if (pending.isEmpty()) {
                    EmptyHint(tr(R.string.text_no_pending_tasks))
                } else {
                    pending.forEach { task ->
                        ListLine(
                            title = task.taskName,
                            subtitle = "${task.scaleName} · ${tr(R.string.text_due)} ${task.endTime}",
                            actionLabel = tr(R.string.text_answer),
                            onAction = { onOpenTask(task.taskId) }
                        )
                    }
                }
            }
        }
        item {
            SectionCard(tr(R.string.text_recent_reports)) {
                if (reports.isEmpty()) {
                    EmptyHint(tr(R.string.text_no_reports_available))
                } else {
                    reports.take(3).forEach { report ->
                        ListLine(
                            title = report.scaleName,
                            subtitle = "${report.taskName} · ${report.createdAt}",
                            actionLabel = tr(R.string.text_view_2),
                            onAction = { onOpenReport(report.reportId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(
    repository: RespondentRepository,
    onOpenTask: (Long) -> Unit,
    onOpenReport: (Long) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MyAssessmentTask>>(emptyList()) }
    var reports by remember { mutableStateOf<List<MyReportSummary>>(emptyList()) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            tasks = repository.fetchMyTasks()
            reports = repository.fetchMyReports()
        }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_tasks)
        }
        loading = false
    }

    val visible = when (filter) {
        "PENDING" -> tasks.filter { it.status != "COMPLETED" }
        "COMPLETED" -> tasks.filter { it.status == "COMPLETED" }
        "OVERDUE" -> tasks.filter { it.status == "OVERDUE" }
        else -> tasks
    }

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_tasks))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_my_tasks), tr(R.string.text_prioritize_incomplete_tasks_after_submission_you_will_be))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("ALL" to tr(R.string.text_all), "PENDING" to tr(R.string.text_pending), "COMPLETED" to tr(R.string.text_completed), "OVERDUE" to tr(R.string.text_overdue)).forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyHint(tr(R.string.text_no_tasks_match_this_filter)) }
        } else {
            items(visible, key = { it.taskId }) { task ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(task.taskName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${task.scaleName} · ${tr(R.string.text_due)} ${task.endTime}", color = Color(0xFF5E7384))
                        StatusPill(
                            when (task.status) {
                                "COMPLETED" -> tr(R.string.text_completed)
                                "OVERDUE" -> tr(R.string.text_overdue)
                                else -> tr(R.string.text_in_progress)
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.status == "COMPLETED") {
                                Button(onClick = {
                                    reports.firstOrNull { it.taskId == task.taskId }?.let { onOpenReport(it.reportId) }
                                }) {
                                    Text(tr(R.string.text_view_report))
                                }
                            } else {
                                Button(onClick = { onOpenTask(task.taskId) }) {
                                    Text(tr(R.string.text_continue))
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
private fun TaskQuestionScreen(
    repository: RespondentRepository,
    taskId: Long,
    onBack: () -> Unit,
    onSubmitted: (Long) -> Unit
) {
    var payload by remember { mutableStateOf<TaskQuestionPayload?>(null) }
    var answerSheetId by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var versionNo by rememberSaveable(taskId) { mutableStateOf<Long?>(null) }
    var currentIndex by rememberSaveable(taskId) { mutableStateOf(0) }
    val answers = remember(taskId) { mutableStateListOf<AnswerItemRequest>() }
    var loading by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(taskId) {
        runCatching {
            repository.fetchTaskQuestions(taskId)
        }.onSuccess {
            answerSheetId = it.draftAnswerSheetId
            versionNo = it.draftVersionNo
            currentIndex = 0
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
            message = it.message ?: tr(R.string.text_failed_to_load_questions)
        }
        loading = false
    }

    val data = payload
    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_questions))
        return
    }
    if (data == null) {
        ErrorFullScreen(message ?: tr(R.string.text_no_question_data_was_found), onBack)
        return
    }
    if (data.completedFlag && data.completedReportId != null) {
        ErrorFullScreen(tr(R.string.text_this_task_is_complete_you_can_view_the), onBack, tr(R.string.text_view_report)) {
            onSubmitted(data.completedReportId)
        }
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
        GradientHeader(data.scaleName, tr(R.string.text_task_value_value_questions, data.taskId, data.questions.size))
        androidx.compose.material3.LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        current?.let { question ->
            ElevatedPanel(modifier = Modifier.weight(1f, fill = false)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${question.questionNo}. ${question.questionTitle}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${tr(R.string.text_type)}: ${questionTypeLabel(question.questionType)}", color = Color(0xFF587082))
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
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(tr(R.string.text_back))
            }
            if (data.allowSaveFlag) {
                OutlinedButton(
                    onClick = {
                        validateClientAnswers(data, answers.toList(), requireCompleteAnswers = false)?.let {
                            message = it
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
                                message = tr(R.string.text_draft_saved)
                            }.onFailure {
                                message = it.message ?: tr(R.string.text_failed_to_save_draft)
                            }
                            processing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !processing
                ) {
                    Text(tr(R.string.text_save))
                }
            }
            Button(
                onClick = {
                    if (currentIndex < data.questions.lastIndex) {
                        message = null
                        currentIndex += 1
                    } else {
                        validateClientAnswers(data, answers.toList(), requireCompleteAnswers = true)?.let {
                            message = it
                            return@Button
                        }
                        processing = true
                        scope.launch {
                            runCatching {
                                repository.submitAnswerSheet(
                                    SubmitAnswerSheetRequest(
                                        taskId = data.taskId,
                                        scaleId = data.scaleId,
                                        answerSheetId = answerSheetId,
                                        versionNo = versionNo,
                                        submitToken = UUID.randomUUID().toString(),
                                        answers = answers.toList()
                                    )
                                )
                            }.onSuccess {
                                if (it.reportId != null) onSubmitted(it.reportId) else onBack()
                            }.onFailure {
                                message = it.message ?: tr(R.string.text_submission_failed)
                            }
                            processing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !processing
            ) {
                Text(if (currentIndex < data.questions.lastIndex) tr(R.string.text_next) else tr(R.string.text_submit))
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

    LaunchedEffect(Unit) {
        runCatching {
            reports = repository.fetchMyReports()
        }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_reports)
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_reports))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_my_reports), tr(R.string.text_results_and_recommendations_are_presented_in_respondent_friendly))
        }
        if (error != null) item { ErrorCard(error!!) }
        if (reports.isEmpty()) {
            item { EmptyHint(tr(R.string.text_no_reports_available)) }
        } else {
            items(reports, key = { it.reportId }) { report ->
                ElevatedPanel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(report.scaleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(report.taskName, color = Color(0xFF5E7384))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(riskLabel(report.riskLevel))
                            StatusPill("${tr(R.string.text_total_score)} ${report.totalScore}", filled = false)
                        }
                        Text(report.createdAt, color = Color(0xFF5E7384))
                        Button(onClick = { onOpenReport(report.reportId) }) {
                            Text(tr(R.string.text_view_report))
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

    LaunchedEffect(reportId) {
        runCatching {
            report = repository.fetchReportDetail(reportId)
        }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_report)
        }
        loading = false
    }

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_report_details))
        return
    }
    if (report == null) {
        ErrorFullScreen(error ?: tr(R.string.text_report_not_found), onBack)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_assessment_result), riskSummary(report!!.riskLevel))
        }
        item {
            ElevatedPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(R.string.text_current_status), style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(riskLabel(report!!.riskLevel), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(report!!.content, style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider()
                    Text(tr(R.string.text_recommended_next_step), style = MaterialTheme.typography.labelLarge, color = Color(0xFF567082))
                    Text(nextSuggestion(report!!.riskLevel), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            SectionCard(tr(R.string.text_answer_summary)) {
                if (report!!.answerDetails.isEmpty()) {
                    EmptyHint(tr(R.string.text_no_answer_details))
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
                SectionCard(tr(R.string.text_scoring_details)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report!!.scoreSource?.let { Text("${tr(R.string.text_score_source)}: $it") }
                        report!!.standardScore?.let { Text("${tr(R.string.text_standard_score)}: $it") }
                        report!!.zScore?.let { Text(tr(R.string.text_z_score_value, it)) }
                        report!!.tScore?.let { Text(tr(R.string.text_t_score_value, it)) }
                        report!!.normCode?.let { Text("${tr(R.string.text_norm_code)}: $it") }
                        if (report!!.highRiskFlag) {
                            StatusPill(tr(R.string.text_high_risk), filled = false)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(tr(R.string.text_back))
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
    var rescheduleTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var historyAppointmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var appointmentHistory by remember { mutableStateOf<List<AppointmentStatusLog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        counselors = repository.fetchCounselors()
        appointments = repository.fetchMyAppointments()
        if (selectedCounselorId != null) {
            schedules = repository.fetchCounselorSchedules(selectedCounselorId!!)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_appointment_data)
        }
        loading = false
    }

    LaunchedEffect(selectedCounselorId) {
        selectedCounselorId?.let { id ->
            runCatching {
                schedules = repository.fetchCounselorSchedules(id)
            }.onFailure {
                error = it.message ?: tr(R.string.text_failed_to_load_schedules)
            }
        }
    }

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_appointments))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_book_counseling_2), tr(R.string.text_choose_a_counselor_and_time_slot_then_submit))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            SectionCard(if (rescheduleTargetId == null) tr(R.string.text_new_appointment) else tr(R.string.text_reschedule_appointment)) {
                if (counselors.isEmpty()) {
                    EmptyHint(tr(R.string.text_no_counselors_are_currently_available))
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
                            Text(tr(R.string.text_available_time_slots), fontWeight = FontWeight.Bold)
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
                                label = { Text(tr(R.string.text_notes)) }
                            )
                            Button(
                                onClick = {
                                    if (selectedCounselorId == null || selectedScheduleId == null) {
                                        error = tr(R.string.text_select_a_counselor_and_time_slot_first)
                                    } else {
                                        scope.launch {
                                            runCatching {
                                                if (rescheduleTargetId == null) {
                                                    repository.createAppointment(
                                                        CreateAppointmentRequest(
                                                            counselorUserId = selectedCounselorId!!,
                                                            scheduleId = selectedScheduleId!!,
                                                            remark = remark.ifBlank { null }
                                                        )
                                                    )
                                                } else {
                                                    repository.rescheduleAppointment(
                                                        rescheduleTargetId!!,
                                                        RescheduleAppointmentRequest(
                                                            counselorUserId = selectedCounselorId!!,
                                                            scheduleId = selectedScheduleId!!,
                                                            remark = remark.ifBlank { null }
                                                        )
                                                    )
                                                }
                                                reload()
                                                remark = ""
                                                selectedScheduleId = null
                                                rescheduleTargetId = null
                                            }.onFailure {
                                                error = it.message ?: tr(R.string.text_failed_to_update_appointment)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (rescheduleTargetId == null) tr(R.string.text_book_appointment) else tr(R.string.text_confirm_reschedule))
                            }
                            if (rescheduleTargetId != null) {
                                TextButton(onClick = { rescheduleTargetId = null; selectedScheduleId = null; remark = "" }) {
                                    Text(tr(R.string.text_cancel_rescheduling))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(tr(R.string.text_my_appointments)) {
                if (appointments.isEmpty()) {
                    EmptyHint(tr(R.string.text_no_appointments))
                } else {
                    appointments.forEach { appointment ->
                        AppointmentLine(
                            appointment = appointment,
                            history = appointmentHistory.takeIf { historyAppointmentId == appointment.id },
                            onReschedule = {
                                rescheduleTargetId = appointment.id
                                selectedCounselorId = appointment.counselorUserId
                                selectedScheduleId = null
                                remark = appointment.remark.orEmpty()
                            },
                            onHistory = {
                                scope.launch {
                                    runCatching { repository.fetchAppointmentHistory(appointment.id) }
                                        .onSuccess { logs -> historyAppointmentId = appointment.id; appointmentHistory = logs }
                                        .onFailure { error = it.message ?: tr(R.string.text_failed_to_load_status_history) }
                                }
                            },
                            onCancel = {
                                scope.launch {
                                    runCatching {
                                        repository.cancelAppointment(appointment.id)
                                        reload()
                                    }.onFailure {
                                        error = it.message ?: tr(R.string.text_failed_to_cancel_appointment)
                                    }
                                }
                            }
                        )
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

    suspend fun reload() {
        notifications = repository.fetchMyNotifications()
    }

    LaunchedEffect(Unit) {
        runCatching { reload() }.onFailure {
            error = it.message ?: tr(R.string.text_failed_to_load_notifications)
        }
        loading = false
    }

    val visible = if (unreadOnly) notifications.filter { !it.readFlag } else notifications

    if (loading) {
        FullscreenLoading(tr(R.string.text_loading_notifications))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GradientHeader(tr(R.string.text_notifications), tr(R.string.text_value_unread, notifications.count { !it.readFlag }))
        }
        if (error != null) item { ErrorCard(error!!) }
        item {
            FilterChip(
                selected = unreadOnly,
                onClick = { unreadOnly = !unreadOnly },
                label = { Text(if (unreadOnly) tr(R.string.text_unread_only) else tr(R.string.text_show_all)) }
            )
        }
        if (visible.isEmpty()) {
            item { EmptyHint(if (unreadOnly) tr(R.string.text_no_unread_notifications) else tr(R.string.text_no_notifications)) }
        } else {
            items(visible, key = { it.id }) { notification ->
                ElevatedPanel(containerColor = if (notification.readFlag) Color.White else Color(0xFFF6FBFF)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(notification.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(if (notification.readFlag) tr(R.string.text_read) else tr(R.string.text_unread), filled = !notification.readFlag)
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
                                        error = it.message ?: tr(R.string.text_failed_to_mark_as_read)
                                    }
                                }
                            }) {
                                Text(tr(R.string.text_mark_as_read))
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
                label = { Text(tr(R.string.text_enter_your_answer)) }
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
                label = { Text(tr(R.string.text_enter_a_value_value_value, question.sliderMin ?: 0, question.sliderMax ?: 100)) },
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
                    label = { Text(question.textInputPlaceholder ?: tr(R.string.text_additional_details)) }
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

private fun validateClientAnswers(
    payload: TaskQuestionPayload,
    answers: List<AnswerItemRequest>,
    requireCompleteAnswers: Boolean = true
): String? {
    val questionMap = payload.questions.associateBy { it.questionId }
    val answersByQuestionId = answers.groupBy { it.questionId }

    answersByQuestionId.keys
        .firstOrNull { it !in questionMap }
        ?.let { invalidQuestionId ->
            return tr(R.string.text_answer_references_an_invalid_question_value, invalidQuestionId)
        }

    payload.questions.forEach { question ->
        val questionAnswers = answersByQuestionId[question.questionId].orEmpty()
        if (requireCompleteAnswers && question.requiredFlag && questionAnswers.isEmpty()) {
            return tr(R.string.text_required_question_value_has_not_been_answered, question.questionNo)
        }
        validateQuestionAnswers(question, questionAnswers)?.let { return it }
    }

    return null
}

private fun validateQuestionAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.isEmpty()) {
        return null
    }
    return when (question.questionType) {
        "SINGLE_CHOICE", "MATRIX" -> validateSingleChoiceAnswers(question, answers)
        "MULTI_SELECT" -> validateMultiSelectAnswers(question, answers)
        "SLIDER" -> validateSliderAnswers(question, answers)
        "TEXT" -> validateTextAnswers(question, answers)
        "TEXT_WITH_OPTION" -> validateTextWithOptionAnswers(question, answers)
        else -> tr(R.string.text_question_type_value_is_not_supported, question.questionType)
    }
}

private fun validateSingleChoiceAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return tr(R.string.text_question_value_must_contain_exactly_one_selected_option, question.questionNo)
    }
    val answer = answers.first()
    if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
        return tr(R.string.text_question_value_contains_an_invalid_option_selection, question.questionNo)
    }
    if (answer.answerValue != null) {
        return tr(R.string.text_question_value_does_not_accept_a_numeric_answer, question.questionNo)
    }
    if (!answer.answerText.isNullOrBlank()) {
        return tr(R.string.text_question_value_does_not_accept_text_input, question.questionNo)
    }
    return null
}

private fun validateMultiSelectAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.isEmpty()) {
        return tr(R.string.text_question_value_must_contain_at_least_one_selected, question.questionNo)
    }
    val optionIds = answers.mapNotNull { it.optionId }
    if (optionIds.size != answers.size || optionIds.distinct().size != optionIds.size) {
        return tr(R.string.text_question_value_contains_duplicate_or_invalid_multi_select, question.questionNo)
    }
    val optionMap = question.options.associateBy { it.optionId }
    if (optionIds.any { it !in optionMap }) {
        return tr(R.string.text_question_value_contains_an_invalid_option_selection, question.questionNo)
    }
    if (question.optionSelectionLimit != null && optionIds.size > question.optionSelectionLimit) {
        return tr(R.string.text_question_value_exceeds_the_selection_limit_of_value, question.questionNo, question.optionSelectionLimit)
    }
    val exclusiveSelected = optionIds.count { optionMap.getValue(it).exclusiveFlag }
    if (exclusiveSelected > 1 || (exclusiveSelected == 1 && optionIds.size > 1)) {
        return tr(R.string.text_question_value_contains_an_exclusive_option_conflict, question.questionNo)
    }
    if (answers.any { it.answerValue != null }) {
        return tr(R.string.text_question_value_does_not_accept_a_numeric_answer, question.questionNo)
    }
    if (answers.any { !it.answerText.isNullOrBlank() }) {
        return tr(R.string.text_question_value_does_not_accept_text_input, question.questionNo)
    }
    return null
}

private fun validateSliderAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return tr(R.string.text_question_value_must_contain_exactly_one_slider_value, question.questionNo)
    }
    val answer = answers.first()
    val value = answer.answerValue
        ?: return tr(R.string.text_question_value_requires_a_slider_value, question.questionNo)
    if (answer.optionId != null) {
        return tr(R.string.text_question_value_does_not_accept_option_selection, question.questionNo)
    }
    if (!answer.answerText.isNullOrBlank()) {
        return tr(R.string.text_question_value_does_not_accept_text_input, question.questionNo)
    }
    val min = question.sliderMin
    val max = question.sliderMax
    if (min == null || max == null || value < min || value > max) {
        return tr(R.string.text_question_value_slider_value_is_out_of_range, question.questionNo)
    }
    question.sliderStep
        ?.takeIf { it > 0.0 }
        ?.let { step ->
            val offset = BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(min))
            if (offset.remainder(BigDecimal.valueOf(step)).compareTo(BigDecimal.ZERO) != 0) {
                return tr(R.string.text_question_value_slider_value_must_match_step_value, question.questionNo, step.toDisplayValue())
            }
        }
    return null
}

private fun validateTextAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return tr(R.string.text_question_value_must_contain_exactly_one_text_answer, question.questionNo)
    }
    val answer = answers.first()
    if (answer.optionId != null || answer.answerValue != null) {
        return tr(R.string.text_question_value_does_not_accept_option_selection, question.questionNo)
    }
    if (answer.answerText.isNullOrBlank()) {
        return tr(R.string.text_question_value_requires_text_input, question.questionNo)
    }
    return null
}

private fun validateTextWithOptionAnswers(
    question: TaskQuestionItem,
    answers: List<AnswerItemRequest>
): String? {
    if (answers.size != 1) {
        return tr(R.string.text_question_value_must_contain_exactly_one_selected_option, question.questionNo)
    }
    val answer = answers.first()
    if (answer.optionId == null || question.options.none { it.optionId == answer.optionId }) {
        return tr(R.string.text_question_value_contains_an_invalid_option_selection, question.questionNo)
    }
    if (answer.answerValue != null) {
        return tr(R.string.text_question_value_does_not_accept_a_numeric_answer, question.questionNo)
    }
    if (question.textInputEnabled == true) {
        if (answer.answerText.isNullOrBlank()) {
            return tr(R.string.text_question_value_requires_text_input, question.questionNo)
        }
    } else if (!answer.answerText.isNullOrBlank()) {
        return tr(R.string.text_question_value_does_not_accept_text_input, question.questionNo)
    }
    return null
}

@Composable
private fun GradientHeader(title: String, subtitle: String) {
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
private fun ElevatedPanel(
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
private fun StatusPill(label: String, filled: Boolean = true) {
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
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFF718697), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedPanel(containerColor = Color(0xFFFFF4F2)) {
        Text(message, color = Color(0xFF9A3C2B))
    }
}

@Composable
private fun FullscreenLoading(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, color = Color(0xFF587082))
        }
    }
}

@Composable
private fun ErrorFullScreen(message: String, onBack: () -> Unit, actionLabel: String = tr(R.string.text_back), action: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, textAlign = TextAlign.Center, color = Color(0xFF7A3B30))
            Button(onClick = action ?: onBack) {
                Text(actionLabel)
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
private fun AppointmentLine(
    appointment: AppointmentSummary,
    history: List<AppointmentStatusLog>?,
    onReschedule: () -> Unit,
    onHistory: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(appointment.counselorDisplayName ?: "${tr(R.string.text_counselor)} #${appointment.counselorUserId}", fontWeight = FontWeight.Bold)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onHistory) {
                Text(tr(R.string.text_status_history))
            }
            if (appointment.appointmentStatus == "CREATED" || appointment.appointmentStatus == "CONFIRMED") {
                TextButton(onClick = onReschedule) {
                    Text(tr(R.string.text_reschedule))
                }
                TextButton(onClick = onCancel) {
                    Text(tr(R.string.text_cancel_appointment))
                }
            }
        }
        history?.forEach { log ->
            Text("${log.fromStatus ?: "-"} → ${log.toStatus} · ${log.createdAt}", color = Color(0xFF5E7384))
        }
        HorizontalDivider()
    }
}

private fun questionTypeLabel(type: String): String = when (type) {
    "SINGLE_CHOICE" -> tr(R.string.text_single_choice)
    "MULTI_SELECT" -> tr(R.string.text_multiple_choice)
    "SLIDER" -> tr(R.string.text_slider)
    "MATRIX" -> tr(R.string.text_matrix)
    "TEXT" -> tr(R.string.text_text)
    "TEXT_WITH_OPTION" -> tr(R.string.text_choice_with_details)
    else -> type
}

private fun appointmentStatusLabel(status: String): String = when (status) {
    "CREATED", "CONFIRMED" -> tr(R.string.text_pending_2)
    "COMPLETED" -> tr(R.string.text_completed)
    "CANCELLED" -> tr(R.string.text_cancelled)
    "NO_SHOW" -> tr(R.string.text_no_show)
    else -> status
}

private fun sourceLabel(source: String): String = when (source) {
    "USER" -> tr(R.string.text_user_created)
    "ADMIN" -> tr(R.string.text_staff_created)
    else -> source
}

private fun riskLabel(level: String): String = when (level) {
    "HIGH" -> tr(R.string.text_needs_prompt_attention)
    "MEDIUM", "ATTENTION" -> tr(R.string.text_continued_attention_recommended)
    else -> tr(R.string.text_generally_stable)
}

private fun riskSummary(level: String): String = when (level) {
    "HIGH" -> tr(R.string.text_this_result_shows_notable_changes_please_speak_with)
    "MEDIUM", "ATTENTION" -> tr(R.string.text_this_result_may_indicate_recent_stress_continue_monitoring)
    else -> tr(R.string.text_this_assessment_is_generally_stable_maintain_a_regular)
}

private fun nextSuggestion(level: String): String = when (level) {
    "HIGH" -> tr(R.string.text_book_counseling_soon_and_discuss_recent_sleep_stress)
    "MEDIUM", "ATTENTION" -> tr(R.string.text_retake_the_assessment_in_one_to_two_weeks)
    else -> tr(R.string.text_maintain_a_regular_routine_and_moderate_exercise_reassess)
}
